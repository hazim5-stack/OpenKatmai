# Findings — DJI RC 2 (rc331) on firmware v10.00.0800 (2026.07.30)

Everything below was observed live on one retail unit and cross-checked against
the exact signed firmware binaries where noted.

## Firmware identity

Live readback from the connected RC 2 on 2026-11-08 returned:

```text
dji.prop.external_version = 10.00.08.00
ro.build.id = V14.00.00.04
ro.build.display.id = V14.00.00.04 test-keys
```

The project targets external firmware **v10.00.0800 (2026.07.30)**.
`V14.00.00.04` is the internal Android build ID of this tested device, not
the public firmware version. Earlier documentation used that build ID as
firmware shorthand; this section corrects the naming. Other firmware releases
require separate compatibility verification.

## 1. The three DJI guards in stock `adbd`

### 1.1 Production gate (pre-AUTH) — the reason for `offline`

The APEX `adbd` (`/apex/com.android.adbd/bin/adbd`, SHA-256
`b300d9bb…5422`, Build ID `c30245f84b2d2ddcecbcd9f640a84192`) evaluates, inside
`handle_packet(CNXN)`:

```text
ro.boot.mp_state == "production" && ro.boot.dbg_cnt < 1
```

When true it takes a DJI early-return path and never calls
`send_auth_request()` / `send_connect()`. The host therefore never receives
`AUTH TOKEN` and the transport stays `offline` forever — on USB **and** on TCP.

Live confirmation on this unit:

```text
ro.boot.mp_state = production
ro.boot.dbg_cnt    = (empty)      -> gate active
```

A direct-libusb client sending a standards-compliant `CNXN` (version
0x01000000, MAXDATA 256 KiB, split header/payload transfers) received **zero
bytes** back across repeated 15–30 s windows. Every single-variable variant
(version field, MAXDATA, banner, checksum) failed identically — the daemon
simply never answers.

### 1.2 userlock

Running a gate-patched copy of the same binary manually dies immediately:

```text
adbd-p E auth.cpp:229] Failed to get adbd socket: No such file or directory
adbd-p E auth.cpp:79]  adb get userlock : locked
```

So even with the CNXN gate removed, DJI's auth layer refuses to run outside
the official init environment.

### 1.3 The auth socket

`Failed to get adbd socket` — the daemon expects a socket created by init's
service environment. It does not exist for manually started processes.

### Conclusion

Patching DJI's binary is a dead end at runtime. The working approach is a
**clean AOSP adbd** (`adbd-root`, same AOSP base, aarch64) which contains none
of the three guards. Because `ro.adb.secure=0`, the connection completes with
no RSA authorization at all.

## 2. Architecture notes (v10.00.0800; Android build V14.00.00.04)

| Component | Architecture | Evidence |
|---|---|---|
| Linux kernel | aarch64 (`5.4.210-qgki`) | `uname` |
| `adbd` | 64-bit (interpreter `/system/bin/linker64`) | ELF header |
| `/system/bin/sh` | 64-bit | ELF `EI_CLASS=2` |
| `system_server` | **32-bit** | `dlopen failed: "...libagent64.so" is 64-bit instead of 32-bit`; classloader shows `/system/lib` |
| Apps (DJI Fly, KISS) | 32-bit ARMv7 | installed splits |

`ro.zygote=zygote64_32` yet system_server is 32-bit — trust the loader error,
not the property.

## 3. Capabilities of the working shell

`adbd-root` started by `uid=1000(system)` yields:

```text
uid=1000(system) gid=1000(system)
groups=1000(system),1007(log),1065(reserved_disk),1077(external_storage),
       3001(net_bt_admin),3002(net_bt),3003(inet),3007(net_bw_acct),9997(everybody)
context=u:r:system_app:s0
CapEff: 0000000000000000
```

i.e. Android `system` powers (package management, settings via `svc`, activity
control, log access) but **zero Linux capabilities** — no root.

## 4. Auto power-off ("تم الإطفاء")

The RC 2 shuts itself down after idle time. Symptoms look exactly like an
"app killer" or a crash: MTP drops, TCP refuses, USB device disappears.
Boot history confirms it:

```text
persist.sys.boot.reason.history: shutdown,,8 / shutdown,,7 ...
ro.boot.bootreason: shutdown,
```

Mitigation while plugged in: `svc power stayon true`
(`stay_on_while_plugged_in = 7`).

Note: `settings put` fails with an NPE from this shell (the adbd runs as
uid 1000 without a package name, `ContentProvider.getCallingPackage` throws).
Use `svc …` / `cmd …` / `am …` alternatives instead.

## 6. 64-bit app support

### 2026-11-08 correction from a real ARM64 application

