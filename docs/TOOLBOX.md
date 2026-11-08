# OpenKatmai Toolbox

Optional on-device tools for **DJI RC 2 firmware v10.00.0800 (2026.07.30)**.
The companion uses OpenKatmai's existing loopback ADB service to run selected
actions as **UID 1000 system**. Installing the APK alone does not enable ADB or root.

![OpenKatmai Toolbox on a real RC 2](../assets/toolbox.png)

## Features

- Overview: identity, external firmware, memory, storage, battery and runtime state.
- Apps: list third-party packages and open a selected launcher activity.
- Install APK: select an APK directly in internal Download, confirm installation
  or update, and optionally request `arm64-v8a`.
- Logs: read the latest 100 logcat entries, bounded to 45 KB. Logs stay on screen;
  they are not uploaded or sent to the project maintainers.
- Settings, developer settings and factory-assistant Shell shortcuts.
- Optional **Toolbox** Quick Settings tile.

ARM64 packages use the existing bounded `launch64.sh` workflow. Install the helper
below and follow [the ARM64 guide](64-BIT-APPS.md). APK compatibility and SDK or
aircraft connectivity are separate from successful installation and UI launch.

## Install

Activate system ADB using [First-time setup](SETUP.md), then download
[OpenKatmai-Toolbox.apk](https://github.com/hazim5-stack/OpenKatmai/releases/download/toolbox-v1.0.0/OpenKatmai-Toolbox.apk).
Compare its SHA-256 with the release's `SHA256SUMS.txt` before installation.

```powershell
adb -s SERIAL install -r .\OpenKatmai-Toolbox.apk
adb -s SERIAL push .\scripts\launch64.sh /data/data/com.dpad.fuli/launch64.sh
adb -s SERIAL shell chmod 700 /data/data/com.dpad.fuli/launch64.sh
adb -s SERIAL shell sha256sum /data/data/com.dpad.fuli/launch64.sh
(Get-FileHash .\scripts\launch64.sh -Algorithm SHA256).Hash
adb -s SERIAL shell am start -n org.openkatmai.toolbox/.MainActivity
adb -s SERIAL shell cmd statusbar add-tile org.openkatmai.toolbox/.ToolboxTileService
```

Replace `SERIAL` with your device serial. Ensure the two helper hashes match before
using ARM64 app launch. The local daemon must listen on `127.0.0.1:5555`, even when
your computer connects through USB. Reactivate it after each reboot. The companion
does not persist ADB, elevate itself to root, or inherit the computer's shell UID.

Copy APKs to **internal Download** by MTP, tap **Install APK**, select a file,
and confirm. The system-side install stages the APK in a unique temporary file
inside the factory assistant's data directory and removes it on completion or failure.
The source APK in Download is retained. There is no arbitrary shell-command input
and external activity intents do not accept action/command parameters.

CPU/GPU tuning, fan control, swap changes, Magisk modules, boot animations, partition
tools and bootloader actions from the original toolkit are not part of this adaptation.

## Build and attribution

Build on Windows with JDK 17+, Android platform 30 and build-tools 35.0.0:

```powershell
.\src\toolbox\build.ps1
```

Output: `src/toolbox/build/OpenKatmai-Toolbox.apk`. The local signing key is created
in the ignored build directory; keep it to sign future updates to your local build.
Locally generated keys differ from the published release key, so rebuilding does
not produce a byte-identical release or an APK that updates a differently signed copy.

UI components are adapted from **RC2 Unlock Toolkit**, under MIT. See
[attribution](../src/toolbox/NOTICE.md) and [upstream license](../src/toolbox/UPSTREAM-LICENSE.txt).
The application has its own package ID and can coexist with the upstream Toolbox.

## Validation

On the connected RC 2, real UI taps confirmed overview data through UID 1000 ADB,
the app chooser, launching the original Toolbox, reading logs, reinstalling the
same upstream Toolbox APK from Download (`Success`), and opening Android settings,
developer settings and the factory assistant's Shell activity. The test APK copy
was removed afterward. No command was submitted through the Shell UI.

The helper was deployed with matching host/device hashes. The companion's ARM64
selection/launch path has not separately been validated in this test; earlier
helper validation is recorded in [findings](FINDINGS.md). A real tap on the Toolbox
Quick Settings tile opened the companion successfully. Primary zygote remained stopped,
secondary running and persistent policy `4` after the functional tests.

The APK passed signature and zip-alignment verification. A host protocol fixture
tests UID rejection, packet-size bounds, late stream-close handling, quoting,
package-command injection rejection and Download path traversal rejection:

```powershell
New-Item -ItemType Directory -Force .\src\toolbox\build\tests | Out-Null
javac --release 8 -d .\src\toolbox\build\tests .\src\toolbox\java\org\openkatmai\toolbox\LocalAdb.java .\src\toolbox\java\org\openkatmai\toolbox\Actions.java .\src\toolbox\tests\TransportTest.java
java -cp .\src\toolbox\build\tests org.openkatmai.toolbox.TransportTest
```

This host-only test temporarily binds `127.0.0.1:5555` and fails if occupied.
It does not connect to the RC 2. The live device checks above are separate evidence.

## Remove

```powershell
adb -s SERIAL shell cmd statusbar remove-tile org.openkatmai.toolbox/.ToolboxTileService
adb -s SERIAL uninstall org.openkatmai.toolbox
```

Removing the companion does not remove your installed APKs or the original Toolbox.
Remove `launch64.sh` only if you no longer use it from other OpenKatmai shortcuts.
