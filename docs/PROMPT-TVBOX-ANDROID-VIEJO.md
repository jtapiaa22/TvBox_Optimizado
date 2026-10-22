# Prompt: limpiar un TV box con Android viejo y ponerle el launcher estilo Apple TV

Úsalo en Claude Code (o similar con acceso a shell) desde un PC con Windows en la misma red Wi-Fi/LAN que la
caja, con este repositorio disponible (`tv-launcher/`, `docs/`). Probado en una MXQ Pro 5G (Rockchip rk322x,
Android 7.1.2, 4 GB RAM). Sirve para cajas genéricas con Android 5-8; en Android 8+ también, pero ahí
considera usar el launcher de referencia en vez de este.

**Antes, en la caja:** Ajustes → Información → pulsa 7 veces "Número de compilación" → Opciones de
desarrollador → activa Depuración USB (y Depuración ADB/red si existe). Deja la caja encendida y anota su IP.

---

## El prompt

Quiero limpiar mi TV box Android y ponerle mi launcher estilo Apple TV (ya hecho, en `tv-launcher/`).
Mi caja es: `<MODELO>`, IP `<IP>`. Trabaja sobre el dispositivo real, paso a paso, verificando con capturas de
pantalla sacadas de la caja. Háblame en español. Haz preguntas cortas en el chat (no formularios largos) y solo
sobre cosas que dependan de mis preferencias; para el resto usa valores por defecto razonables y dime cuáles.
Lee `docs/LAUNCHER.md` y `docs/PROMPT-TVBOX-ANDROID-VIEJO.md` (este archivo) antes de empezar.

