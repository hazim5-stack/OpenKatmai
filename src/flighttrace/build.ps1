param([switch]$BridgeOnly,
 [string]$SdkPath="$env:LOCALAPPDATA\Android\Sdk",
 [string]$JavaHome='C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot',
 [string]$NdkVersion='28.2.13676358')
$ErrorActionPreference='Stop'
$bt=Join-Path $SdkPath 'build-tools\35.0.0'
$android=Join-Path $SdkPath 'platforms\android-35\android.jar'
$j=Join-Path $JavaHome 'bin'
$ndk=Join-Path $SdkPath "ndk\$NdkVersion\toolchains\llvm\prebuilt\windows-x86_64"
function Check {if($LASTEXITCODE -ne 0){throw "Build failed: $LASTEXITCODE"}}
Push-Location $PSScriptRoot
try {
 foreach($dir in @('build','build/bridge-manifest','build/bridge-classes','build/bridge-dex','build/bridge-native/lib/armeabi-v7a','build/gen','build/app-classes','build/app-dex','assets')){New-Item -ItemType Directory -Force $dir | Out-Null}
 if(!(Test-Path build/local.keystore)){
  & "$j/keytool.exe" -genkeypair -keystore build/local.keystore -storepass android -keypass android -alias local -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=OpenKatmai FlightTrace Local Development';Check
 }
 function Sign([string]$unsigned,[string]$output){
  & "$bt/zipalign.exe" -f 4 $unsigned build/aligned.apk;Check
  & "$j/java.exe" -jar "$bt/lib/apksigner.jar" sign --ks build/local.keystore --ks-key-alias local --ks-pass pass:android --key-pass pass:android --out $output build/aligned.apk;Check
  & "$j/java.exe" -jar "$bt/lib/apksigner.jar" verify --verbose $output;Check
  & "$bt/zipalign.exe" -c 4 $output;Check
 }
 & "$ndk/bin/clang.exe" --target=armv7a-linux-androideabi30 --sysroot="$ndk/sysroot" -shared -fPIC -O2 -Wall -Wextra -o build/bridge-native/lib/armeabi-v7a/libflightbridge.so native/agent.c -llog;Check
 $sources=@(Get-ChildItem bridge -Recurse -Filter *.java | ForEach-Object FullName)
 & "$j/javac.exe" -encoding UTF-8 --release 8 -classpath $android -d build/bridge-classes @sources;Check
 & "$j/jar.exe" cf build/bridge-classes.jar -C build/bridge-classes .;Check
 & "$j/java.exe" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 24 --output build/bridge-dex build/bridge-classes.jar;Check
 Copy-Item -LiteralPath BridgeManifest.xml -Destination build/bridge-manifest/AndroidManifest.xml -Force
 & "$bt/aapt.exe" package -f -M build/bridge-manifest/AndroidManifest.xml -I $android -F build/bridge-unsigned.apk;Check
 Push-Location build/bridge-dex
 try{& "$bt/aapt.exe" add ../bridge-unsigned.apk classes.dex;Check}finally{Pop-Location}
 Push-Location build/bridge-native
 try{& "$bt/aapt.exe" add ../bridge-unsigned.apk lib/armeabi-v7a/libflightbridge.so;Check}finally{Pop-Location}
 Sign build/bridge-unsigned.apk assets/bridge-helper.apk
 if(!$BridgeOnly){
  # --- osmdroid map library (pinned, verified) ---
  $osmVersion='6.1.20'
  $osmAar="build/deps/osmdroid-android-$osmVersion.aar"
  $osmSha256='2cbec2a2a1a671a76849897748080079a3158a7a4c8554b86f2c75860f91046a'
  if(!(Test-Path $osmAar)){
   Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/osmdroid/osmdroid-android/$osmVersion/osmdroid-android-$osmVersion.aar" -OutFile $osmAar
  }
  if((Get-FileHash $osmAar -Algorithm SHA256).Hash.ToLower() -ne $osmSha256){throw "osmdroid AAR hash mismatch"}
  $osmDir="build/deps/osmdroid-$osmVersion"
  if(!(Test-Path "$osmDir/classes.jar")){
   New-Item -ItemType Directory -Force $osmDir | Out-Null
   Expand-Archive -LiteralPath $osmAar -DestinationPath $osmDir -Force
  }
  # The AAR ships resource folders at its root; aapt wants them under one -S dir.
  $osmRes='build/deps/osmdroid-res'
  if(!(Test-Path $osmRes)){
   New-Item -ItemType Directory -Force $osmRes | Out-Null
   Get-ChildItem "$osmDir/res" -Directory | ForEach-Object {Copy-Item -Recurse -LiteralPath $_.FullName -Destination $osmRes}
  }
  # aapt1 in build-tools 35 silently uses only the first -S, so merge the app
  # resources and osmdroid resources into one staging directory first.
  $resMerged='build/deps/res-merged'
  if(Test-Path $resMerged){Remove-Item -Recurse -Force $resMerged}
  New-Item -ItemType Directory -Force $resMerged | Out-Null
  function Merge-Res($src,$dst){
   Get-ChildItem $src -Directory | ForEach-Object {
    $target=Join-Path $dst $_.Name
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item -Recurse -Force -Path (Join-Path $_.FullName '*') -Destination $target
   }
  }
  Merge-Res res $resMerged
  Merge-Res $osmRes $resMerged
  # Two passes over the SAME merged resource set: consistent IDs, two packages.
  New-Item -ItemType Directory -Force build/gen,build/gen-lib | Out-Null
  & "$bt/aapt.exe" package -f -M AndroidManifest.xml -S $resMerged -A assets -I $android -J build/gen -F build/app-unsigned.apk;Check
  & "$bt/aapt.exe" package -f -M AndroidManifest.xml -S $resMerged -I $android -J build/gen-lib --custom-package org.osmdroid.library -F build/gen-lib/dummy.apk;Check
  Remove-Item -Force build/gen-lib/dummy.apk
  $sources=@(Get-ChildItem app,build/gen,build/gen-lib -Recurse -Filter *.java | ForEach-Object FullName)
  & "$j/javac.exe" -encoding UTF-8 --release 8 -classpath "$android;$osmDir/classes.jar" -d build/app-classes @sources;Check
  & "$j/jar.exe" cf build/app-classes.jar -C build/app-classes .;Check
  & "$j/java.exe" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --lib $android --min-api 26 --output build/app-dex build/app-classes.jar "$osmDir/classes.jar";Check
  Push-Location build/app-dex
  try{& "$bt/aapt.exe" add ../app-unsigned.apk classes.dex;Check}finally{Pop-Location}
  Sign build/app-unsigned.apk build/FlightTrace.apk
  Write-Output "Built: $PSScriptRoot\build\FlightTrace.apk"
 }
}finally{Pop-Location}
