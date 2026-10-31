# MXQ Pro 5G (Rockchip rk322x, Android 7.1.2): notas del dispositivo

Caja de referencia con la que se desarrolló todo. Android 7.1.2 (SDK 25), **1 GB de RAM real** (Android reporta 4 GB, falseado por el firmware), eMMC de 8 GB, 1280×720 a 160 dpi,
firmware `userdebug` con `ro.adb.secure=0`. Sin entradas HDMI ni TV en vivo. ADB por Wi-Fi en el puerto 5555.

## Qué se quitó (primero `pm disable-user --user 0`; luego `pm uninstall --user 0`, reversible)
Tras comprobar que todo funcionaba con los paquetes desactivados, se quitaron con `pm uninstall --user 0
<paquete>`. Eso no basta para limpiar Ajustes → Aplicaciones: esa pantalla sigue listando las apps de sistema
desinstaladas (con 0 B; se veía, por ejemplo, el teclado chino Google Pinyin y "Bookmark Provider"). Para que
desaparezcan de verdad se hizo, con respaldo previo de los APK en el PC (155 MB, no se sube a GitHub):
`adb root` → `adb remount` → `rm -rf` de las 29 carpetas en `/system/app` y `/system/priv-app` → reinicio.
Chrome y TalkBack, que tenían una copia actualizada en `/data/app`, además necesitaron `pm uninstall <paquete>`
(sin `--user`). Verificado tras reiniciar: arranca directo en el launcher y ninguna de las 29 aparece. No libera
casi espacio (unos 155 MB en total, sobre todo Chrome).

| Grupo | Paquetes |
|---|---|
| Telemetría/OTA Adups | `com.adups.fota` |
| Apps de prueba de fábrica (no abrirlas) | `com.rockchip.devicetest`, `com.example.txczdevicetest`, `com.DeviceTest`, `com.cghs.stresstest` |
| Preinstaladas inútiles | `com.android.music`, `com.android.calculator2`, `com.android.htmlviewer`, `com.android.printspooler`, `com.android.wallpaper.livepicker`, `com.android.dreams.basic` |
| Accesibilidad y voz | `com.google.android.marvin.talkback`, `com.svox.pico` |
| Respaldos y asistentes de Google | `com.android.wallpaperbackup`, `com.android.backupconfirm`, `com.android.sharedstoragebackup`, `com.google.android.backuptransport`, `com.google.android.syncadapters.contacts`, `com.google.android.onetimeinitializer`, `com.google.android.partnersetup` |
| Navegadores y archivos | `acr.browser.barebones` (Lightning), `com.android.chrome`, `com.android.rockchip` (explorador), `com.rockchips.mediacenter` |
| Launcher original | `com.droidlogic.mboxlauncher` |

Ya venían desactivados de fábrica: Katniss (voz de Google), Pinyin IME, WebView de Google y BookmarkProvider.

**Se dejaron activos a propósito:** Miracast (`com.rockchip.wfd`), actualizaciones Rockchip
(`android.rockchip.update.service`), LED (`com.example.changeled`), Button Mapper (`flar2.homebutton`),
y nada más. El paquete `com.android.providers.partnerbookmarks` ("Bookmark Provider" en Ajustes) se dejó
al principio y después también se quitó con `pm uninstall --user 0`.

**Chrome:** como el WebView de Google viene desactivado, en Android 7 Chrome puede hacer de WebView. Aquí se
probó desactivarlo y las apps (IPTV JGX, Xuper TV, Downloader) siguieron funcionando. En otra caja, hacer la
misma prueba antes de darlo por bueno.

**Reinicios:** tras el primer reinicio el firmware reactivó el launcher original; desde el segundo se mantuvo
desactivado. El Home preferido (`cmd package set-home-activity`) sobrevivió siempre.

## Animaciones
`window_animation_scale`, `transition_animation_scale` y `animator_duration_scale` a 0.5 (originales: 1.0).

## Restaurar
```powershell
.\restore.ps1
```
Vuelve a copiar a `/system` las carpetas respaldadas (`tv-backup\system-apps`, que hay que tener antes de borrar),
devuelve las animaciones a 1.0 y vuelve a poner el launcher original como Home. **El script de restauración no se
ha probado**: se escribió al borrar y no se ejecutó después. Las IP del script son las de esta caja:
edítalas si cambia.

## Dock usado
IPTV JGX (`com.jgx.iptv`), Downloader (`com.esaba.downloader`), Play Store (`com.android.vending`) y Ajustes
(`com.android.tv.settings`), más lo que se agregue desde el propio launcher (mantener OK sobre una app).

## Cerrar ADB al terminar
Con `ro.adb.secure=0` cualquiera en la red puede abrir una shell. En la caja: Ajustes → Opciones de
desarrollador → desactivar Depuración USB y reiniciar. Comprobar que el puerto 5555 rechaza la conexión.


## Hardware real (leído en solo lectura)
- SoC Rockchip RK3229, 4 × Cortex-A7 (32 bits) a 1.2 GHz, kernel 3.10.104, GPU de la familia Mali-400.
- RAM real 1 GB: `dmesg` dice "Memory: 132MB 891MB = 1023MB total" y `/proc/iomem` solo muestra ese rango. Android
  reporta 3.9 GB (`MemTotal`) y un `Cached` mayor que la RAM real: el firmware está modificado. El launcher
  calcula la RAM libre como `MemFree + Inactive(file)` y no muestra total.
- eMMC de 8 GB (7.3 GiB), 14 particiones Rockchip estándar (uboot, trust, misc, baseparamer, resource, kernel,
  boot, recovery, backup, cache, metadata, kpanic, system, userdata).
- Placa: serigrafía **TXCZ-RK3229-MXQ-V2.2** (leída de una foto de la placa; coincide con el paquete de fábrica
  `com.example.txczdevicetest`). El chip Wi-Fi impreso es **SV6051P**. No dice "R329Q": ese nombre aparece en
  listados de ROMs de terceros, pero no está confirmado que sea esta placa.
- Wi-Fi `ssv6051` (SSV6051, 2.4 GHz según lo que se sabe del chip; el "5G" del nombre sería marketing). Enlace
  típico de 57-72 Mbps.
- Firmware genérico `rk322x_box-userdebug 7.1.2 NHG47K eng.ROCKCHIP.20200316` con `test-keys`. `adb root`
  funciona (se usó una vez para leer `/proc/iomem` y se revirtió con `adb unroot`).
- Conclusión: no vale la pena flashear un Android más nuevo (1 GB, 32 bits, Mali-400, kernel 3.10).
