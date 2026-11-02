# TV BOX-3 (RK322x, Android 7.1.2) — optimización hecha el 2026-11-01

Segunda caja del repositorio: una caja genérica con chip Rockchip RK322x, del mismo tipo que la MXQ Pro 5G pero con **otro
firmware** y con algo que la MXQ no tenía: un **agente de gestión remota instalado de fábrica** que instalaba apps por su cuenta.
Se hizo todo por ADB desde otra computadora (ver "Cómo se trabajó").

## Datos de la caja (lo que dice y lo que es de verdad)
| | Lo que informa el sistema | Lo real |
|---|---|---|
| Android | `ro.build.version.release` = **13.0** | **7.1.2** (SDK 25; el fingerprint dice `rk322x_box ... 7.1.2 NHG47K ... userdebug/test-keys`) |
| RAM | `MemTotal` = **536 870 912 kB** (512 GB) | **~1 GB** (suma de `present` en `/proc/zoneinfo` = 261 888 páginas de 4 KB) |
| Modelo / build | `TV BOX-3`, `eng.akrd2.20250519.170310` | — |
| Chip | `rk30sdk` / `rk30board` / fabricante `rockchip` | RK322x, `armeabi-v7a` (32 bits) |
| Pantalla | 1280x720, 160 dpi | — |
| ADB | `ro.adb.secure=1`, y `adb root` **sí funciona** (compilación `userdebug`) | — |

**Lección:** en estas cajas `getprop` y `/proc/meminfo` pueden estar falseados. Para el reconocimiento usa el *fingerprint*, `ro.build.version.sdk`
y la suma de `/proc/zoneinfo`; para medir memoria, `dumpsys meminfo` (PSS por proceso).

## Lo que se encontró (importante)
La caja traía, además de apps de prueba de fábrica, **software de administración remota** y un cliente de **compartir ancho de banda**:

| Paquete | Origen / cómo se vio | Qué hace (análisis **estático**, sin ejecutarlo) |
|---|---|---|
| `com.android.rockchips.netservice` | De fábrica (3.4.6, `/system/app/netserevice`); actualizado a 3.6.0 el 2026-09-12 12:32:10 | Agente con clases `AppUpgradeManager`, `TaskUpgradeManager`, `BlacklistManager`, `EventRecordManager`, `LogUploadWorker` (todas con "heartbeat"): consulta a un servidor, actualiza/instala apps, revisa una lista negra y sube registros. **No tiene nada de red.** Corre con uid `system` |
| `com.core.rss` | Instalado 2026-09-12 12:32:22 (12 s después de la actualización de `netservice`) | Incluye una librería nativa de **proxy / relay TCP** (rutas con `castar_sdk`, `TcpTunnelPipeline`, `StreamRelayBase`, "Dispatch server"): usaba la conexión de la casa como salida de internet de terceros. Tenía **6 conexiones permanentes** a servidores en puertos 142–180, todas de su uid |
| `com.lssa.service` | Instalado 2026-06-06; mismo certificado que `netservice` | Servicio con `INSTALL_PACKAGES` / `DELETE_PACKAGES` / `WRITE_SECURE_SETTINGS` concedidos, descargador (`okdownload`) |
| `com.android.domain.rk` | uid `system`; mismo certificado | Componentes `StartupBroadcast`/`SystemService` (el mismo esquema que `netservice`); instalaba/borraba apps |
| `com.systemguard.android` | Ya venía desactivada | Arranque con la caja, `REBOOT`, dibujar sobre otras apps |
| `com.oranth.accessibility` (`RkAlarm.apk`) | De fábrica, uid `system` | **Servicio de accesibilidad** (ve la pantalla e intercepta teclas) con `MainService` temporizado |

- **Mismo certificado** (SHA-256 `2d370c21f5dfd553d2a796314b70925fb38adeef90864c920bbbbb12887d3522`) en `netservice`, `lssa`, `domain.rk` y
  `RkAlarm`. Como `domain.rk` corre como `system`, esa es la **clave de plataforma del firmware**: quien actualizó `netservice` en septiembre
  tiene la clave del fabricante. Es una inferencia razonable, no una prueba.
