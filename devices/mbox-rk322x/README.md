# MBOX (RK322x, Android 7.1.2): optimización hecha el 2026-11-21

Tercera caja Rockchip RK322x del repositorio, con **otro firmware** (`rv.1.0.20250514`). A diferencia de la TV BOX-3, **no traía agentes
remotos**: no estaban `netservice`, `core.rss`, `lssa`, `domain.rk` ni `RkAlarm`, y al conectarse solo había conexiones de Google y de ADB.

## Datos de la caja (lo que dice y lo que es de verdad)
| | Lo que informa el sistema | Lo real |
|---|---|---|
| Android | `ro.build.version.release` = **14.0** | **7.1.2** (SDK 25; fingerprint `rk322x_box ... 7.1.2 NHG47K rv.1.0.20250514 userdebug/test-keys`) |
| RAM | `MemTotal` = **8 175 360 kB** (8 GB) | **~1 GB** (suma de `present` en `/proc/zoneinfo`) |
| Modelo | `MBOX`, fabricante `rockchip` | RK322x, `armeabi-v7a` |
| Pantalla | 1280x720, 160 dpi | — |
| ADB | **`ro.adb.secure=0`** (shell sin autorización para cualquiera en la red), `adb root` funciona | — |

Trae **SuperSU permanente** (`su`, `daemonsu`, `supolicy` en `/system/xbin`); no se tocó. `dnschk` y `httpurl` en `/system/xbin` son
herramientas de prueba de red de AOSP (solo usan `getaddrinfo`/`connect`; nada las ejecuta). Sin marcas de Vo1d.

## Qué se hizo
1. **Respaldo** (paquetes, `getprop`, `settings`, `/proc/net/tcp`).
2. **Debloat** (`pm disable-user`): pruebas de fábrica (`com.rockchip.devicetest`, `com.DeviceTest`, `com.cghs.stresstest`, `com.ftest`),
   `com.charon.rocketfly` (el "cohete" de liberar RAM; corre como `system`), relleno (`dreams.basic`, `htmlviewer`, `printspooler`, Pinyin,
   voz Pico, `wallpaperbackup`), OTA de Rockchip, y a pedido del usuario: navegador Lightning, MediaCenter, DLNA y Miracast (`com.rockchip.wfd`).
3. **Voz de Google (Katniss) apagada**: ocupaba ~113 MB de PSS en reposo. **Play Store y Play Services se dejaron** (a pedido), y YouTube
   también (además está SmartTube).
4. **Launcher propio** como Home; el de fábrica es `com.droidlogic.mboxlauncher` (`/system/app/MboxLaunchers`). La primera vez tardó ~11 s
   en dibujarse (captura en negro si se saca antes).
5. **Borrado de /system** de las 18 apps desactivadas con `tools/limpiar-apps-desactivadas/` (respaldo previo en el PC). Tras reiniciar,
   arranca directo en el launcher y no queda nada desactivado pendiente.
6. **Corrección de calidad H.264**: diagnóstico en `8:1`. Esta caja tiene **otra** `libstagefright.so` (ver
   `tools/correccion-calidad-h264/README.md`); el script la reconoció y tras el parche y un reinicio el decodificador llega a `8:8000`.

## Resultados
- `Used RAM` (`dumpsys meminfo`): ~540 MB antes → ~450 MB después (con Play encendido).
- El contador "RAM libre" del launcher muestra ~5.6 GB: este firmware falsea `/proc/meminfo` (mismo problema que la MXQ Pro 5G).

## Pendiente
- **Cerrar ADB** (Ajustes → Opciones de desarrollador → Depuración USB): con `ro.adb.secure=0` cualquiera en la red tiene shell.
- No se probó reproducción real en una app tras el parche H.264 en esta caja (sí en la segunda TV BOX-3, con la otra librería).
