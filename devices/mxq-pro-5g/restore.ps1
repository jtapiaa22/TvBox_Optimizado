# Restaura los paquetes que se quitaron con `pm uninstall --user 0`. Uso: .\restore.ps1
# Los APK siguen en /system, asi que `cmd package install-existing` los vuelve a instalar para el usuario 0.
# (Chrome y TalkBack venian actualizados desde /data/app: vuelven a la version de fabrica.)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$d = "192.168.1.4:5555"   # cambia la IP si es otra caja
& $adb connect $d

$paquetes = @(
  'com.adups.fota', 'com.rockchip.devicetest', 'com.example.txczdevicetest', 'com.DeviceTest',
  'com.cghs.stresstest', 'com.android.music', 'com.android.calculator2', 'com.android.htmlviewer',
  'com.android.printspooler', 'com.android.wallpaper.livepicker', 'com.android.dreams.basic',
  'com.google.android.marvin.talkback', 'com.svox.pico', 'com.android.wallpaperbackup',
  'com.android.backupconfirm', 'com.android.sharedstoragebackup', 'com.google.android.backuptransport',
  'com.google.android.syncadapters.contacts', 'com.google.android.onetimeinitializer',
  'com.google.android.partnersetup', 'acr.browser.barebones', 'com.android.chrome',
  'com.android.rockchip', 'com.rockchips.mediacenter', 'com.droidlogic.mboxlauncher',
  'com.google.android.katniss', 'com.android.inputmethod.pinyin', 'com.android.bookmarkprovider',
  'com.android.providers.partnerbookmarks'
)
foreach ($p in $paquetes) {
  & $adb -s $d shell cmd package install-existing $p
  & $adb -s $d shell pm enable $p
}

# Launcher original como Home
& $adb -s $d shell cmd package set-home-activity com.droidlogic.mboxlauncher/.Launcher

# Animaciones a su valor de fabrica
& $adb -s $d shell settings put global window_animation_scale 1.0
& $adb -s $d shell settings put global transition_animation_scale 1.0
& $adb -s $d shell settings put global animator_duration_scale 1.0
