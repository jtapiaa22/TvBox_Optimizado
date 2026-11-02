<#
  Borra de /system las apps que estan DESACTIVADAS (pm disable-user) en la caja, con respaldo previo en el PC.
  Es la limpieza del final del debloat: una app desactivada sigue ocupando espacio, aparece en Ajustes con 0 B y
  algo del sistema podria volver a activarla. Solo toca apps que YA estan desactivadas.

  Uso:   .\limpiar-apps-desactivadas.ps1 -Ip 192.168.1.4                 # MODO PRUEBA: muestra que haria, no cambia nada
         .\limpiar-apps-desactivadas.ps1 -Ip 192.168.1.4 -Ejecutar       # respalda, borra y reinicia (pide confirmacion)

  Que hace con -Ejecutar, en este orden (y se detiene ante cualquier error ANTES de borrar):
    1. Respaldo de cada carpeta /system/app/<X> o /system/priv-app/<X> en <Respaldo>\app y <Respaldo>\priv-app, y
       comprueba que CADA archivo de la caja esta en el PC con el mismo tamano. Si falta uno, no borra nada.
    2. adb root + adb remount (necesita una compilacion depurable: userdebug). Si no hay root, no borra nada.
    3. Las apps de sistema que tenian una actualizacion en /data/app (Play Store, Play Services...) se desinstalan con
       `pm uninstall` (sin --user) para que no queden sueltas como apps normales.
    4. rm -rf de cada carpeta, SOLO si la ruta es /system/app/<nombre> o /system/priv-app/<nombre>.
    5. adb unroot y reinicio.

  Para volver atras: el respaldo tiene el mismo formato que usa devices/mxq-pro-5g/restore.ps1 (-Respaldo <carpeta>).
  Ese restore NO esta probado: no te fies de el sin revisarlo.

  -Conservar: paquetes desactivados que NO se borran (por defecto el WebView de Google: ya venia desactivado de
  fabrica y Chrome hace de WebView; borrarlo no libera nada util).
  Nunca se borran paquetes basicos del sistema aunque estuvieran desactivados (lista $intocables).
#>
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [int]$Port = 5555,
    [string]$Respaldo = "$PSScriptRoot\respaldo-system",
    [string[]]$Conservar = @('com.google.android.webview'),
    [switch]$Ejecutar
)
$ErrorActionPreference = 'Continue'   # adb escribe avisos normales por stderr; los errores reales se cortan con throw explicitos

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { $adb = $c.Source } else { throw "No encuentro adb. Instala las platform-tools o ponlas en el PATH." } }
$dev = "${Ip}:$Port"

# Nunca se borran, aunque alguien los haya desactivado
$intocables = @(
    'android', 'com.android.systemui', 'com.android.settings', 'com.android.tv.settings', 'com.android.shell',
    'com.android.packageinstaller', 'com.google.android.packageinstaller', 'com.android.inputmethod.latin',
    'com.android.chrome', 'com.jgx.launcher', 'com.android.externalstorage', 'com.android.documentsui',
    'com.android.defcontainer', 'com.android.keychain', 'com.android.certinstaller', 'com.android.location.fused',
    'com.android.vpndialogs', 'com.android.proxyhandler', 'com.android.captiveportallogin', 'com.android.provision'
)
$intocablesRegex = '^com\.android\.providers\.'

