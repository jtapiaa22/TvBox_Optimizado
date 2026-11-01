# Compila el launcher y genera build\launcher.apk (sin Gradle: aapt2 + javac + d8 + apksigner).
# Detecta solo el Android SDK, las build-tools y la plataforma MAS NUEVAS instaladas, y un JDK. No hace falta Python.
$ErrorActionPreference = "Continue"  # las herramientas escriben avisos por stderr; los codigos de salida se revisan abajo
$root = $PSScriptRoot

# --- Android SDK (ANDROID_HOME, ANDROID_SDK_ROOT o la ruta por defecto de Android Studio) ---
$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk") |
    Where-Object { $_ -and (Test-Path "$_\build-tools") } | Select-Object -First 1
if (-not $sdk) { throw "No encuentro el Android SDK. Instala Android Studio (o las command-line tools) o define ANDROID_HOME. Hacen falta build-tools y una plataforma (platforms/android-NN)." }

# build-tools: la version mas nueva que tenga aapt2
$bt = Get-ChildItem "$sdk\build-tools" -Directory |
    Where-Object { Test-Path "$($_.FullName)\aapt2.exe" } |
    Sort-Object { try { [version]($_.Name -replace '-.*$', '') } catch { [version]'0.0' } } -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $bt) { throw "No hay build-tools con aapt2 en $sdk\build-tools. Instalalas desde el SDK Manager." }

# plataforma: el android.jar de la API mas alta instalada (el launcher apunta a SDK 25 pero compila contra cualquiera >= 25)
$jar = Get-ChildItem "$sdk\platforms" -Directory |
    Where-Object { (Test-Path "$($_.FullName)\android.jar") -and (($_.Name -replace '^android-', '') -as [double]) -ge 25 } |
    Sort-Object { [double]($_.Name -replace '^android-', '') } -Descending |
    Select-Object -First 1 | ForEach-Object { "$($_.FullName)\android.jar" }
if (-not $jar) { throw "No hay ninguna plataforma android-25 o mas nueva en $sdk\platforms. Instala una (por ejemplo Android 14, API 34)." }

# --- JDK (JAVA_HOME, ~\.jdks, PATH o el JBR de Android Studio) ---
$javac = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { $javac = "$env:JAVA_HOME\bin\javac.exe" }
if (-not $javac) { $javac = (Get-ChildItem "$env:USERPROFILE\.jdks\*\bin\javac.exe" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName }
if (-not $javac) { $c = Get-Command javac -ErrorAction SilentlyContinue; if ($c) { $javac = $c.Source } }
if (-not $javac) { $javac = @("$env:ProgramFiles\Android\Android Studio\jbr\bin\javac.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1 }
if (-not $javac) { throw "No encuentro un JDK (javac). Instala un JDK 17 o 21 o define JAVA_HOME." }
$jdk = Split-Path $javac -Parent
$env:JAVA_HOME = Split-Path $jdk -Parent    # d8.bat y apksigner.bat lo necesitan

Write-Host "SDK: $sdk"
Write-Host "build-tools: $(Split-Path $bt -Leaf) · plataforma: $(Split-Path (Split-Path $jar -Parent) -Leaf) · JDK: $env:JAVA_HOME"

$out = "$root\build"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$out\classes" | Out-Null

& "$bt\aapt2.exe" link --manifest "$root\AndroidManifest.xml" -I $jar --min-sdk-version 25 --target-sdk-version 25 -o "$out\base.apk"
if ($LASTEXITCODE) { throw "aapt2 failed" }
& "$jdk\javac.exe" --release 8 -Xlint:-options -cp $jar -d "$out\classes" (Get-ChildItem "$root\src" -Recurse -Filter *.java).FullName
if ($LASTEXITCODE) { throw "javac failed" }
$classes = (Get-ChildItem "$out\classes" -Recurse -Filter *.class).FullName
& "$bt\d8.bat" --lib $jar --min-api 25 --output $out @classes
if ($LASTEXITCODE) { throw "d8 failed" }

# agrega classes.dex al APK (con .NET: no hace falta Python)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\base.apk", 'Update')
try { [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\classes.dex", 'classes.dex') }
finally { $zip.Dispose() }
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "zipalign failed" }

$ks = "$root\debug.keystore"
if (-not (Test-Path $ks)) {
    & "$jdk\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JGX Launcher" | Out-Null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\launcher.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner failed" }
Write-Host "OK: $out\launcher.apk"
