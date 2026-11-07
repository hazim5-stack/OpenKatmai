# First-time setup: factory assistant, Shell and working ADB

This guide covers the recorded DJI RC 2 (`rc331`) running **v10.00.0800 (2026.07.30)** (Android build `V14.00.00.04`),
Android 11. It produces a **UID 1000 system shell, not UID 0 root**. It does not
unlock the bootloader, flash firmware, or modify system partitions. Other
firmware builds need their own compatibility checks.

## 1. Prepare the files on the computer

Install Android platform-tools and use PowerShell for the examples. Run commands
from the repository root unless stated otherwise. Keep scripts as UTF-8 text
with LF line endings; do not save them as `.sh.txt`.

Collect:

- `adbd-root`: obtain and verify it using [the payload guide](../payloads/README.md).
- `scripts/runroot.sh`: first activation.
- `scripts/restart.sh`: later activation after reboot.
- A compatible original factory-assistant APK for `com.dpad.fuli`, if you cannot
  already open the installed assistant. `DJI-Factory-Assistant.apk` is a local
  filename used during setup, not a universal official download name.

The local assistant APK inspected during this work had package `com.dpad.fuli`,
version `1.0.08.29-5e7f0af3`, version code `155`, and SHA-256:

```text
58b176eb1e17cacb7522914d282a69a677603ea9026993fc143c6a390211e44f
```

This identifies that copy; it is not a substitute for verifying its origin and
DJI signature. No assistant APK is distributed by this repository. An arbitrary
APK renamed to that filename will not acquire system permissions.

Verify the required ADB payload before copying it:

```powershell
(Get-FileHash .\adbd-root -Algorithm SHA256).Hash
# Expected: F45E7917C094E6905557057B8F2AF516C658458E675C23EB00DE7FE34CB6B3AB
```

## 2. Copy to the remote's internal Download folder

Connect a USB-C **data** cable. Select **File transfer / MTP** in the remote's
USB options. USB tethering, MIDI and charging-only are different modes; enabling
them does not bypass the stock ADB production gate.

In Windows Explorer, open the remote's **Internal shared storage → Download**.
Copy these files directly into that folder:

```text
Download/
  DJI-Factory-Assistant.apk    # your compatible original copy, if needed
  adbd-root
  runroot.sh
  restart.sh
```

Copy the script files themselves, not the containing `scripts` directory, when
using the exact commands below. Android exposes internal shared storage through
`/sdcard`; **an external microSD card is not required**. Here
`/sdcard/Download/runroot.sh` means the internal file just copied.

## 3. Open/install the assistant from the remote

The owner-recorded route on this unit was:

1. Open the remote's **Storage** screen.
2. Choose **Audio** to enter the available file-selection/browser surface.
3. Navigate to internal **Download**, where you already copied the assistant APK.
4. Select the compatible assistant APK, accept the installation/update if offered,
   and choose **Open**. If the assistant is already installed and accessible,
   simply open it.
5. The factory assistant appears as **开发助手**. Select
   **SHELL命令测试 / shell命令测试** (Shell command test).

The Storage/Audio route is the owner's recorded UI path, not a claim that every
DJI file picker exposes APKs. If the APK is gray or absent, check that it is an
actual `.apk` in internal Download and that the file browser is not restricting
selection to audio. A filtered picker does not prove the APK is incompatible.
Do not rename an APK to an audio extension. If installation says that the user
cannot install that package, record the exact message and verify the package and
signature; developer mode alone does not remove those restrictions.

The verified installed launcher component is `com.dpad.fuli/.DevActivity`; the
Shell page is `com.dpad.fuli/.ShellCommandActivity`. These names are useful only
after you have a working privileged connection; they do not bootstrap ADB.

## 4. First activation from Shell

Use the English keyboard. Replace any old text in the input box with exactly:

```sh
sh /sdcard/Download/runroot.sh
```

Press the page's **Send / 发送** button once. Wait about 5–10 seconds before checking
the computer. The script redirects its output to a file, so a blank black output
area is not proof of failure. Do not keep sending the same command repeatedly.

The supplied script:

1. Records the caller identity and source payload hash.
2. Copies the payload to `/data/data/com.dpad.fuli/adbd-r`.
3. Verifies the destination hash and aborts on mismatch.
4. Stops the stock `adbd` service.
5. Sets TCP port `5555` and starts the clean daemon as the assistant's UID 1000.
6. Writes diagnostics to internal Download and schedules the restoration timer
   described below.

The filename `runroot.sh` is historical. Successful execution still gives
**system UID 1000**, not root.

## 5. Confirm ADB on the computer

```powershell
adb devices -l
```

Select the remote's USB serial from the output. Use `-s SERIAL` on every command
if more than one device/transport is present:

```powershell
adb -s SERIAL shell id
# Expected: uid=1000(system) gid=1000(system) ...
adb -s SERIAL shell getprop ro.build.display.id
adb -s SERIAL shell getprop ro.build.fingerprint
adb -s SERIAL pull /sdcard/Download/runroot-out.txt .
adb -s SERIAL pull /sdcard/Download/adbd-r.log .
```

The USB transport should now show `device`. Wi-Fi is optional for computer-side
work. If desired, connect from the same trusted LAN using the remote's current IP:

```powershell
adb connect 192.168.1.92:5555  # example only: replace with the remote's current IP
adb -s 192.168.1.92:5555 shell id
```

The daemon also listens on TCP 5555 without RSA authentication in this setup.
Keep it on a trusted network. The on-device tile examples use that same daemon
through `127.0.0.1:5555`, even when the computer uses USB.

To reduce idle power-off while plugged in:

```powershell
adb -s SERIAL shell svc power stayon true
```

This does not disable the script's restoration timer, and does not guarantee that
every DJI power-management condition is bypassed.

## Activation lifetime

The repository's existing `runroot.sh` schedules an automatic return to stock
ADB after **1,800 seconds**. The timer kills the clean daemon, clears its TCP-port
property and starts stock adbd. A later `restart.sh` invocation does **not** cancel
an older timer. A disconnect around 30 minutes can therefore be expected even
while the screen is awake. Preserve this distinction when diagnosing disconnects.

`restart.sh` starts the already installed daemon without creating a new timer.
After the initial timer has fired, it can reactivate the existing payload. After
a reboot, the old background timer is gone; use `restart.sh` once for that boot.
This guide does not install automatic boot activation.

## 6. After a reboot

Open the assistant and Shell page again, then send:

```sh
sh /sdcard/Download/restart.sh
```

Wait, run `adb devices -l`, and verify `id` again. The payload under the assistant's
private directory must still exist. If it does not, repeat first-time setup using
the verified payload instead of repeatedly running `restart.sh`.

Diagnostics:

```powershell
adb -s SERIAL pull /sdcard/Download/restart-out.txt .
adb -s SERIAL shell pidof adbd-r
adb -s SERIAL shell ss -ltn
```

If ADB cannot connect, read `runroot-out.txt` or `restart-out.txt` through MTP from
internal Download. Check for `HASH_MISMATCH_ABORT`, a missing source file, or an
empty daemon PID. No PC-side `adb shell` command can help until the connection is
working. Developer-mode toggles and revoking USB authorizations do not by
themselves remove the documented pre-authentication production gate.

## 7. Next steps

- [Install and launch ARM64 apps](64-BIT-APPS.md).
- [Add application and Shell shortcuts to Quick Settings](QUICK-SETTINGS.md).
- [Troubleshooting](TROUBLESHOOTING.md).
- [What system UID does and does not provide](ROOT-STATUS.md).
