# Compila, instala el launcher en la caja y lo deja como Home. Uso: .\install.ps1 [ip]
param([string]$Tv = "192.168.1.4")

# adb: ANDROID_HOME, la ruta por defecto de Android Studio o el PATH
$adb = @("$env:ANDROID_HOME\platform-tools\adb.exe", "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe", "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $adb) { $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { $adb = $c.Source } }
if (-not $adb) { throw "No encuentro adb. Instala las platform-tools del Android SDK o ponlas en el PATH." }

$s = "${Tv}:5555"
& "$PSScriptRoot\build.ps1"
if (-not (Test-Path "$PSScriptRoot\build\launcher.apk")) { throw "La compilacion no genero launcher.apk: no instalo nada." }
& $adb connect $s
& $adb -s $s install -r "$PSScriptRoot\build\launcher.apk"
& $adb -s $s shell cmd package set-home-activity com.jgx.launcher/.Launcher
& $adb -s $s shell pm disable-user --user 0 com.droidlogic.mboxlauncher
& $adb -s $s shell input keyevent KEYCODE_HOME
