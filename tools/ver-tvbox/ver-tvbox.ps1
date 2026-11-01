<#
  Ver en el PC la pantalla del TV box (como una capturadora), por Wi-Fi, con sonido (experimental).
  Usa lo que ya hay: ADB por red + screenrecord de la caja + ffplay del PC. Sin instalar nada.

  Uso:   .\ver-tvbox.ps1                 (o doble clic en ver-tvbox.cmd)
         .\ver-tvbox.ps1 -SinAudio       (solo imagen: no usa root)
         .\ver-tvbox.ps1 -RetrasoAudioMs 400   (si el sonido llega ANTES que la imagen, atrasalo)
         .\ver-tvbox.ps1 -Suave                (imagen mas fluida a cambio de ~1,5 s de retraso; el audio se atrasa igual)
         .\ver-tvbox.ps1 -Suave -BufferMs 800  (colchon mas chico: menos retraso, menos suavidad)
         .\ver-tvbox.ps1 -Ip 192.168.1.4 -Bitrate 3000000 -Size 1280x720

  - Cierra la ventana de video para terminar.
  - AUDIO: Android 7 no lo trae. Se captura con un programa propio (tv-audio\AudioCap.java) que lee "remote submix".
    Necesita ROOT por ADB mientras esta abierto: la caja queda con root por ADB (cualquiera en tu red podria
    conectarse como root). Al cerrar, el script lo revierte (adb unroot). Con -SinAudio no se usa root.
  - La tele sigue sonando: Android duplica el audio hacia la captura (medido: aparece un hilo DUPLICATING).
  - Android limita screenrecord a 3 minutos: el script lo reinicia solo (la ventana parpadea un instante).
  - Si la pantalla de la caja esta QUIETA, la imagen no se actualiza hasta que algo cambie (es normal).
  - La imagen y el sonido van por caminos separados: pueden no estar perfectamente sincronizados.
  - Necesita que el ADB de la caja siga abierto.
#>
param(
    [string]$Ip = '192.168.1.4',
    [int]$Port = 5555,
    [int]$Bitrate = 3000000,
    [string]$Size = '1280x720',
    [switch]$SinAudio,
    [int]$RetrasoAudioMs = 0,
    [switch]$Suave,           # imagen mas fluida a cambio de retraso: la ventana junta un colchon antes de mostrar
    [int]$BufferMs = 1500     # tamano del colchon en modo -Suave (el audio se atrasa lo mismo, salvo que indiques -RetrasoAudioMs)
)
$ErrorActionPreference = 'Continue'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$ffplay = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\ffplay.exe"
foreach ($x in $adb, $ffplay) { if (-not (Test-Path $x)) { throw "No encuentro $x" } }
$dev = "${Ip}:$Port"
$dex = Join-Path $PSScriptRoot 'tv-audio\audiocap.dex'
$title = "TV box ($Ip)"
if ($Suave -and -not $PSBoundParameters.ContainsKey('RetrasoAudioMs')) { $RetrasoAudioMs = $BufferMs }
# Opciones de ffplay para el video: normal = sin colchon (minimo retraso); suave = junta ~$BufferMs ms antes de empezar.
if ($Suave) {
    $probeBytes = [int]([math]::Max(200000, ($Bitrate / 8) * ($BufferMs / 1000.0) * 1.2))
    $opcV = "-framerate 25 -probesize $probeBytes -analyzeduration $($BufferMs * 1000)"
} else {
    $opcV = '-fflags nobuffer -flags low_delay -framedrop -probesize 32 -analyzeduration 0'
}

function Conectar {
    & $adb connect $dev | Out-Null
    return (((& $adb devices) -join "`n") -match [regex]::Escape("$dev`tdevice"))
}
if (-not (Conectar)) { throw "La caja ($dev) no responde por ADB. Esta encendida y en la misma red Wi-Fi? Tiene la depuracion activa?" }

