# D9 PRO 5G (HiSilicon, Android 9 real, 1 GB) — optimización del 2026-11-17

Caja genérica. El fingerprint dice `google/walley/eros-p1:10/D9PRO5G ... userdebug/test-keys` y `getprop` dice Android 12.1,
pero `ro.build.version.sdk` = 28 (Android 9). 1 GB de RAM, 1920×1080 a 320 dpi, `armeabi-v7a`, `ro.adb.secure` vacío.
**ADB por Wi-Fi en el puerto 3222**, no el 5555 (aparece por `adb mdns services`). `adb root`/`remount` funcionan (userdebug).
`install.ps1` traía `:5555` fijo (en esta caja se instaló a mano); ahora acepta `-Port 3222`.

## Lo que se encontró
- **`com.lz.sysplugin` 2.125**: app de sistema (uid `system`) con `INSTALL_PACKAGES`/`DELETE_PACKAGES`, empaquetada con libjiagu
  (ofuscada, no se pudo leer su código). Mantenía ~25 conexiones permanentes del uid `system` a puertos 107–197 (mismo
  patrón que el proxy de la TV BOX-3). Al desactivarla bajaron a 0. Es la que instaló `com.android.tvh`.
- **`com.android.tvh` 1.9**: servicio de accesibilidad de anuncios (Zeasn, SDK de Google Ads, servidor `nietv.ahd2020.com`).
  Instalado por sysplugin el 2026-11-04.
- **No se analizó** el contenido real de sysplugin: la atribución se basa en que las conexiones cayeron a 0 al apagarla.

## Qué se quitó (todo borrado de `/system` o desinstalado; reinicio comprobado)
`com.lz.sysplugin`, `com.android.tvh`, `com.example.himac`, `com.dyos.factorytest` (no se abrió), Kodi, Chrome, AirPlay, YouTube TV,
`HiMusic`, `HiGalleryL`, `HiFileManager`, `BasicDreams`, `PrintSpooler`, `HTMLViewer`, `Traceur`, `WallpaperBackup`,
`BackupRestoreConfirmation`, `SharedStorageBackup`, `GoogleBackupTransport`, `GoogleOneTimeInitializer`, `GooglePartnerSetup`,
`GoogleContactsSyncAdapter`, `PlayAutoInstallConfig` y el launcher de fábrica `X96miniLauncher` (`com.droidlogic.mboxlauncher`;
el firmware lo reactivaba tras reiniciar, por eso se borró).

## Qué se dejó a propósito
- **Play Store y Play Services activos** (pedido del usuario; el procedimiento del repo los apaga). Se abre bien (pantalla de login).
- `com.android.webview` (66) es el WebView del sistema: se dejó.
- `com.hisilicon.android.hiRMService` (persistente, uid `system`, con `INSTALL_PACKAGES`): servicio del fabricante del chip, no se probó apagado.
- `HiVideoPlayer`, `HiPQTools`, proveedores y `com.android.tv.settings`.
- No se aplicó la corrección H.264 (es para el `libstagefright` del RK322x).

## Apps y launcher
Downloader 1.5.1-ForGoogleAndroidDevices y TizenTube 2.0.3 ya estaban instalados (no se corrieron los instaladores del repo).
Launcher propio instalado como Home; `DOCK` = IPTV JGX (si se instala), TizenTube, Downloader, Play Store, Ajustes.
`com.android.tv.quicksettings` se agregó a `HIDDEN`. Animaciones a 0.5.

## Respaldo y restauración
Los APK de sysplugin, tvh, hiRMService, himac, factorytest, YouTube TV, Kodi, Chrome, AirPlay, HiMusic, HiGallery, HiFileManager,
HiVideoPlayer, HiPQTools y el launcher de fábrica están en `E:\Proyectos\IPTV\tv-backup-d9pro\apks` (fuera del repo), junto con
`packages-f.txt`, `getprop.txt` y los ajustes. Por pedido del usuario **no se respaldaron las carpetas completas de `/system`**
y no hay `restore.ps1`: lo borrado de `/system` solo se recupera reinstalando esos APK o con el firmware original.

## Pendiente
- Cerrar ADB: desactivar Depuración USB y reiniciar; `Test-NetConnection 192.168.1.12 -Port 3222` debe fallar.
- Sonido, control remoto y reproducción de video no se probaron con la tele (solo capturas de pantalla).
