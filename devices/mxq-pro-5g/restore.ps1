# Restaura los paquetes desactivados. Uso: .\restore.ps1
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.1.4:5555
& $adb -s 192.168.1.4:5555 shell pm enable com.adups.fota
& $adb -s 192.168.1.4:5555 shell pm enable com.rockchip.devicetest
& $adb -s 192.168.1.4:5555 shell pm enable com.example.txczdevicetest
& $adb -s 192.168.1.4:5555 shell pm enable com.DeviceTest
& $adb -s 192.168.1.4:5555 shell pm enable com.cghs.stresstest
& $adb -s 192.168.1.4:5555 shell pm enable com.android.music
& $adb -s 192.168.1.4:5555 shell pm enable com.android.calculator2
& $adb -s 192.168.1.4:5555 shell pm enable com.android.htmlviewer
& $adb -s 192.168.1.4:5555 shell pm enable com.android.printspooler
& $adb -s 192.168.1.4:5555 shell pm enable com.android.wallpaper.livepicker
& $adb -s 192.168.1.4:5555 shell pm enable com.droidlogic.mboxlauncher
& $adb -s 192.168.1.4:5555 shell cmd package set-home-activity com.droidlogic.mboxlauncher/.Launcher
& $adb -s 192.168.1.4:5555 shell settings put global window_animation_scale 1.0
& $adb -s 192.168.1.4:5555 shell settings put global transition_animation_scale 1.0
& $adb -s 192.168.1.4:5555 shell settings put global animator_duration_scale 1.0
