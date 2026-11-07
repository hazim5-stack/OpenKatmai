# Installing and launching 64-bit Android apps

Scope: the recorded RC 2 on **v10.00.0800 (2026.07.30)** (Android build `V14.00.00.04`), with the working **UID 1000** ADB
connection from [First-time setup](SETUP.md). The successful real-device test
established that an ARM64 Android application can be installed and launched
through ActivityManager. It does not establish compatibility with every APK.

## 1. Understand the two independent requirements

The hardware, kernel, linker64 and 64-bit ART are available. `system_server` and
many preinstalled apps run as 32-bit processes. The default policy stops the
primary 64-bit zygote after boot while leaving `zygote_secondary` running.

There are two separate issues:

1. **Installation ABI:** DJI's default install path can select `armeabi-v7a` even
   for an ARM64-only package. Specify `--abi arm64-v8a` and check the result.
2. **Launch runtime:** the primary `zygote` and its socket must be available when
   Android creates a new 64-bit app process.

Starting zygote does not install an APK. Selecting an ABI does not start zygote.
This is native 64-bit execution, not emulation and not a 32-to-64-bit conversion.

## 2. Inspect your APK and discover its component

Use `aapt` from your installed Android SDK build-tools:

```powershell
aapt dump badging .\Example64.apk
```

Look for the actual `package: name=`, `launchable-activity: name=`, `sdkVersion`,
and `native-code: 'arm64-v8a'` entries. The package/component names below are
**placeholders**; replace them with your APK's real values:

```text
Package:   com.example.arm64app
Component: com.example.arm64app/com.example.arm64app.MainActivity
```

An APK without native libraries can still run as 32-bit on this firmware; an ABI
override alone is not proof that its process is 64-bit. An x86_64-only APK is not
an ARM64 APK. Split APK/APKS/XAPK bundles may require their complete matching set;
this single-APK recipe does not reconstruct missing splits or libraries.

## 3. Install with an explicit ABI

```powershell
adb -s SERIAL install --abi arm64-v8a -r .\Example64.apk
adb -s SERIAL shell pm path com.example.arm64app
adb -s SERIAL shell dumpsys package com.example.arm64app
adb -s SERIAL shell cmd package resolve-activity --brief com.example.arm64app
```

Require `Success`, confirm the package exists, and inspect
`primaryCpuAbi=arm64-v8a`. Use the resolved launcher activity, which may differ
from a class guessed from the app's display name. `-r` requests an update; it
cannot bypass signature mismatches or missing split APKs. Do not uninstall an
existing package with valuable data simply to hide an installation error.

Earlier small test APKs failed with `Error deriving application ABI`, including
with an explicit ABI. A real ARM64 APK subsequently succeeded. Thus that error
alone is not evidence that PackageManager can never install ARM64 software.

## 4. Record the baseline before changing runtime state

```powershell
adb -s SERIAL shell id
adb -s SERIAL shell getprop init.svc.zygote
adb -s SERIAL shell getprop init.svc.zygote_secondary
adb -s SERIAL shell getprop persist.dji.zygote_policy
adb -s SERIAL shell ls -l /dev/socket/zygote
```

The recorded baseline was UID `1000`, primary `stopped`, secondary `running`,
policy `4`, and no primary socket. The helper below refuses unexpected primary
states and a stopped secondary. Do not stop the running secondary to make a
64-bit application work.

## 5. Install the generic per-launch helper

[`scripts/launch64.sh`](../scripts/launch64.sh) takes exactly one activity component.
It checks the caller, package installation, ABI and secondary runtime; obtains a
lock; starts primary zygote only if needed; waits for its socket; launches the
component; checks the foreground package; and restores the previous primary
state. A bounded fallback attempts restoration if its main invocation dies.

```powershell
adb -s SERIAL push .\scripts\launch64.sh /data/data/com.dpad.fuli/launch64.sh
adb -s SERIAL shell chmod 700 /data/data/com.dpad.fuli/launch64.sh
Get-FileHash .\scripts\launch64.sh -Algorithm SHA256
adb -s SERIAL shell sha256sum /data/data/com.dpad.fuli/launch64.sh
```

Compare both hashes. Keep the executable helper in the assistant's private
directory, owned by UID 1000, rather than executing a mutable shared-storage
helper from an app tile. The assistant/data path is specific to this setup.

Launch your installed ARM64 application:

```powershell
adb -s SERIAL shell sh /data/data/com.dpad.fuli/launch64.sh com.example.arm64app/com.example.arm64app.MainActivity
```

Expected markers on success:

```text
ATTEMPT=1
APP_READY
RESTORE_DONE
```

The helper allows a second launch attempt because a bounded live test first
encountered `Broken pipe` and a subsequent attempt succeeded. Stale connection
state is a hypothesis, not a proved diagnosis. The script does not restart
`system_server` or either running secondary runtime to address it.

Afterward confirm:

```powershell
adb -s SERIAL shell getprop init.svc.zygote
adb -s SERIAL shell getprop init.svc.zygote_secondary
adb -s SERIAL shell getprop persist.dji.zygote_policy
adb -s SERIAL shell dumpsys activity activities
```

If primary was stopped before the launch, expect it to be stopped again;
secondary should remain running and policy should retain its original value.
`RESTORE_FAILED` or a missing `RESTORE_DONE` requires checking the state, not
assuming cleanup succeeded. The fallback is best effort if the device itself
shuts down or the privileged daemon disappears.

## 6. Put preparation before every tile launch

Use the ARM64 tile in [the Quick Settings example](QUICK-SETTINGS.md). Configure
its `ARM64_COMPONENT` to the real component above. Each tap runs the fixed local
ADB helper **before** opening the configured app; it does not first try to launch
the ARM64 app through an unavailable runtime. Ordinary launcher/Fly tiles remain
plain launch shortcuts and do not need this preparation.

The pipeline is:

```text
Tap ARM64 tile
  → connect to local system ADB at 127.0.0.1:5555
  → verify UID 1000
  → run launch64.sh with the configured component
  → verify installed ARM64 package and current runtime state
  → temporarily start primary zygote, if needed
  → launch app and check the foreground package
  → restore the prior primary state and release the lock
```

After reboot, reactivate ADB from the assistant first. This tile is not an ADB
bootstrap or a persistent boot modification. A computer does not need to remain
connected once the local daemon is running.

## 7. Limits of the result

- The tested app remained alive when primary zygote was stopped after launch.
  A later 64-bit secondary process or a process restart may need primary zygote
  again. This bounded launch method does not promise uninterrupted background
  multiprocess operation for all applications.
- `APP_READY` means the package became the foreground activity; it does not prove
  initialization of every SDK, service, permission, account, hardware connection
  or native library. Check the app's real UI and relevant logs next.
- Grant only permissions that the application's actual flow needs. An app that
  needs unavailable Google services, incompatible hardware or another Android
  API level is not fixed merely by selecting ARM64.
- The generic helper/template is a reusable adaptation of the observed method;
  installing and launching every possible target APK has not been tested.
- No persistent zygote policy change is required by this workflow. The verified
  primary init definition includes `--start-system-server`, so do not assume
  permanent primary activation is harmless on a different build.

See [Findings](FINDINGS.md#6-64-bit-app-support) for evidence and the retracted
earlier interpretation, and [Troubleshooting](TROUBLESHOOTING.md) for failure
markers and recovery.
