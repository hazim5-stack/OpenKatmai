param(
    [string]$SdkPath = $env:ANDROID_HOME,
    [string]$BuildToolsVersion = '35.0.0',
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (!$SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (!$SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$bt = Join-Path $SdkPath "build-tools\$BuildToolsVersion"
$androidJar = Join-Path $SdkPath 'platforms\android-30\android.jar'
foreach ($required in @($androidJar, "$bt\aapt.exe", "$bt\zipalign.exe", "$bt\lib\d8.jar", "$bt\lib\apksigner.jar")) {
    if (!(Test-Path -LiteralPath $required)) { throw "Required SDK file is missing: $required" }
}
function Java-Tool([string]$name) {
    if ($JavaHome) { return Join-Path $JavaHome "bin\$name.exe" }
    return (Get-Command $name -ErrorAction Stop).Source
}
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$java = Java-Tool 'java'
$javac = Java-Tool 'javac'
$jar = Java-Tool 'jar'
$keytool = Java-Tool 'keytool'
Push-Location $PSScriptRoot
try {
    foreach ($dir in @('build','build\gen','build\classes','build\dex')) { New-Item -ItemType Directory -Force $dir | Out-Null }
    & "$bt\aapt.exe" package -f -M AndroidManifest.xml -S res -I $androidJar -J build\gen -F build\resources.apk
    Check-Exit
    $sources = @(Get-ChildItem java,build\gen -Recurse -Filter '*.java' | ForEach-Object FullName)
    & $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -d build\classes @sources
    Check-Exit
    & $jar cf build\classes.jar -C build\classes .
    Check-Exit
    & $java -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib $androidJar --min-api 24 --output build\dex build\classes.jar
    Check-Exit
    Copy-Item -LiteralPath build\resources.apk -Destination build\unsigned.apk -Force
    Push-Location build\dex
    try { & "$bt\aapt.exe" add ..\unsigned.apk classes.dex; Check-Exit } finally { Pop-Location }
    & "$bt\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
    Check-Exit
    if (!(Test-Path -LiteralPath build\local.keystore)) {
        & $keytool -genkeypair -keystore build\local.keystore -storepass android -keypass android -alias local -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=OpenKatmai Local Tiles'
        Check-Exit
    }
    & $java -jar "$bt\lib\apksigner.jar" sign --ks build\local.keystore --ks-key-alias local --ks-pass pass:android --key-pass pass:android --out build\OpenKatmai-Tiles.apk build\aligned.apk
    Check-Exit
    & $java -jar "$bt\lib\apksigner.jar" verify --verbose build\OpenKatmai-Tiles.apk
    Check-Exit
    & "$bt\zipalign.exe" -c 4 build\OpenKatmai-Tiles.apk
    Check-Exit
    Write-Output "Built: $PSScriptRoot\build\OpenKatmai-Tiles.apk"
} finally { Pop-Location }