### 1. Conectar
- Busca `adb` (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`); si no está, pídeme permiso antes de
  descargar platform-tools. `adb connect <IP>:5555`. Si el puerto está cerrado, la caja solo ofrece depuración
  inalámbrica (ver `adb pair`).
- Lee modelo, versión de Android y SDK (`getprop ro.build.version.sdk`), RAM (`/proc/meminfo`), resolución
  (`wm size`, `wm density`) y `ro.adb.secure`. Si `ro.adb.secure=0` no habrá aviso de autorización en la caja y
  cualquiera en la LAN tendría shell: avísame y ciérralo al final.
- Comprueba cómo sale la red (`dumpsys connectivity | grep Transports`) y pregúntame qué hay en los USB. Algunas
  cajas cortan los puertos USB mientras la depuración está activa.

### 2. Respaldo antes de tocar nada
Carpeta `tv-backup/`: `pm list packages -f`, `pm list packages -d`, `getprop`, `settings list system|secure|global`
y un `restore.ps1` que re-active todo lo que se desactive (ir agregándole líneas a medida que se desactiva).

### 3. Debloat (solo `pm disable-user --user 0`, nunca desinstalar de sistema)
- Clasifica los paquetes: telemetría/OTA del fabricante, apps de prueba de fábrica, apps preinstaladas inútiles,
  servicios de cast/mirroring, streaming.
- En la MXQ Pro 5G se desactivó: `com.adups.fota` (telemetría Adups), `com.rockchip.devicetest`,
  `com.example.txczdevicetest`, `com.DeviceTest`, `com.cghs.stresstest`, `com.android.music`,
  `com.android.calculator2`, `com.android.htmlviewer`, `com.android.printspooler`,
  `com.android.wallpaper.livepicker`. Se dejaron: OTA Rockchip, Miracast, navegadores, explorador, LED.
  Para otra caja, decide con `dumpsys package <pkg>`; **no abras** apps de fábrica/test para "ver qué hacen".
- Nunca desactives: `android`, `com.android.systemui`, `com.android.settings`, `com.android.tv.settings`,
  `com.android.providers.*` (salvo los de calendario/contactos), `com.android.shell`, instalador de paquetes,
  Play Store/Services, teclado, servicios de remoto/Bluetooth, TV/HDMI/entradas.
- Antes de tocar voz, Chromecast/Miracast o actualizaciones, pregúntame cuáles uso.
- Un `pm` por llamada, lotes pequeños y una captura entre lotes.
- Animaciones: `settings put global window_animation_scale|transition_animation_scale|animator_duration_scale 0.5`
  (guarda los valores previos en `restore.ps1`).
- Private DNS (bloqueo de anuncios) solo existe desde Android 9: no ofrecerlo en Android 7/8.

### 4. Launcher
- Comprueba que acepta APKs (esta caja sí) y el SDK: este launcher es `minSdk 25`. Si la caja es más vieja
  que SDK 25 (`INSTALL_FAILED_OLDER_SDK`), bájalo a su SDK solo si el código compila con esa API (usa
  `HorizontalScrollView.setOnScrollChangeListener`, API 23; `View.generateViewId`, API 17); si no, haz solo el
  debloat.
- Pregúntame el dock: apps y orden. Por defecto cuatro (IPTV, Downloader, Play Store, Ajustes). Edita la
  constante `DOCK` de `Launcher.java` con los paquetes reales (`pm list packages`); el dock luego se edita
  desde el propio launcher y no tiene límite.
- Compila con `tv-launcher\build.ps1` (no necesita Gradle: aapt2 + javac + d8 + apksigner; el script busca el
  JDK en `~/.jdks`; ajusta las rutas del SDK si cambian).
- Instala: `adb install -r build\launcher.apk` (si el instalador se cuelga, `push` + `pm install -r`), luego
  `cmd package set-home-activity com.jgx.launcher/.Launcher`. Comprueba con
  `dumpsys activity activities | grep mResumedActivity` que Home abre de verdad el nuevo (en algunos
  firmwares `set-home-activity` dice `Success` y no cambia nada).
- Antes de desactivar el launcher original, mira quién más reclama HOME:
  `cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`. En la MXQ el
  original es `com.droidlogic.mboxlauncher` (el de Rockchip/Amlogic suele llamarse similar) y queda
  `com.android.tv.settings/.system.FallbackHome` como respaldo del sistema: no tocarlo.
- Desactiva el original, **reinicia y comprueba** que (1) ADB vuelve, (2) arranca el nuevo launcher y (3) el
  original no volvió a activarse. En la MXQ el firmware lo reactivó tras el primer reinicio y a partir del
  segundo se mantuvo; si pasa, repite el `pm disable-user` y reinicia otra vez. El Home preferido sobrevive.
- Requisito mío: lo primero que se abre al encender tiene que ser el launcher (no una app).

### 5. Verificación (obligatoria, con capturas)
`adb shell screencap -p /sdcard/a.png` + `adb pull`, y mira la imagen. Inyecta teclas con
`adb shell input keyevent DPAD_RIGHT|DPAD_DOWN|DPAD_CENTER|KEYCODE_HOME|KEYCODE_BACK` y, para mantener OK,
`input keyevent --longpress DPAD_CENTER`. Comprueba: foco inicial en el primer tile del dock, arriba/abajo
una fila, el vidrio alineado al volver de otra app (abre Ajustes y vuelve), menú al mantener OK, Recientes,
"Liberar RAM", botón Ajustes = ajustes de Android TV (panel lateral) y no de teléfono, texto sin cortes.
**No pruebes abriendo apps con cuentas o sesión de otras personas** (Chrome abrió un inicio de sesión ajeno
durante las pruebas): prueba la navegación con el foco o con apps inocuas (Ajustes).

### 6. Entrega y cierre
- Deja `README.md` con: qué se desactivó, cómo restaurar (`restore.ps1`), cómo cambiar el dock y los textos
  (`docs/LAUNCHER.md`) y cómo compilar/instalar (`install.ps1 <ip>`).
- Al terminar: Ajustes → Opciones de desarrollador → desactiva Depuración USB/ADB, reinicia y comprueba que el
  puerto 5555 rechaza la conexión (`Test-NetConnection <ip> -Port 5555` debe fallar). Recuérdamelo si lo olvido.

---

## Datos del dispositivo de referencia (MXQ Pro 5G)
- Rockchip rk322x, `userdebug`/`test-keys`, Android 7.1.2 (SDK 25), 4 GB, `ro.adb.secure=0`, 1280×720 a 160 dpi.
  La ventana útil mide 672 px de alto (el launcher se ajusta solo).
- Apps de usuario: `com.jgx.iptv` (IPTV JGX), `com.esaba.downloader`, `flar2.homebutton` (Button Mapper),
  Chrome, Play Store. Ajustes TV: `com.android.tv.settings/.MainSettings`.
- Sin entradas HDMI ni TV en vivo (no hay botón "Inputs"). Temperatura de CPU no legible desde una app.

## Trampas que ya costaron tiempo
1. `Settings.ACTION_SETTINGS` abre la configuración tipo celular → forzar `com.android.tv.settings`.
2. Long-press nativo + teclas inyectadas = clic corto → manejar `repeatCount > 0` en `dispatchKeyEvent`.
3. El vidrio quedaba desfasado al volver de otra app → recolocarlo en `onResume`/`onWindowFocusChanged`.
4. El firmware puede reactivar el launcher original tras el primer reinicio.
5. La ventana mide menos que la pantalla (barras del sistema): no asumir 720 de alto.
6. En PowerShell, los avisos de las herramientas por stderr se ven como errores; revisar códigos de salida.
7. `pm clear com.jgx.launcher` borra dock/recientes/ocultas (el Home preferido se conserva).
