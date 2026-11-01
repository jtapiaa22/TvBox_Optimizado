# Prompt: limpiar un TV box con Android viejo y ponerle el launcher estilo Apple TV

Úsalo en Claude Code (o similar con acceso a shell) desde un PC con Windows en la misma red Wi-Fi/LAN que la
caja, con este repositorio disponible (`tv-launcher/`, `docs/`). Probado en una MXQ Pro 5G (Rockchip rk322x,
Android 7.1.2, 1 GB de RAM real aunque Android diga 4 GB). Sirve para cajas genéricas con Android 5-8; en Android 8+ también, pero ahí
considera usar el launcher de referencia en vez de este.

**Antes, en la caja:** Ajustes → Información → pulsa 7 veces "Número de compilación" → Opciones de
desarrollador → activa Depuración USB (y Depuración ADB/red si existe). Deja la caja encendida y anota su IP.

---

## Reglas de trabajo (leer primero)
1. **Primero chequear, después hacer.** Antes de cambiar nada (en la caja, en archivos o en el repositorio),
   comprueba el estado actual y que lo que vas a tocar existe: el paquete está instalado, la cadena que
   vas a reemplazar aparece una vez en el archivo, la IP responde. No supongas.
2. **Después de hacer, verificar.** Tras editar archivos, vuelve a leer el resultado (`git diff` o buscar el
   texto nuevo) antes de continuar. Tras un cambio en la caja, captura de pantalla o lectura del estado.
3. **Reemplazos de texto en lote:** usa siempre la forma completa (cadena vieja → cadena nueva) y avisa si una
   cadena no se encontró. Incidente real: una función de reemplazo recibió un solo par de textos, PowerShell
   lo aplanó y reemplazó una *letra* en vez de la frase ("Android" quedó "nndroid") y el archivo dañado se
   publicó. Con un solo par, usa `$t.Replace($viejo, $nuevo)` directo y revisa el resultado.
4. **Antes de `git push`:** `git diff --stat` y `git diff` revisados, buscar texto dañado y comparar con la
   carpeta de trabajo. Después del push, comprobar el remoto. Los cambios del proyecto se suben a GitHub al
   terminar cada bloque de trabajo; no esperar a que el usuario lo pida.
5. **Los datos del sistema pueden mentir.** En cajas baratas el firmware falsea la RAM (la MXQ Pro 5G dice
   4 GB y tiene 1 GB). Verifica el hardware real antes de prometer nada: `dmesg | grep Memory:`,
   `/proc/iomem` (requiere `adb root`; revierte con `adb unroot`) y `ro.board.platform`.

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
  `com.android.wallpaper.livepicker`, `com.android.dreams.basic`, TalkBack, voz Pico y los respaldos/asistentes de Google. Tras preguntarme, tambien navegadores, explorador de archivos y MediaCenter. Se dejaron: OTA Rockchip, Miracast, LED y Button Mapper. Lista completa en `devices/mxq-pro-5g/README.md`.
  Para otra caja, decide con `dumpsys package <pkg>`; **no abras** apps de fábrica/test para "ver qué hacen".
- **Chrome y WebView:** en Android 7 Chrome suele hacer de WebView si el de Google esta desactivado (aqui venia asi). Antes de desactivar Chrome, pidele al usuario que abra sus apps y confirme que nada sale en blanco; si falla, `pm enable com.android.chrome`.
- Nunca desactives: `android`, `com.android.systemui`, `com.android.settings`, `com.android.tv.settings`,
  `com.android.providers.*` (salvo los de calendario/contactos), `com.android.shell`, instalador de paquetes,
  Play Store/Services, teclado, servicios de remoto/Bluetooth, TV/HDMI/entradas.
- Antes de tocar voz, Chromecast/Miracast o actualizaciones, pregúntame cuáles uso.
- Un `pm` por llamada, lotes pequeños y una captura entre lotes.
- Para que las apps quitadas no salgan en Ajustes → Aplicaciones (esa lista incluye las de sistema desinstaladas, con 0 B), hay que borrarlas de `/system`: primero respaldo de sus carpetas en el PC, luego `adb root`, `adb remount`, `rm -rf` de cada carpeta (comprobando que la ruta es `/system/app/X` o `/system/priv-app/X`), `adb unroot` y reinicio. Si la app tenía actualización en `/data/app` (Chrome, TalkBack), además `pm uninstall <paquete>` sin `--user`. Pídele confirmación al usuario antes: toca la partición del sistema.
- Animaciones: `settings put global window_animation_scale|transition_animation_scale|animator_duration_scale 0.5`
  (guarda los valores previos en `restore.ps1`).
- **Play Store y Play Services (el mayor ahorro de RAM):** en la MXQ Pro 5G sumaban ~300 MB de 1 GB reales. Se deshabilitan
  (`pm disable-user --user 0`) `com.android.vending`, `com.google.android.gms`, `com.google.android.gsf.login` y
  `com.google.android.gsf`; **no** `com.google.android.packageinstaller` (instalador de APK). Pregúntale al usuario si usa
  Play Store para algo. Pasos, en este orden:
  1. **Instalar Downloader primero**: es la forma de instalar apps sin Play Store. Versión decidida:
     **1.5.1-ForGoogleAndroidDevices (código 62)**, liviana y rápida. Viene como `.xapk`: se instala con
     `adb install-multiple -r` del APK principal más la pieza de densidad de la caja (`config.mdpi` a 160 dpi) y los idiomas.
  2. **Si la caja ya trae otro Downloader (por ejemplo más nuevo) y da errores**, desinstalarlo
     (`pm uninstall com.esaba.downloader`) e instalar el 1.5.1: Android no permite bajar de versión con `install -r`
     (`INSTALL_FAILED_VERSION_DOWNGRADE`); `-d` solo sirve en firmware depurable. Se pierden los ajustes de Downloader.
  3. **Comprobar que nadie use Play Store** (`dumpsys activity activities | grep mResumedActivity`): en la MXQ se cortó
     una instalación en curso al apagarla.
  4. Apagar los paquetes de a uno, midiendo y abriendo las apps del usuario después. Es reversible con `pm enable`.
  5. Para medir memoria **no usar `/proc/meminfo`** (falseado): usar el PSS por proceso de `dumpsys meminfo`.
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
- Rockchip rk322x, `userdebug`/`test-keys`, Android 7.1.2 (SDK 25), **1 GB de RAM real** (Android reporta 4 GB: el firmware falsea `/proc/meminfo`, `MemTotal`, `Cached` y `ActivityManager`; se comprobo con `dmesg` y `/proc/iomem` tras `adb root`), 8 GB de eMMC, `ro.adb.secure=0`, 1280×720 a 160 dpi.
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


