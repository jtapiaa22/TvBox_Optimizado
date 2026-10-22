# Compila, instala el launcher en la caja y lo deja como Home. Uso: .\install.ps1 [ip]
param([string]$Tv = "192.168.1.4")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$s = "${Tv}:5555"
& "$PSScriptRoot\build.ps1"
& $adb connect $s
& $adb -s $s install -r "$PSScriptRoot\build\launcher.apk"
& $adb -s $s shell cmd package set-home-activity com.jgx.launcher/.Launcher
& $adb -s $s shell pm disable-user --user 0 com.droidlogic.mboxlauncher
& $adb -s $s shell input keyevent KEYCODE_HOME
