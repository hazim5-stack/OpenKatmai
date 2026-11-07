# Application shortcuts in the notification / Quick Settings panel

The RC 2 panel containing Wi-Fi and Bluetooth accepts Android `TileService`
components on the recorded V14 build. These are **Quick Settings tiles**, not
ordinary notification icons. A tile opens an installed app; it does not embed
that app's interface into System UI or change the default Home application.

Real-device testing established ordinary application tiles, a Shell shortcut,
and an ARM64 launch shortcut with temporary runtime preparation. The generic
source included here has been built and signature/alignment checked; its
placeholder ARM64 target is not an installed or separately device-tested app.

## 1. Choose the type of tile

- **App:** launch an ordinary installed package through its launcher intent.
  This is sufficient for the recorded 32-bit launcher and DJI Fly.
- **ARM64 App:** run the protected `launch64.sh` helper through local system ADB,
  then open the configured activity after preparing the 64-bit runtime.
- **Shell:** use local system ADB to open the assistant's non-exported
  `com.dpad.fuli/.ShellCommandActivity`. If local ADB is unavailable, open the
  assistant's exported main page instead; choose Shell manually there.

`cmd statusbar add-tile` only registers an existing tile service. It cannot turn
an arbitrary target APK into a tile. Either the target app already supplies a
TileService, or you install a small wrapper such as the example below.

## 2. Configure the example source

Source: [`src/quick-tiles`](../src/quick-tiles). Wrapper package:
`org.openkatmai.tiles`. It uses Android's framework APIs and a small fixed-endpoint
ADB client; there are no downloaded Gradle or third-party library dependencies.

Edit [`Config.java`](../src/quick-tiles/java/org/openkatmai/tiles/Config.java):

```java
static final String PLAIN_PACKAGE = "fr.neamar.kiss";
static final String PLAIN_LABEL = "Launcher";
static final String ARM64_COMPONENT =
    "com.example.arm64app/com.example.arm64app.MainActivity";
static final String ARM64_LABEL = "ARM64 App";
```

Replace the ARM64 placeholder using the real result of:

```powershell
adb -s SERIAL shell cmd package resolve-activity --brief com.example.arm64app
```

When changing the ordinary app, also update the `<queries><package .../></queries>`
entry in `AndroidManifest.xml` to the same package. For example, a DJI Fly tile
uses `PLAIN_PACKAGE = "dji.go.v5"` and a matching `dji.go.v5` query. Package queries
allow Android 11 package discovery; they do not grant permissions to that app.

For names shown before the service begins listening, update the corresponding
service's `android:label` in the manifest too. The service updates the live label
from Config when listening.

To add more simultaneous ordinary-app tiles, create another public TileService
class based on `AppTileService`, give it its own fixed package/label, declare it
in the manifest with the same Quick Settings action and binding permission, and
register that new component. For multiple ARM64 tiles, give each a fixed,
validated target and route it through the same protected helper. Do not expose
arbitrary shell text or endpoints through exported activities or intent extras.

The wrapper's bridge activity is private and finishes before opening the target
task. The Shell test exposed a task-ordering issue: returning to an older
assistant page could cover the requested Shell page. A separate temporary task
and `FLAG_ACTIVITY_CLEAR_TOP` address that issue in this template.

## 3. Build on Windows

Install JDK 17, Android SDK platform **android-30**, and build-tools **35.0.0**.
SDK/JDK installation paths vary; pass yours explicitly or use `ANDROID_HOME` /
`ANDROID_SDK_ROOT` and `JAVA_HOME`.

From the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\src\quick-tiles\build.ps1 `
  -SdkPath 'C:\Android\Sdk' `
  -JavaHome 'C:\Program Files\Microsoft\jdk-17'
