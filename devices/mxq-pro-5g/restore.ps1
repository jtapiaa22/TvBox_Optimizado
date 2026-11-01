# Restaura las apps que se borraron de /system (29 carpetas). Uso: .\restore.ps1 [carpeta-respaldo]
# SIN PROBAR: se escribio al borrar las apps, no se ejecuto despues. Revisa el resultado antes de fiarte.
# Necesita el respaldo hecho antes de borrar: <respaldo>\app\<Carpeta> y <respaldo>\priv-app\<Carpeta>
# (el respaldo con los APK no se sube a GitHub; vive en tv-backup\system-apps del PC).
param([string]$Respaldo = "D:\Proyectos\IPTV\tv-backup\system-apps")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$d = "192.168.1.4:5555"   # cambia la IP si es otra caja
& $adb connect $d
& $adb -s $d root; Start-Sleep 3; & $adb connect $d
& $adb -s $d remount

foreach ($sub in 'app', 'priv-app') {
  Get-ChildItem "$Respaldo\$sub" -Directory | ForEach-Object {
    $dest = "/system/$sub/$($_.Name)"
    & $adb -s $d push $_.FullName "/system/$sub/"
    & $adb -s $d shell "chmod -R 755 $dest; find $dest -type f -exec chmod 644 {} +; chown -R root:root $dest; restorecon -R $dest"
  }
}

# Launcher original como Home (si se restauro MboxLauncher)
& $adb -s $d shell cmd package set-home-activity com.droidlogic.mboxlauncher/.Launcher

# Animaciones a su valor de fabrica
& $adb -s $d shell settings put global window_animation_scale 1.0
& $adb -s $d shell settings put global transition_animation_scale 1.0
& $adb -s $d shell settings put global animator_duration_scale 1.0

# Play Store y Play Services (solo estan deshabilitados, no borrados; ver play-services.ps1)
foreach ($p in 'com.android.vending', 'com.google.android.gms', 'com.google.android.gsf.login', 'com.google.android.gsf') {
  & $adb -s $d shell pm enable $p
}

& $adb -s $d shell sync
& $adb -s $d reboot   # el sistema vuelve a registrar los APK al arrancar