# --- analisis de las salidas de adb (funciones puras: se pueden probar con texto) ---
function Get-Desactivados([string]$texto) {
    [regex]::Matches($texto, '(?m)^\s*package:(\S+)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
}
function Get-Rutas([string]$texto) {
    [regex]::Matches($texto, '(?m)^\s*codePath=(\S+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
}
function Get-CarpetasSistema($rutas) {   # solo carpetas /system/app/<X> o /system/priv-app/<X> (no archivos .apk sueltos)
    @($rutas | Where-Object { $_ -match '^/system/(app|priv-app)/[A-Za-z0-9._+-]+$' -and $_ -notmatch '\.apk$' })
}
function Get-Actualizaciones($rutas) { @($rutas | Where-Object { $_ -like '/data/app/*' }) }

function Invoke-Adb { & $adb -s $dev @args }
function Texto($salida) { (@($salida) -join "`n").Trim() }

& $adb connect $dev | Out-Null
if (((& $adb devices) -join "`n") -notmatch [regex]::Escape("$dev`tdevice")) { throw "La caja ($dev) no responde por ADB." }

$desact = @(Get-Desactivados (Texto (Invoke-Adb shell 'pm list packages -d')))
Write-Host "Paquetes desactivados en la caja: $($desact.Count)"

$plan = @()
foreach ($p in $desact) {
    if ($Conservar -contains $p) { Write-Host ("  CONSERVA   {0} (pedido con -Conservar)" -f $p) -ForegroundColor DarkGray; continue }
    if (($intocables -contains $p) -or ($p -match $intocablesRegex)) { Write-Host ("  INTOCABLE  {0}: paquete basico del sistema, no se borra" -f $p) -ForegroundColor Yellow; continue }
    $rutas = @(Get-Rutas (Texto (Invoke-Adb shell "dumpsys package $p")))
    $sys = @(Get-CarpetasSistema $rutas)      # @() obligatorio: con un solo resultado PowerShell lo devuelve como texto suelto
    $upd = @(Get-Actualizaciones $rutas)
    if ($sys.Count -eq 0 -and $upd.Count -eq 0) { Write-Host ("  SIN RUTA   {0}: no tiene carpeta en /system que borrar" -f $p) -ForegroundColor DarkGray; continue }
    if ($sys.Count -gt 1) { Write-Host ("  DUDOSO     {0}: tiene {1} carpetas en /system ({2}); no se toca" -f $p, $sys.Count, ($sys -join ', ')) -ForegroundColor Yellow; continue }
    $kb = '?'
    if ($sys.Count -eq 1) { $m = [regex]::Match((Texto (Invoke-Adb shell "du -sk $($sys[0])")), '^(\d+)'); if ($m.Success) { $kb = $m.Groups[1].Value } }
    $plan += [pscustomobject]@{ Paquete = $p; Carpeta = $(if ($sys.Count -eq 1) { $sys[0] } else { '' }); Actualizacion = ($upd.Count -gt 0); KB = $kb }
}

Write-Host ""
Write-Host "PLAN (lo que se borraria):" -ForegroundColor Cyan
if ($plan.Count -eq 0) { Write-Host "  nada"; return }
$plan | Format-Table Paquete, Carpeta, Actualizacion, KB -AutoSize | Out-String | Write-Host

if (-not $Ejecutar) {
    Write-Host "MODO PRUEBA: no se cambio nada en la caja. Revisa la lista; si esta bien, volve a correr con -Ejecutar." -ForegroundColor Green
    return
}

Write-Host "Se va a BORRAR de /system las $($plan.Count) apps de arriba (con respaldo en $Respaldo)." -ForegroundColor Yellow
if ((Read-Host "Escribi BORRAR para continuar") -ne 'BORRAR') { Write-Host "Cancelado: no se toco nada."; return }

# 1. Respaldo + comprobacion de que esta completo
foreach ($it in $plan) {
    if (-not $it.Carpeta) { continue }
    $sub = if ($it.Carpeta -like '/system/priv-app/*') { 'priv-app' } else { 'app' }
    $nombre = Split-Path $it.Carpeta -Leaf
    $dest = Join-Path $Respaldo $sub
    New-Item -ItemType Directory -Force $dest | Out-Null
    Write-Host "Respaldando $($it.Carpeta)..."
    & $adb -s $dev pull $it.Carpeta "$dest\" | Out-Null
    # Cada archivo regular de la caja tiene que estar en la PC con el MISMO tamano (los enlaces simbolicos que adb copia de mas no molestan)
    $archivos = @((Invoke-Adb shell "find $($it.Carpeta) -type f") | ForEach-Object { "$_".Trim() } | Where-Object { $_ -like "$($it.Carpeta)/*" })
    if ($archivos.Count -eq 0) { throw "No pude listar los archivos de $($it.Carpeta) para verificar el respaldo. No se borro NADA." }
    $problemas = @()
    foreach ($f in $archivos) {
        $local = Join-Path (Join-Path $dest $nombre) ($f.Substring($it.Carpeta.Length + 1) -replace '/', '\')
        $m = [regex]::Match((Texto (Invoke-Adb shell "ls -l $f")), '(\d+)\s+\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}')
        if (-not $m.Success) { $problemas += "$f (no pude leer su tamano en la caja)" }
        elseif (-not (Test-Path -LiteralPath $local)) { $problemas += "$f (falta en el PC)" }
        elseif ((Get-Item -LiteralPath $local).Length -ne [int64]$m.Groups[1].Value) { $problemas += "$f (tamano distinto)" }
    }
    if ($problemas.Count) { throw "Respaldo incompleto de $($it.Carpeta): $($problemas -join '; '). No se borro NADA." }
}
$plan | Export-Csv (Join-Path $Respaldo 'lista.csv') -NoTypeInformation -Encoding UTF8
Write-Host "Respaldo completo en $Respaldo" -ForegroundColor Green

# 2. root + remount (antes de cambiar nada en la caja)
Write-Host "adb root..."
& $adb -s $dev root | Out-Null
$root = $false
for ($i = 0; $i -lt 12 -and -not $root; $i++) {
    Start-Sleep 2
    & $adb connect $dev | Out-Null
    if ((Texto (Invoke-Adb shell id)) -match 'uid=0\(root\)') { $root = $true }
}
if (-not $root) { throw "No hay root en esta caja (adb root no funciono). No se borro nada de /system." }
$rm = Texto (& $adb -s $dev remount)
if ($rm -notmatch 'succeeded') { throw "adb remount fallo ($rm). No se borro nada de /system." }

# 3. Actualizaciones de /data/app de apps de sistema
foreach ($it in ($plan | Where-Object { $_.Actualizacion })) {
    $r = Texto (Invoke-Adb shell "pm uninstall $($it.Paquete)")
    Write-Host ("  pm uninstall {0}: {1}" -f $it.Paquete, $r)
}

# 4. Borrado (solo rutas que cumplen el patron)
foreach ($it in $plan) {
    if (-not $it.Carpeta) { continue }
    if ($it.Carpeta -notmatch '^/system/(app|priv-app)/[A-Za-z0-9._+-]+$') { throw "Ruta rara, no la toco: $($it.Carpeta)" }
    Invoke-Adb shell "rm -rf $($it.Carpeta)" | Out-Null
    $quedo = Texto (Invoke-Adb shell "ls -d $($it.Carpeta) 2>/dev/null")
    if ($quedo) { Write-Host "  NO se pudo borrar $($it.Carpeta)" -ForegroundColor Red } else { Write-Host "  borrada $($it.Carpeta)" }
}

# 5. Cerrar
& $adb -s $dev unroot | Out-Null
Start-Sleep 3
& $adb connect $dev | Out-Null
Write-Host "Reiniciando la caja. Cuando arranque: adb connect $dev y comproba con  adb shell pm list packages -d" -ForegroundColor Cyan
& $adb -s $dev reboot