```

Replace those paths with existing directories. `-BuildToolsVersion` can select
another compatible installed version; the recorded build used 35.0.0. This
PowerShell execution-policy override is scoped to that build process.

The script runs aapt, javac, D8, zipalign and apksigner and fails on nonzero tool
exit status. Output:

```text
src/quick-tiles/build/OpenKatmai-Tiles.apk
src/quick-tiles/build/local.keystore
```

The key is generated locally with the example development password `android`.
It is not a DJI/system signing key and confers no privileged UID. Keep the same
key for subsequent APK updates; do not publish it. Build artifacts and keys are
ignored by Git. To publish a production release, use your own signing procedure
rather than treating this example key as a release credential.

## 4. Deploy the helper and install the wrapper

Activate ADB using [First-time setup](SETUP.md) and select the correct device
serial. For the ARM64 tile, install the actual target app with the explicit ABI
and verify its package as described in [64-bit apps](64-BIT-APPS.md).

```powershell
adb -s SERIAL push .\scripts\launch64.sh /data/data/com.dpad.fuli/launch64.sh
adb -s SERIAL shell chmod 700 /data/data/com.dpad.fuli/launch64.sh
Get-FileHash .\scripts\launch64.sh -Algorithm SHA256
adb -s SERIAL shell sha256sum /data/data/com.dpad.fuli/launch64.sh
adb -s SERIAL install -r .\src\quick-tiles\build\OpenKatmai-Tiles.apk
```

Compare the helper hashes. The wrapper itself is a small Java-only app; it does
not need to be installed as ARM64. Keeping the wrapper on the default 32-bit path
lets its buttons work while primary zygote is stopped.

The wrapper requests INTERNET to reach **127.0.0.1:5555**. Its code does not accept
other host addresses, caller-provided shell commands or target components from
outside the package. Its UID remains an ordinary app UID. Privileged operations
are performed only by the already running system-UID ADB daemon. Therefore:

- USB can be the computer's only connection, but local TCP 5555 must still work
  for the ARM64 and direct Shell buttons.
- No pairing code is used by this local clean-daemon protocol.
- An AUTH response, wrong UID, absent helper or unavailable port is an error.
  The template does not bypass authentication or start ADB by itself.

## 5. Add the buttons to the panel

```powershell
adb -s SERIAL shell cmd statusbar check-support
# Expected on the verified build: true
adb -s SERIAL shell cmd statusbar add-tile org.openkatmai.tiles/.AppTileService
adb -s SERIAL shell cmd statusbar add-tile org.openkatmai.tiles/.Arm64TileService
adb -s SERIAL shell cmd statusbar add-tile org.openkatmai.tiles/.ShellTileService
adb -s SERIAL shell cmd statusbar expand-settings
```

Swipe down on the remote if necessary and inspect the panel. The display labels
come from the wrapper's tile services, not the target APK filenames. Tile order
is controlled by DJI's System UI and may change when adding/removing a service.

Do not overwrite `settings secure sysui_qs_tiles` with a guessed list. On this
custom System UI it read `null` even while working custom tiles were visible.
Use `cmd statusbar add-tile` / `remove-tile` and verify the actual screen.

## 6. Verify real behavior

Tap the button on the remote and check the resulting UI. A successful command
or visible tile alone is not proof that its target app launched.

```powershell
adb -s SERIAL shell dumpsys activity activities
adb -s SERIAL logcat -d -s OpenKatmaiTiles
adb -s SERIAL shell getprop init.svc.zygote
adb -s SERIAL shell getprop init.svc.zygote_secondary
adb -s SERIAL shell getprop persist.dji.zygote_policy
```

For ARM64, look for `APP_READY` and `RESTORE_DONE` in the wrapper's log and the
correct foreground package. Check that the primary state returned to its
baseline, secondary stayed running, and persistent policy did not change.
Then inspect the app's own required permissions/services. Some apps may create
additional 64-bit processes later; see the limitations in the ARM64 guide.

For Shell, require foreground `com.dpad.fuli/.ShellCommandActivity` and the command
input page. Opening the Shell tile does **not** press Send or execute text already
in that input box. The assistant may retain old input text; inspect it before
manually sending any command.

Reopen each tile a second time to detect task-reuse problems. Reboot persistence
of panel placement is not established here. After any reboot, reactivate ADB
before expecting the ARM64 or direct Shell path to work.

## 7. Remove or update

Remove individual buttons without uninstalling target apps:

```powershell
adb -s SERIAL shell cmd statusbar remove-tile org.openkatmai.tiles/.AppTileService
adb -s SERIAL shell cmd statusbar remove-tile org.openkatmai.tiles/.Arm64TileService
adb -s SERIAL shell cmd statusbar remove-tile org.openkatmai.tiles/.ShellTileService
```

Rebuild with the same local signing key and use `adb install -r` to update the
wrapper. If you rename/remove a service, remove the old tile and add the new
component. No Home/default-launcher change is needed for any of these buttons.

To uninstall, remove the wrapper through Android's app settings. On the recorded
system-UID ADB setup, command-line uninstall could fail with an AppOps/package
identity exception even when the normal on-device uninstall confirmation worked.
Remove `/data/data/com.dpad.fuli/launch64.sh` only when no remaining tile uses it.
