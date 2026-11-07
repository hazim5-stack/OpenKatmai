# Troubleshooting

## Device vanishes (USB + network at once)

**Cause:** the RC 2's auto power-off ("تم الإطفاء" toast), not a crash.
Check `getprop persist.sys.boot.reason.history` — you will see `shutdown` entries.

**Fix:** `adb shell "svc power stayon true"` and keep the unit on the charger
during long sessions. A reboot restores stock adbd — re-run `restart.sh`.

## `adb devices` shows `offline`

Stock adbd is answering again (after a reboot) with the production gate active.
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

You are talking to the stock adbd (or another device). Verify with
`adb shell "pidof adbd-r"` — it must return a PID. Disconnect other devices.

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
