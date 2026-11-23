# TB009KA (HiSilicon, Android 9 real, 1 GB) — optimización del 2026-11-23

Caja genérica de la **misma familia que la D9 Pro** (`devices/d9pro-hisilicon`): chip HiSilicon (`ro.board.platform=bigfish`),
fabricante `Hivendor`, fingerprint `google/walley/eros-p1:10/... userdebug/test-keys`, `getprop` dice Android 12.1 pero
`ro.build.version.sdk` = 28 (Android 9). 1 GB de RAM (`/proc/zoneinfo`), 1920×1080 a 320 dpi. **ADB por Wi-Fi en el puerto 3222**
(no aparece en `adb mdns services`; el 5555 está cerrado). `install.ps1` ahora acepta `-Port 3222`.

## Lo que se encontró
| Paquete | Qué es | Qué se hizo |
|---|---|---|
| `com.lz.sysplugin` 2.118 (`/system/app/SysPlugin`) | El agente de la D9 Pro (allá 2.125): uid `system`, `INSTALL_PACKAGES`/`DELETE_PACKAGES`/`WRITE_SECURE_SETTINGS`. Firmado con la clave de plataforma de prueba de AOSP (`c8a2e9bc…92ab8`). Al conectarse no tenía conexiones abiertas (caja casi nueva) | desactivado |
| `com.androidytx.store` 1.0.3 | "Tienda" desconocida en `/data`, instalada de fábrica (2025-08-12) pero con uid `system` y los mismos permisos que sysplugin | desactivada y desinstalada |
| `com.android.vending` en `/system/priv-app/Tubesky/Tubesky.apk` | Play Store con el archivo renombrado. **Es la original**: certificado v1 de Google `F0:FD:6C:5B…2D:60:DB:83` y `jarsigner -verify` = jar verified (igual Play Services). Le quitaron la firma v2/v3 al reempaquetar | se dejó |

APK respaldados (sysplugin, store, Play Store, Play Services, hiRMService, himac, factorytest, HappyPlay, AirPlay, launcher de
fábrica) y las 15 carpetas de `/system` en el PC del usuario (`tv-backup-tb009ka`, fuera del repo).

## Qué se hizo
- Desinstalados (eran apps en `/data`): AirPlay, HappyPlay (`com.hpplay.happyplay.aw`), Chrome, Firefox, Kodi, Prime Video, YouTube.
- Desactivados (`pm disable-user`): sysplugin, `com.example.himac`, `com.dyos.factorytest`, HiMusic, HiGallery, HTMLViewer, respaldos de
  Android/Google, `onetimeinitializer`, `partnersetup`, sincronización de contactos, BookmarkProvider y el launcher de fábrica
  `com.droidlogic.mboxlauncher` (`X96miniLauncher`).
- Se dejaron: Play Store y Play Services (pedido del usuario), HiFileManager y HiVideoPlayer (pendrives), `hiRMService` (accesibilidad
  del fabricante, como en la D9), WebView del sistema.
- Launcher propio, **variante moderna** (SDK 28), como Home. Animaciones a 0.5.

## Borrado de /system: no se pudo, y no hizo falta
`adb root` funciona pero `adb remount` falla (`remount of the / superblock failed: Permission denied`): a diferencia de la D9, esta tiene
dm-verity activo. `limpiar-apps-desactivadas.ps1` hizo el respaldo y se detuvo antes de borrar (y quedó `adb root` abierto: se cerró con
`adb unroot`). Haría falta `adb disable-verity` + reinicio, **pero no se hizo**: tras reiniciar, las 15 siguieron desactivadas (el firmware
**no** reactiva el launcher de fábrica ni sysplugin, a diferencia de la D9), sin procesos del agente y sin conexiones salvo Google Play y ADB.

## Pendiente
- Cerrar ADB al terminar (Depuración USB/red apagada y reinicio; el puerto 3222 debe rechazar).
- No se probó la corrección H.264 (es para el `libstagefright` del RK322x).
