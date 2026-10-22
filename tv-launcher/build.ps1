# Compila el launcher y genera build\launcher.apk (sin Gradle: aapt2 + javac + d8 + apksigner)
$ErrorActionPreference = "Continue"  # native tools write notes to stderr; exit codes are checked below
$root = $PSScriptRoot
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\35.0.0"
$jar  = "$sdk\platforms\android-35\android.jar"
$jdk  = (Get-ChildItem "$env:USERPROFILE\.jdks\*\bin\javac.exe" | Select-Object -First 1).Directory.FullName
$out  = "$root\build"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$out\classes" | Out-Null

& "$bt\aapt2.exe" link --manifest "$root\AndroidManifest.xml" -I $jar --min-sdk-version 25 --target-sdk-version 25 -o "$out\base.apk"
if ($LASTEXITCODE) { throw "aapt2 failed" }
& "$jdk\javac.exe" --release 8 -Xlint:-options -cp $jar -d "$out\classes" (Get-ChildItem "$root\src" -Recurse -Filter *.java).FullName
if ($LASTEXITCODE) { throw "javac failed" }
$classes = (Get-ChildItem "$out\classes" -Recurse -Filter *.class).FullName
& "$bt\d8.bat" --lib $jar --min-api 25 --output $out @classes
if ($LASTEXITCODE) { throw "d8 failed" }

# add classes.dex to the apk
python -I -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a'); z.write(sys.argv[2],'classes.dex'); z.close()" "$out\base.apk" "$out\classes.dex"
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"

$ks = "$root\debug.keystore"
if (-not (Test-Path $ks)) {
    & "$jdk\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JGX Launcher" | Out-Null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\launcher.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner failed" }
Write-Host "OK: $out\launcher.apk"
