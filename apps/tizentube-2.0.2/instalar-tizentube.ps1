<#
  Instala TizenTube Cobalt 2.0.2 (de reisxd) en una caja por ADB y, SOLO si se pide, saca el YouTube que traiga.

  Uso:   .\instalar-tizentube.ps1 -Ip 192.168.1.4 -SoloDetectar          # muestra que hay, no cambia nada
         .\instalar-tizentube.ps1 -Ip 192.168.1.4                        # instala TizenTube; NO toca YouTube
         .\instalar-tizentube.ps1 -Ip 192.168.1.4 -ReemplazarYouTube     # instala TizenTube y despues saca YouTube

  Orden de seguridad: primero se instala TizenTube y se comprueba que quedo; recien despues se desinstala YouTube, y antes
  de desinstalarlo se guarda su APK en la carpeta de respaldo (-Respaldo). Si TizenTube falla, YouTube no se toca.

  El APK pesa ~93 MB y NO esta en el repositorio: se baja de la pagina oficial de su autor
  (github.com/reisxd/TizenTubeCobalt, licencia BSD-3-Clause) y se comprueba su SHA-256 antes de instalarlo.
  Solo se soporta armeabi-v7a (es lo que se probo); en una caja arm64 el script se detiene y explica que hacer.
#>
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 5555,
    [switch]$SoloDetectar,
    [switch]$ReemplazarYouTube,
    [switch]$Reinstalar,                                      # instala encima aunque ya este (install -r conserva los datos)
    [string[]]$PaquetesYouTube = @('com.google.android.youtube.tv', 'com.google.android.youtube'),
    [string]$Apk = "",                                        # un APK ya bajado (se verifica igual el hash)
    [string]$Descargas = "$PSScriptRoot\descargas",            # carpeta para el APK bajado (ignorada por git)
    [string]$Respaldo = "$PSScriptRoot\respaldo-youtube"       # donde se guarda el APK de YouTube antes de quitarlo
)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { $adb = $c.Source } else { throw "No encuentro adb." } }
$dev = "${Ip}:$Port"
$paquete = 'io.gh.reisxd.tizentube.cobalt'
$codigo = 202                                                 # 2.0.2
$url = 'https://github.com/reisxd/TizenTubeCobalt/releases/download/v2.0.2/cobalt-arm.apk'
$tamano = 97195140
$sha256 = 'FF90C6E85C37246B809CCA80282910509782C1384ECCFBB6066DA3B497DD9FD2'

& $adb connect $dev | Out-Null
if (((& $adb devices) -join "`n") -notmatch [regex]::Escape("$dev`tdevice")) { throw "La caja ($dev) no responde por ADB." }
function Sh([string]$cmd) { (& $adb -s $dev shell $cmd) -join "`n" }

# --- que hay en la caja
$abis = Sh 'getprop ro.product.cpu.abilist'
Write-Host "ABIs de la caja: $abis"
$infoTT = Sh "dumpsys package $paquete"
$ttInstalado = if ($infoTT -match 'versionCode=(\d+)') { [int]$Matches[1] } else { 0 }
Write-Host ("TizenTube: " + $(if ($ttInstalado) { "instalado (versionCode $ttInstalado)" } else { 'no instalado' }))

