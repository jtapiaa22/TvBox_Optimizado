<#
  Compila, instala el launcher en la caja y lo deja como Home.

  Uso:   .\install.ps1 192.168.1.4                                # instala y MUESTRA los otros launchers que hay
         .\install.ps1 192.168.1.4 -DesactivarOriginal            # ademas desactiva el original, si hay uno solo
         .\install.ps1 192.168.1.4 -LauncherOriginal <paquete>    # desactiva ESE paquete (el que elijas de la lista)
         .\install.ps1 192.168.1.4 -SoloDetectar                  # solo muestra los candidatos: no compila ni instala
         .\install.ps1 192.168.1.4 -Ajustes ajustes.json          # ademas importa un archivo de ajustes (dock, ocultas...)
         .\install.ps1 192.168.1.4 -Fondo foto.jpg                # ademas deja una imagen de fondo (jpg o png)

  El archivo de ajustes sale de la propia caja: mantener OK sobre una app > Personalizar launcher > Exportar ajustes, y
  luego `adb pull /sdcard/Android/data/com.jgx.launcher/files/launcher-settings.json`. Importarlo en otra caja deja el
  dock en el mismo orden; los paquetes que esa caja no tenga se descartan solos y sus apps nuevas van al final.

  Cada caja trae un launcher original con otro nombre (en la MXQ Pro 5G: com.droidlogic.mboxlauncher). El script lo
  busca (apps que responden a HOME), te lo muestra y NO desactiva nada si no se lo pides. Desactivar es reversible
  (adb shell pm enable <paquete>). Despues de reiniciar, comprueba que el firmware no lo haya reactivado.
#>
param(
    [string]$Tv = "192.168.1.4",
    [string]$LauncherOriginal = "",
    [switch]$DesactivarOriginal,
    [switch]$SoloDetectar,
    [string]$Ajustes = "",
    [string]$Fondo = ""
)
$nuestro = 'com.jgx.launcher'
# Paquetes que responden a HOME pero NO son un launcher que reemplazar (respaldo del sistema mientras no hay Home)
$ignorar = @($nuestro, 'android', 'com.android.settings', 'com.android.tv.settings', 'com.android.provision')

# adb: ANDROID_HOME, la ruta por defecto de Android Studio o el PATH
$adb = @("$env:ANDROID_HOME\platform-tools\adb.exe", "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe", "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $adb) { $c = Get-Command adb -ErrorAction SilentlyContinue; if ($c) { $adb = $c.Source } }
if (-not $adb) { throw "No encuentro adb. Instala las platform-tools del Android SDK o ponlas en el PATH." }
$s = "${Tv}:5555"

# Paquetes que responden a HOME (salida de `cmd package query-activities`: lineas "packageName=<paquete>")
function Get-PaquetesHome([string]$texto) {
    [regex]::Matches($texto, '(?m)^\s*packageName=(\S+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
}

function Mostrar-Candidatos {
    $txt = (& $adb -s $s shell 'cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME') -join "`n"
    $todos = @(Get-PaquetesHome $txt)
    $cand = @($todos | Where-Object { $ignorar -notcontains $_ })
    Write-Host "Apps que responden a HOME en la caja: $($todos -join ', ')"
    if ($cand.Count -eq 0) {
        Write-Host "No hay otro launcher activo que reemplazar (solo el nuestro y el respaldo del sistema)." -ForegroundColor Green
    } else {
        Write-Host "Candidatos a launcher original:" -ForegroundColor Yellow
        foreach ($p in $cand) { Write-Host ("  - {0}   {1}" -f $p, ((& $adb -s $s shell "pm path $p") -join ' ').Trim()) }
    }
    return , $cand
}

& $adb connect $s | Out-Null
if (((& $adb devices) -join "`n") -notmatch [regex]::Escape("$s`tdevice")) { throw "La caja ($s) no responde por ADB." }

if ($SoloDetectar) { [void](Mostrar-Candidatos); return }

& "$PSScriptRoot\build.ps1"
if (-not (Test-Path "$PSScriptRoot\build\launcher.apk")) { throw "La compilacion no genero launcher.apk: no instalo nada." }
$res = (& $adb -s $s install -r "$PSScriptRoot\build\launcher.apk") -join ' '
Write-Host $res
if ($res -notmatch 'Success') { throw "No se pudo instalar el launcher ($res). Si dice INSTALL_FAILED_UPDATE_INCOMPATIBLE, la caja tiene otro build firmado con otra clave: desinstala com.jgx.launcher primero." }
& $adb -s $s shell cmd package set-home-activity "$nuestro/.Launcher"

$cand = Mostrar-Candidatos
$objetivo = $LauncherOriginal
if (-not $objetivo -and $DesactivarOriginal) {
    if ($cand.Count -eq 1) { $objetivo = $cand[0] }
    else { Write-Host "-DesactivarOriginal necesita exactamente UN candidato (hay $($cand.Count)). Elige uno con -LauncherOriginal <paquete>." -ForegroundColor Yellow }
}
if ($objetivo) {
    if ($ignorar -contains $objetivo) { throw "$objetivo no se puede desactivar con este script (es el nuestro o el respaldo del sistema)." }
    Write-Host "Desactivando $objetivo (reversible: adb shell pm enable $objetivo)" -ForegroundColor Cyan
    & $adb -s $s shell pm disable-user --user 0 $objetivo
} elseif ($cand.Count -gt 0) {
    Write-Host "No desactive nada. Para hacerlo: .\install.ps1 $Tv -LauncherOriginal <paquete>" -ForegroundColor Yellow
}
& $adb -s $s shell input keyevent KEYCODE_HOME

if ($Ajustes -or $Fondo) {
    if ($Ajustes -and -not (Test-Path $Ajustes)) { throw "No existe el archivo de ajustes: $Ajustes" }
    if ($Fondo -and -not (Test-Path $Fondo)) { throw "No existe la imagen de fondo: $Fondo" }
    $dest = '/sdcard/Android/data/com.jgx.launcher/files'
    & $adb -s $s shell am start -n "$nuestro/.Launcher" | Out-Null   # la primera vez crea la carpeta de la app
    Start-Sleep 3
    & $adb -s $s shell "mkdir -p $dest"
    if ($Fondo) {
        $ext = [IO.Path]::GetExtension($Fondo).ToLower()
        if ($ext -notin '.jpg', '.jpeg', '.png') { throw "La imagen de fondo debe ser .jpg o .png (es $ext)" }
        & $adb -s $s shell "rm -f $dest/background.jpg $dest/background.jpeg $dest/background.png"   # solo vale una
        & $adb -s $s push $Fondo "$dest/background$ext"
        Write-Host "Imagen de fondo copiada: $Fondo" -ForegroundColor Green
    }
    if ($Ajustes) {
        & $adb -s $s push $Ajustes "$dest/launcher-settings.json"
        & $adb -s $s shell am start -n "$nuestro/.Launcher" --ez import_settings true | Out-Null
        Write-Host "Ajustes importados desde $Ajustes" -ForegroundColor Green
    } else {
        & $adb -s $s shell am force-stop $nuestro     # que lea la imagen al volver a abrirse
        & $adb -s $s shell input keyevent KEYCODE_HOME
    }
}
