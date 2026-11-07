package com.kuroma.chibi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var web: WebView? = null
    private lateinit var params: WindowManager.LayoutParams

    // estado del arrastre (la ventana se mueve con coordenadas de pantalla reales)
    private var dragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var posX = 0f
    private var posY = 0f
    @Volatile private var speed = 0f // dp por milisegundo (lo lee el JS para saber si lo arrastran "rápido")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (web == null) showOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chibi", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Chibi está en pantalla")
            .setContentText("Toca para abrir los ajustes")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_launcher), "Quitar", stopPi
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun showOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        val prefs = getSharedPreferences("chibi", Context.MODE_PRIVATE)
        val sizeDp = prefs.getInt("size", 190)
        val w = (sizeDp * dm.density).roundToInt()
        val h = (sizeDp * 1.65f * dm.density).roundToInt()

        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dm.widthPixels - w)
            y = prefs.getInt("y", (dm.heightPixels - h) / 2)
        }

        val view = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setOnLongClickListener { true }
            isHapticFeedbackEnabled = false
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "Android")
            setOnTouchListener { _, ev -> onTouch(ev); false } // false: el WebView también recibe el toque
            loadUrl("file:///android_asset/chibi.html")
        }
        web = view
        wm?.addView(view, params)
    }

    private fun onTouch(ev: MotionEvent) {
        val now = SystemClock.uptimeMillis()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.rawX; lastY = ev.rawY; lastT = now; speed = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - lastX
                val dy = ev.rawY - lastY
                val dt = max(1L, now - lastT)
                if (dragging) {
                    val dm = resources.displayMetrics
                    posX = (posX + dx).coerceIn(-params.width / 2f, dm.widthPixels - params.width / 2f)
                    posY = (posY + dy).coerceIn(-params.height / 4f, dm.heightPixels - params.height * 0.75f)
                    params.x = posX.roundToInt()
                    params.y = posY.roundToInt()
                    wm?.updateViewLayout(web, params)
                    speed = hypot(dx, dy) / dt / dm.density
                }
                lastX = ev.rawX; lastY = ev.rawY; lastT = now
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> speed = 0f
        }
    }

    private fun savePosition() {
        getSharedPreferences("chibi", Context.MODE_PRIVATE).edit()
            .putInt("x", params.x).putInt("y", params.y).apply()
    }

    /** Funciones que el HTML llama como Android.setDragging(...), Android.getSpeed(), Android.close() */
    inner class Bridge {
        @JavascriptInterface
        fun setDragging(on: Boolean) {
            main.post {
                dragging = on
                if (on) { posX = params.x.toFloat(); posY = params.y.toFloat() } else savePosition()
            }
        }

        @JavascriptInterface
        fun getSpeed(): Float = speed

        @JavascriptInterface
        fun close() { main.post { stopSelf() } }
    }

    override fun onDestroy() {
        web?.let {
            try { wm?.removeView(it) } catch (_: Exception) {}
            it.destroy()
        }
        web = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.kuroma.chibi.STOP"
        private const val CHANNEL = "chibi"
        private const val NOTIF_ID = 1
    }
}