$yt = @()
foreach ($p in $PaquetesYouTube) {
    $ruta = (Sh "pm path $p") -replace 'package:', ''
    if ($ruta) {
        $ver = if ((Sh "dumpsys package $p") -match 'versionName=(\S+)') { $Matches[1] } else { '?' }
        $sistema = ($ruta -split "`n")[0].Trim() -like '/system/*' -or ($ruta -split "`n")[0].Trim() -like '/vendor/*' -or ($ruta -split "`n")[0].Trim() -like '/oem/*'
        $yt += [pscustomobject]@{ Paquete = $p; Version = $ver; Sistema = $sistema; Rutas = @($ruta -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }
    }
}
if ($yt.Count) {
    Write-Host "YouTube encontrado:" -ForegroundColor Yellow
    $yt | ForEach-Object { Write-Host ("  - {0}  v{1}  {2}" -f $_.Paquete, $_.Version, $(if ($_.Sistema) { 'app de SISTEMA' } else { 'app de usuario' })) }
} else { Write-Host "No hay YouTube instalado (paquetes buscados: $($PaquetesYouTube -join ', '))." -ForegroundColor Green }
if ($SoloDetectar) { return }

if ($abis -notmatch 'armeabi-v7a' -or $abis -match 'arm64-v8a') {
    throw "Esta caja no es armeabi-v7a puro ($abis). El script solo trae el hash del APK 'cobalt-arm.apk' (probado). Para arm64 baja 'cobalt-arm64.apk' de la misma pagina de versiones e instalalo a mano."
}

# --- 1) instalar TizenTube (si hace falta), verificando el hash
if ($ttInstalado -ge $codigo -and -not $Reinstalar) {
    Write-Host "TizenTube ya esta en la version decidida o una mas nueva: no se reinstala." -ForegroundColor Green
} else {
    New-Item -ItemType Directory -Force $Descargas | Out-Null
    $archivo = if ($Apk) { $Apk } else { "$Descargas\cobalt-arm-v2.0.2.apk" }
    if (-not (Test-Path $archivo)) {
        Write-Host "Bajando TizenTube 2.0.2 (~93 MB) de la pagina oficial..." -ForegroundColor Cyan
        & curl.exe -L --fail --silent --show-error -o $archivo $url
        if ($LASTEXITCODE -eq 35) {
            # Error de schannel (p. ej. CRYPT_E_REVOCATION_OFFLINE): Windows no pudo consultar la revocacion del certificado
            # (red, antivirus o proxy de esa PC). Se reintenta sin esa consulta: el SHA-256 de abajo garantiza igual el archivo.
            Write-Host "Windows no pudo comprobar la revocacion del certificado. Reintento sin esa comprobacion (el hash se verifica igual)." -ForegroundColor Yellow
            if ((Test-Path $archivo) -and ((Get-Item $archivo).Length -eq 0)) { Remove-Item $archivo -Force }
            & curl.exe -L --fail --silent --show-error --ssl-no-revoke -o $archivo $url
        }
        if ($LASTEXITCODE) { throw "No se pudo bajar el APK de $url" }
    }
    $hash = (Get-FileHash $archivo -Algorithm SHA256).Hash
    if ($hash -ne $sha256) {
        if (-not $Apk) { Remove-Item $archivo -Force }
        throw "El SHA-256 del APK no coincide (esperado $sha256, obtenido $hash). No se instala nada."
    }
    Write-Host "Hash verificado. Instalando (tarda un par de minutos por Wi-Fi)..." -ForegroundColor Cyan
    $r = (& $adb -s $dev install -r $archivo) -join ' '
    Write-Host $r
    if ($r -notmatch 'Success') { throw "No se pudo instalar TizenTube ($r). YouTube no se toca." }
    if (-not ((Sh "pm path $paquete") -match 'base.apk')) { throw "TizenTube no figura instalado tras la instalacion. YouTube no se toca." }
    Write-Host "TizenTube instalado." -ForegroundColor Green
}

# --- 2) reemplazar YouTube (solo si se pidio)
if (-not $ReemplazarYouTube) {
    if ($yt.Count) { Write-Host "YouTube NO se toco. Para reemplazarlo: volve a correr con -ReemplazarYouTube." -ForegroundColor Yellow }
    return
}
foreach ($y in $yt) {
    $carpeta = Join-Path $Respaldo $y.Paquete
    New-Item -ItemType Directory -Force $carpeta | Out-Null
    $i = 0
    foreach ($ruta in $y.Rutas) {
        $i++
        & $adb -s $dev pull $ruta "$carpeta\$i-$([IO.Path]::GetFileName($ruta))" | Out-Null
    }
    $guardados = @(Get-ChildItem $carpeta -File | Where-Object { $_.Length -gt 0 })
    if ($guardados.Count -lt $y.Rutas.Count) { throw "No pude respaldar $($y.Paquete): no se desinstala." }
    Write-Host "Respaldo de $($y.Paquete): $carpeta ($($guardados.Count) archivo/s)"
    if ($y.Sistema) {
        # app de sistema: se quita solo para el usuario 0; se recupera con: cmd package install-existing <paquete>
        $res = Sh "pm uninstall --user 0 $($y.Paquete)"
    } else {
        $res = Sh "pm uninstall $($y.Paquete)"
    }
    Write-Host "Desinstalar $($y.Paquete): $res"
    if ((Sh "pm path $($y.Paquete)") -match 'package:') { Write-Host "  AVISO: $($y.Paquete) sigue presente." -ForegroundColor Yellow }
}
if ($yt.Count) { Write-Host "Listo. Para volver atras con una app de sistema: adb shell cmd package install-existing <paquete>; con una de usuario: adb install-multiple <APKs del respaldo>." -ForegroundColor Cyan }
