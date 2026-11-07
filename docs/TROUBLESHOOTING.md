# Troubleshooting

Start with [First-time setup](SETUP.md) for the Storage/Audio → assistant → Shell
route, [64-bit apps](64-BIT-APPS.md) for ABI/runtime preparation, and
[Quick Settings](QUICK-SETTINGS.md) for notification-panel shortcuts.

## Shell has a blank black output area

`runroot.sh` and `restart.sh` redirect output into internal Download, respectively
`runroot-out.txt` and `restart-out.txt`. Send the command once, wait, and check
`adb devices -l`. If ADB is still unavailable, read the text file over MTP. A
blank Shell view is not proof that nothing ran. `/sdcard/Download` here is an
alias of internal shared storage, not a requirement for a removable SD card.

## Assistant APK is gray in Storage → Audio

This was the owner's route into the file browser. Some picker states filter
non-audio files. Confirm the APK is in internal Download and use an available
unfiltered browser/install surface or an existing assistant icon. Do not change
the APK extension to pretend it is audio. A picker filter and a package-install
permission/signature rejection are different problems.

## ADB disconnects around 30 minutes after first activation

The original `runroot.sh` has a 1,800-second auto-restore timer. It stops the
clean daemon and returns stock ADB independently of screen sleep. `restart.sh`
does not create a new timer, but cannot cancel an older scheduled timer. See
[Activation lifetime](SETUP.md#activation-lifetime) before blaming USB hardware.

## Device vanishes (USB + network at once)

**Cause:** the RC 2's auto power-off ("تم الإطفاء" toast), not a crash.
Check `getprop persist.sys.boot.reason.history` — you will see `shutdown` entries.

**Fix:** `adb shell "svc power stayon true"` and keep the unit on the charger
during long sessions. A reboot restores stock adbd — re-run `restart.sh`.

## `adb devices` shows `offline`

One possible cause is stock adbd returning after a reboot or the first-activation
timer, with the production gate active. Check the correct device/cable/transport.
Re-run from the on-device shell page:

```sh
sh /sdcard/Download/restart.sh
```

then `adb connect <RC2-IP>:5555`.

## `connect ... actively refused (10061)`

Nothing listens on 5555: the clean adbd died or was never started. Same fix as
above. If the RC 2 changed Wi-Fi networks, find the new IP
(`adb shell ip -4 addr show wlan0` via USB, or check your router).

## `unauthorized`

The contacted daemon requires authentication; it may be another device or a
different ADB service. The documented clean-daemon setup used `ro.adb.secure=0`
and did not require an RSA prompt. Check the selected serial/IP and the daemon's
on-device log. Once a working transport exists, `adb shell "pidof adbd-r"` should
return a PID. Do not use an unavailable shell as the only diagnostic step.

## `settings put` throws `NullPointerException`

The clean adbd runs as `uid=1000(system)` with no package identity, and
`SettingsProvider` crashes on `getCallingPackage`. Use the service wrappers
instead, which execute inside system_server:

```sh
svc power stayon true        # instead of: settings put global stay_on_while_plugged_in 7
```

## Git Bash mangles `/data/...` into `C:/Program Files/Git/...`

MSYS path conversion. Either:

```sh
export MSYS_NO_PATHCONV=1
```

or use PowerShell for adb commands.

## `dlopen failed: "...so" is 64-bit instead of 32-bit` (agent work)

`system_server` on V14 is 32-bit. Compile agents with
`armv7a-linux-androideabi30-clang`, not `aarch64`.

## `... is not accessible for the namespace "com_android_art"`

The linker namespace wall documented in
[ROOT-STATUS.md](ROOT-STATUS.md#the-blocker-linker-namespace). There is no
writable permitted path on V14 — the agent cannot be loaded into
system_server, regardless of architecture.

## Device in fastboot mode after a DJI Fly update

Documented behaviour of Fly updates on modified runtime environments — not a
brick. `fastboot devices` should still see the unit; `fastboot continue`
boots it normally. Avoid Fly updates while experimenting.

## ARM64 installation fails

Inspect the APK's actual native libraries and use:

```powershell
adb -s SERIAL install --abi arm64-v8a -r .\Example64.apk
```

Verify the installed package's `primaryCpuAbi`. `INSTALL_FAILED_NO_MATCHING_ABIS`
or error -113 can result from the forced default ARMv7 path. `Error deriving
application ABI` also occurred with earlier minimal APK fixtures, but a real
ARM64 application later installed successfully. Missing splits, malformed native
libraries, signature mismatches and minimum Android version are separate causes.

## ARM64 app installs but cannot open / missing zygote socket

Read `init.svc.zygote`, `init.svc.zygote_secondary`, and the primary socket. A
stopped primary with a running secondary was the recorded default. Use the
bounded [launch helper](64-BIT-APPS.md); do not stop the secondary or change
persistent policy as a generic fix. A first `Broken pipe` followed by successful
retry was observed, but its underlying cause is unproved.

## `launch64.sh` failure markers

- `WRONG_UID`: activation did not provide the required UID 1000 system shell.
- `APP_NOT_INSTALLED`: wrong package or installation did not complete.
- `WRONG_APP_ABI`: installed package is not marked `arm64-v8a`; inspect it first.
- `SECONDARY_NOT_RUNNING` / `UNEXPECTED_PRIMARY_STATE`: baseline differs; stop
  and inspect the build/runtime rather than forcing services blindly.
- `PREPARATION_BUSY`: another helper holds the global lock. Wait for completion
  or its 45-second fallback. If a stale lock remains, inspect processes/state
  first; remove only the empty `.openkatmai-launch64-lock` directory with `rmdir`
  after establishing that no launch is still active. Do not recursively delete
  the assistant's data directory.
- `ZYGOTE_NOT_READY`: the primary service/socket did not become ready in time.
- `APP_START_FAILED`: neither bounded attempt confirmed the foreground package.
  Inspect the `am` output and app crash logs; this is not an automatic ABI verdict.
- `RESTORE_FAILED`: inspect the primary state immediately. If it was stopped
  before this launch, restore it using `setprop ctl.stop zygote` from the working
  system shell and verify the result. Leave secondary untouched. If the previous
  primary state is unknown, do not guess which running service to stop.

## Tile is visible but the target app does not open

For a normal app, check the manifest package query, Config package, installed
launcher component and runtime exception. For an ARM64 tile, also check local
TCP 5555, UID 1000, the protected helper, target ABI and `OpenKatmaiTiles` log.
The wrapper does not bootstrap ADB after reboot. A tile's lit state is not a
health check of the target's SDK or hardware.

For Shell, a local-ADB failure falls back to the assistant's main menu. A direct
non-exported Shell activity cannot be opened by an ordinary wrapper UID alone.
If an older assistant page covers the Shell screen, use the bridge's separate
task and clear-top behavior from the included source. Validate a second tap too.

## Command-line uninstall throws an AppOps/package identity exception

Use the normal app-settings uninstall confirmation on the remote, then verify
the package is gone. This setup's UID 1000 shell lacks a normal package identity;
a command-line failure is not proof that uninstall is forbidden in the UI.
