<#
  Instala Downloader 1.5.1-ForGoogleAndroidDevices (de AFTVnews) en una caja por ADB, eligiendo solo las piezas que
  le corresponden a esa caja (densidad de pantalla + idioma).

  Uso:   .\instalar-downloader.ps1 -Ip 192.168.1.4
         .\instalar-downloader.ps1 -Ip 192.168.1.4 -DesinstalarSiHaceFalta

  Si la caja ya tiene un Downloader MAS NUEVO (por ejemplo 2.x): Android no deja bajar de version con "install -r"
  (INSTALL_FAILED_VERSION_DOWNGRADE) y esa version puede dar errores en cajas viejas. Con -DesinstalarSiHaceFalta se
  desinstala (se pierden los ajustes de Downloader, no los archivos de /sdcard) y se instala el 1.5.1.
  Sin esa opcion, el script avisa y no toca nada.
#>
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 5555,
    [switch]$DesinstalarSiHaceFalta,
    [string[]]$Idiomas = @('es', 'en')
)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "${Ip}:$Port"
$aqui = $PSScriptRoot
$codigoDecidido = 62          # 1.5.1-ForGoogleAndroidDevices

& $adb connect $dev | Out-Null
if (((& $adb devices) -join "`n") -notmatch [regex]::Escape("$dev`tdevice")) { throw "La caja ($dev) no responde por ADB." }

# Densidad -> pieza (cubetas de Android)
$dens = [int]((((& $adb -s $dev shell 'wm density') -join ' ') -replace '.*:\s*', '').Trim().Split(' ')[0])
# OJO: hashtable normal, no [ordered]: con un diccionario ordenado, $d[160] se toma como POSICION 160 y no como la clave 160.
$cubetas = @{ 120 = 'ldpi'; 160 = 'mdpi'; 213 = 'tvdpi'; 240 = 'hdpi'; 320 = 'xhdpi'; 480 = 'xxhdpi'; 640 = 'xxxhdpi' }
$cerca = ($cubetas.Keys | Sort-Object { [math]::Abs($_ - $dens) } | Select-Object -First 1)
$piezaDens = "config.$($cubetas[$cerca]).apk"
Write-Host "Densidad de la caja: $dens dpi -> $piezaDens"

# Version ya instalada
$info = (& $adb -s $dev shell 'dumpsys package com.esaba.downloader') -join "`n"
if ($info -match 'versionCode=(\d+)') {
    $instalado = [int]$Matches[1]
    Write-Host "Downloader ya instalado: versionCode $instalado (decidido: $codigoDecidido)"
    if ($instalado -gt $codigoDecidido) {
        if (-not $DesinstalarSiHaceFalta) {
            Write-Host "Hay una version MAS NUEVA. Para reemplazarla por la 1.5.1 volve a correr con -DesinstalarSiHaceFalta." -ForegroundColor Yellow
            return
        }
        Write-Host "Desinstalando la version mas nueva..." -ForegroundColor Yellow
        & $adb -s $dev shell pm uninstall com.esaba.downloader
    }
}

$piezas = @("$aqui\com.esaba.downloader.apk", "$aqui\$piezaDens") + ($Idiomas | ForEach-Object { "$aqui\config.$_.apk" })
foreach ($p in $piezas) { if (-not (Test-Path $p)) { throw "Falta $p" } }
& $adb -s $dev install-multiple -r @piezas
Write-Host "Instalado:" -ForegroundColor Cyan
(& $adb -s $dev shell 'dumpsys package com.esaba.downloader') | Select-String 'versionName' | Select-Object -First 1 | ForEach-Object { $_.Line.Trim() }