- `core.rss` está firmada con otro certificado (`33c4344bd3e70ea1dd267416028e296c7781a6917895034fdc971d04268b4ede`): es un SDK de terceros metido en una app.
- SHA-256 de los APK analizados: `core-rss` `D070FD92…5E97`, `lssa` `8E56F5F0…9E19`, `domain-rk` `8D242DBE…D900`, `netservice-3.6.0` `D58DDB71…C531`, `RkAlarm` `7D3DDA71…0587`.
- **No se analizó** la versión de fábrica de `netservice` (3.4.6): se respaldó pero no se examinó. Tampoco `com.systemguard.android` (se respaldó) ni `com.android.apkinstaller` (se dejó, oculta).
- Falsos positivos descartados: `com.google.android.verifier` (era el verificador de desarrolladores de Google instalado por Play Store; no se pudo validar la firma pero el contenido es de Google).
- No aparecieron las marcas conocidas de la botnet Vo1d: no hay `vo1d` ni `wd` en `/system/xbin` ni `debuggerd_real` en `/system/bin`
  (no se comparó `debuggerd` con un original, así que eso no descarta otra infección).

## Qué se hizo (en este orden)
1. **Reconocimiento** de solo lectura (modelo, SDK real, RAM real, apps, conexiones con `/proc/net/tcp`, que da el uid dueño de cada una).
2. **Se sacaron los agentes:** `core-rss`, `lssa.service`, `domain.rk` y `systemguard` se desinstalaron (con su APK respaldado antes);
   `netservice` y `RkAlarm` se desactivaron y luego se borraron de `/system`. Después de reiniciar **nada se reinstaló**.
3. **Debloat:** apps de prueba de fábrica (`devicetest`, `DeviceTest`, `stresstest`, `factorystability`, `opasuwintegrationsample`), relleno (música,
   calculadora, impresión, teclado chino, protector de pantalla, respaldos de Google), OTA de Rockchip y MediaCenter. A pedido del usuario se
   conservaron Miracast y, por ahora, Chrome (hace de WebView) y el instalador de APK de fábrica; los dos van ocultos en el launcher.
4. **Play Store y Play Services apagados** (`vending`, `gms`, `gsf`, `gsf.login`): los procesos de Google ocupaban unos **420 MB** de ~1 GB.
   Antes se reemplazó Downloader 2.0.4 por el **1.5.1** del repo.
5. **Launcher propio** instalado como Home (el de fábrica se apagó tras comprobar, con un reinicio, que el nuestro arranca solo).
6. **Borrado de /system** de todo lo desactivado (26 apps, y luego `AllApp`) con `tools/limpiar-apps-desactivadas/`, con respaldo previo.
7. **TizenTube 2.0.2** instalado (no había YouTube). Primera vez que se corre el instalador desde otra PC: falló una vez por la revocación de
   certificados de Windows y se agregó el reintento.
8. **Corrección de calidad H.264** (`tools/correccion-calidad-h264/`): el diagnóstico daba `8:1`, la `libstagefright.so` era idéntica a la de la
   MXQ y tras el parche el decodificador de hardware llega a `8:8000`.

## Resultados medidos
| | Antes | Después |
|---|---|---|
| `/system` libre | 981 152 KB | **1 421 112 KB** (−439 960 KB, justo la suma del modo prueba) |
| `/data` usado | 2 267 088 KB | **1 489 628 KB** |
| Procesos de Google (PSS) | ~420 MB (`gms` 164 + `gms.persistent` 153 + `vending` 65 + `vending:background` 38) | 0 |
| RAM libre (`dumpsys meminfo`) | — | ~720–760 MB libres de ~950 (no hay un "antes" con las mismas líneas) |

## Lo que NO se verificó
- **Reproducción real tras el parche H.264:** solo se midió lo que la caja informa (`CodecInfo`). XtremeTV se instaló, pero no consta aún la prueba en un canal.
- Que **TizenTube** y las apps del usuario anden bien en esta caja durante un uso normal.
- Que `netservice` 3.4.6 (de fábrica) haga lo mismo que la 3.6.0: se supone, pero no se analizó.
- Que no vuelva nada con el tiempo: se comprobó tras un reinicio, no durante semanas.
- `restore.ps1` de la MXQ (usado para volver atrás la limpieza) sigue sin probarse.

## Lo que se dejó a propósito
- **Root permanente (SuperSU: `su`, `daemonsu`, `supolicy` en `/system/xbin`)**: el usuario pidió no tocarlo. Es un riesgo: cualquier app puede pedir root.
- **`com.google.android.webview`** (desactivado de fábrica) y **Chrome** (es el WebView que usan las apps).

## Cómo se trabajó
Toda la caja se manejó por **ADB a través de una notebook en otra red** (acceso remoto por AnyDesk). Notas de eso:
- Los scripts del repo tenían la ruta del SDK de una PC fija: ahora usan el `adb` del PATH si no está (`instalar-downloader.ps1`, `play-services.ps1`).
- `instalar-tizentube.ps1` reintenta la descarga sin consultar la revocación del certificado si Windows falla con el error 35.
- Los APK y las librerías de la caja no están en el repo; el análisis y los respaldos viven en el PC del usuario.
