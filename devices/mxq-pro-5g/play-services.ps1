<#
  Apaga o enciende Play Store y Play Services en la caja (reversible: solo los deshabilita, no los borra).
  Es el mayor ahorro de RAM en una caja de 1 GB: medido en la MXQ Pro 5G, Play Services pesaba ~167 MB y
  Play Store ~137 MB.

  Uso:   .\play-services.ps1 off              # apagar  (por defecto)
         .\play-services.ps1 on               # volver a encender (para usar Play Store un rato)
         .\play-services.ps1 off -Ip 192.168.1.50

  ANTES DE APAGAR: dejar instalado Downloader (ver README del dispositivo), porque sin Play Store es la forma
  de instalar apps. Y comprobar que nadie este instalando algo con Play Store: se corta a la mitad.
  NO se toca com.google.android.packageinstaller: es el instalador de APK del sistema.
#>
param(
    [ValidateSet('off', 'on')][string]$Modo = 'off',
    [string]$Ip = '192.168.1.4',
    [int]$Port = 5555
)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { $adb = $c.Source } else { throw "No encuentro adb. Instala las platform-tools o ponlas en el PATH." } }
$dev = "${Ip}:$Port"
$paquetes = 'com.android.vending', 'com.google.android.gms', 'com.google.android.gsf.login', 'com.google.android.gsf'

& $adb connect $dev | Out-Null
if (((& $adb devices) -join "`n") -notmatch [regex]::Escape("$dev`tdevice")) { throw "La caja ($dev) no responde por ADB." }

if ($Modo -eq 'off') {
    $pk = (& $adb -s $dev shell 'pm list packages downloader') -join ' '
    if ($pk -notmatch 'com\.esaba\.downloader') {
        throw "No hay Downloader instalado: sin Play Store no tendrias como instalar apps. Instalalo antes (ver README)."
    }
    $act = (& $adb -s $dev shell 'dumpsys activity activities | grep mResumedActivity') -join ' '
    if ($act -match 'com\.android\.vending') {
        Write-Host "Play Store esta abierta en pantalla ahora. Si alguien esta instalando algo, se cortaria." -ForegroundColor Yellow
        Write-Host "Cerrala (boton Home) y volve a correr esto." -ForegroundColor Yellow
        return
    }
}

foreach ($p in $paquetes) {
    if ($Modo -eq 'off') { & $adb -s $dev shell "pm disable-user --user 0 $p" }
    else { & $adb -s $dev shell "pm enable $p" }
}
Write-Host "Listo ($Modo). Deshabilitados ahora:" -ForegroundColor Cyan
(& $adb -s $dev shell 'pm list packages -d') | Where-Object { $_ -match 'vending|gms|gsf' }
