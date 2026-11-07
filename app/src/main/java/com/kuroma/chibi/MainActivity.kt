package com.kuroma.chibi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var sizeLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chibi", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(24))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Chibi"
            textSize = 30f
            setTextColor(Color.parseColor("#242424"))
        })
        root.addView(TextView(this).apply {
            text = "Un muñequito que vive sobre tu pantalla. Sigue estos pasos una sola vez:"
            textSize = 15f
            setPadding(0, dp(6), 0, dp(10))
        })

        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(4), 0, dp(4)) }
        root.addView(status)

        root.addView(button("1. Permitir mostrar sobre otras apps") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(button("2. Permitir notificaciones") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } else {
                Toast.makeText(this, "No hace falta en tu versión de Android", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(button("3. Sin restricción de batería (recomendado)") {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Ya está sin restricción", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            }
        })

        sizeLabel = TextView(this).apply { textSize = 14f; setPadding(0, dp(18), 0, 0) }
        root.addView(sizeLabel)
        val bar = SeekBar(this).apply {
            max = 160 // 120..280 dp
            progress = prefs.getInt("size", 190) - 120
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.edit().putInt("size", p + 120).apply()
                    sizeLabel.text = "Tamaño del muñeco: ${p + 120}  (se aplica al mostrarlo de nuevo)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(bar)
        sizeLabel.text = "Tamaño del muñeco: ${bar.progress + 120}  (se aplica al mostrarlo de nuevo)"

        root.addView(button("▶  Mostrar muñeco") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Primero activa el paso 1", Toast.LENGTH_LONG).show()
            } else {
                stopService(Intent(this, OverlayService::class.java)) // reinicia con el tamaño nuevo
                startForegroundService(Intent(this, OverlayService::class.java))
                moveTaskToBack(true)
            }
        }.apply { setBackgroundColor(Color.parseColor("#A9D6EE")) })
        root.addView(button("✕  Quitar muñeco") {
            stopService(Intent(this, OverlayService::class.java))
        })

        root.addView(TextView(this).apply {
            text = "\nPara moverlo: mantenlo presionado y arrástralo.\nPara quitarlo: la ✕ de arriba a la izquierda del muñeco, o el botón \"Quitar\" de la notificación."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        status.text = if (ok) "✔ Permiso de superposición activado" else "✖ Falta el permiso de superposición (paso 1)"
        status.setTextColor(if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }
}
