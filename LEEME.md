# Chibi (APK con superposición)

Muñeco animado que flota sobre otras apps. El muñeco es `app/src/main/assets/chibi.html`
(el mismo archivo de la página). Si cambias algo ahí, vuelve a compilar el APK.

## Compilar con GitHub (sin instalar nada)
1. Crea un repositorio **público** en github.com (por ejemplo `chibi-android`).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta `.github`).
   - Con git: `git init && git add . && git commit -m "chibi" && git branch -M main && git remote add origin <URL> && git push -u origin main`
   - Si la web no te deja subir `.github`: en GitHub usa *Add file → Create new file*, escribe
     `.github/workflows/build.yml` como nombre y pega el contenido de ese archivo.
3. En la pestaña **Actions** espera a que "Build APK" termine (unos 3–6 minutos).
4. Entra a esa ejecución, baja a **Artifacts** y descarga `chibi-apk` (es un zip con `app-debug.apk`).

## Instalar
1. Pasa `app-debug.apk` al teléfono.
2. Ábrelo. Android pedirá permitir "instalar apps desconocidas" para la app desde la que lo abres.
3. Abre **Chibi** y sigue los 3 pasos de la pantalla; luego toca **Mostrar muñeco**.
4. En Samsung: Ajustes → Aplicaciones → Chibi → Batería → **Sin restricciones**.

## Límites
- No aparece en la pantalla de bloqueo ni en el Always On Display (Android no deja).
- Si el teléfono mata la app en segundo plano, reabre Chibi y toca "Mostrar muñeco".