The earlier conclusion that this firmware cannot install or launch any ARM64 Android app is
**RETRACTED**. It was broader than the minimal-test results below justify.

A real application containing 52 ARM64 native libraries installed successfully
with explicit `--abi arm64-v8a`. Package readback confirmed
`primaryCpuAbi=arm64-v8a` and `secondaryCpuAbi=null`. This contradicts a blanket
PackageManager ARM32-only rule. Its private application identity is intentionally
omitted; examples in this repository use generic package/component placeholders.

A bounded live test started primary `zygote` while leaving the running secondary
zygote and `persist.dji.zygote_policy=4` unchanged. The first test failed with
`ZygoteStartFailedEx: IOException: Broken pipe`. A second bounded test with the
same application and primary zygote running produced an ActivityManager-managed
COLD launch in about 1.94 seconds. A repeated start reached the existing instance.
Foreground activity readback and an actual screen capture confirmed the app UI.
The earlier broken pipe is consistent with stale connection state, but its cause
has not been proved.

The test restored primary zygote to `stopped`, secondary remained `running`, and
policy remained `4`; separate readback confirmed these values. The app UI remained
visible after restoration. Hardware connectivity, all SDK features, background
multiprocess behavior and reboot persistence were not established. No framework,
firmware, boot configuration, or persistent policy was changed.

A later on-device Quick Settings tap cold-launched the ARM64 app through a fixed
local-system-ADB helper. The final state was again primary stopped, secondary
running, policy 4 and no helper lock. A repeated helper call reported successful
launch and restoration. Separate Shell tile testing confirmed direct entry into
`com.dpad.fuli/.ShellCommandActivity`, including a second tap. No command was
submitted through the Shell UI by the tile.

The generalized source and instructions are now provided in
[64-bit apps](64-BIT-APPS.md), [Quick Settings](QUICK-SETTINGS.md),
[`launch64.sh`](../scripts/launch64.sh), and
[`src/quick-tiles`](../src/quick-tiles). The generalized template has been compiled
and verified locally; this is not a claim that its placeholder target or every
possible APK was tested on the remote.

### Earlier minimal tests and policy observations (historical)

DJI ships an init policy in `/system/etc/init/hw/init.rc`:

```text
on property:sys.boot_completed=1 && property:persist.dji.zygote_policy=2
    stop zygote_secondary        # 64-bit-only mode
on property:sys.boot_completed=1 && property:persist.dji.zygote_policy=4
    stop zygote                  # 32-bit-only mode (shipped default)
```

Both zygotes start at boot; the trigger then stops one of them. Any policy
value other than `2`/`4` matches no trigger and would leave both running.

Live results on this unit:

- `setprop ctl.start zygote` starts the 64-bit zygote cleanly
  (`init.svc.zygote=running`, `/dev/socket/zygote` appears, system stable,
  only ~14 MB extra RSS observed) — providing evidence that the **kernel,
  linker64 and 64-bit ART can execute the tested code** (a direct `app_process64` Java test also returns
  `is64Bit=true`).
- Earlier minimal APK tests produced the following results. Their interpretation
  as proof that PackageManager is universally 32-bit-only is retracted; the
  fixture construction and differences from the real APK remain to be audited:

  | APK variant | `pm install` | result |
  |---|---|---|
  | `lib/arm64-v8a/libdummy.so` | `INSTALL_FAILED_INTERNAL_ERROR: Error deriving application ABI` | rejected |
  | same + `--abi arm64-v8a` override | same error | rejected |
  | `lib/armeabi-v7a/libdummy.so` | Success | runs, `os.arch=armv8l` |
  | no native libs at all | Success | **`primaryCpuAbi=armeabi-v7a`**, runs 32-bit |

- `persist.dji.zygote_policy` (tested `0` and `4`) did not change those minimal
  fixture outcomes. That does not prove a universal compiled-in ABI prohibition.

**Current conclusion:** both direct 64-bit Java execution and an AMS-managed
ARM64 application UI launch have been observed on this firmware. Installation
requires the correct ABI selection; process creation requires a running primary
zygote. A framework modification has not been shown necessary. The documented
per-launch workflow is bounded and does not establish universal app, service or
hardware compatibility.

## 5. MTP quirk worth knowing

The RC 2 enumerates as `2ca3:1021` with three interfaces:

| Interface | Class/Sub/Proto | Role |
|---|---|---|
| 0 | `ff/43/01` | DJI vendor (DUML) |
| 1 | `06/01/01` | MTP/PTP |
| 2 | `ff/42/01` | ADB-shaped FunctionFS |

Files can be staged to `Download/` over plain MTP — no special drivers needed
on Windows 10/11.