$conAudio = -not $SinAudio
if ($conAudio) {
    if (-not (Test-Path $dex)) { Write-Host "Falta ${dex}: sigo solo con imagen." -ForegroundColor Yellow; $conAudio = $false }
}
$rooteado = $false
if ($conAudio) {
    & $adb -s $dev root 2>&1 | Out-Null
    Start-Sleep -Seconds 4
    if (-not (Conectar)) { Start-Sleep -Seconds 3; [void](Conectar) }
    $uid = ((& $adb -s $dev shell 'id') -join ' ')
    if ($uid -match 'uid=0') {
        $rooteado = $true
        & $adb -s $dev push $dex /data/local/tmp/audiocap.dex 2>&1 | Out-Null
    } else {
        Write-Host "No se pudo obtener root en la caja: sigo solo con imagen." -ForegroundColor Yellow
        $conAudio = $false
    }
}

# --- Archivos .cmd temporales (la forma fiable de encadenar salidas binarias en Windows) ---
$batV = Join-Path $env:TEMP 'ver-tvbox-video.cmd'
@"
@echo off
"$adb" -s $dev exec-out screenrecord --output-format=h264 --size $Size --bit-rate $Bitrate --time-limit 175 - | "$ffplay" -hide_banner -loglevel quiet -window_title "$title" -f h264 $opcV -
"@ | Set-Content -Encoding ASCII $batV

$batA = Join-Path $env:TEMP 'ver-tvbox-audio.cmd'
# El filtro lleva un "|": en un .cmd hay que ponerlo entre comillas o corta el comando como si fuera una tuberia.
$af = if ($RetrasoAudioMs -gt 0) { "-af `"adelay=$RetrasoAudioMs|$RetrasoAudioMs`"" } else { '' }
@"
@echo off
"$adb" -s $dev exec-out "CLASSPATH=/data/local/tmp/audiocap.dex app_process /system/bin AudioCap 2>/data/local/tmp/audiocap.err" | "$ffplay" -hide_banner -loglevel quiet -nodisp -f s16le -sample_rate 48000 -ch_layout stereo -fflags nobuffer -flags low_delay -probesize 32 -analyzeduration 0 $af -
"@ | Set-Content -Encoding ASCII $batA

function Limpiar {
    # corta los flujos en el PC
    Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'exec-out' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Get-Process ffplay -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    # ... y lo que quede corriendo en la caja
    $sr = ((& $adb -s $dev shell 'ps | grep screenrecord') -join ' ') -split '\s+' | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1
    if ($sr) { & $adb -s $dev shell "kill -2 $sr" | Out-Null }
    $ap = (& $adb -s $dev shell 'ps | grep app_process') | ForEach-Object { ($_ -split '\s+')[1] } | Where-Object { $_ -match '^\d+$' }
    foreach ($x in $ap) { & $adb -s $dev shell "kill $x" | Out-Null }
}
function VentanaAbierta { [bool](Get-Process ffplay -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'TV box*' }) }

Write-Host ("Mostrando la pantalla de $dev" + $(if ($conAudio) { ' con sonido' } else { ' (sin sonido)' }) + ". Cerra la ventana para terminar.") -ForegroundColor Cyan
try {
    $pa = $null
    if ($conAudio) { $pa = Start-Process cmd -ArgumentList '/c', "`"$batA`"" -PassThru -WindowStyle Hidden }
    $cerrada = $false
    while (-not $cerrada) {
        $t0 = Get-Date
        $pv = Start-Process cmd -ArgumentList '/c', "`"$batV`"" -PassThru -WindowStyle Hidden
        Start-Sleep -Seconds 5                  # tiempo para que abra la ventana
        while (-not $pv.HasExited) {
            if (-not (VentanaAbierta)) { $cerrada = $true; break }     # cerraste la ventana
            Start-Sleep -Milliseconds 500
        }
        $dur = ((Get-Date) - $t0).TotalSeconds
        # limpiar SOLO el video antes de reiniciar (el audio sigue)
        if (-not $cerrada) {
            Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'adb.exe' -and $_.CommandLine -match 'exec-out screenrecord' } |
                ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
            if ($dur -lt 160) { Write-Host "El video se corto a los $([int]$dur) s: termino." -ForegroundColor Yellow; break }
        }
    }
}
finally {
    Limpiar
    if ($rooteado) {
        & $adb -s $dev unroot 2>&1 | Out-Null
        Start-Sleep -Seconds 3
        [void](Conectar)
        Write-Host "Root de ADB revertido en la caja." -ForegroundColor DarkGray
    }
    Write-Host "Listo." -ForegroundColor Cyan
}
