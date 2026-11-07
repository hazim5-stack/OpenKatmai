# Findings — DJI RC 2 (rc331) on firmware V14.00.00.04

Everything below was observed live on one retail unit and cross-checked against
the exact signed firmware binaries where noted.

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

## 2. Architecture notes (V14.00.00.04)

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

## 5. MTP quirk worth knowing

The RC 2 enumerates as `2ca3:1021` with three interfaces:

| Interface | Class/Sub/Proto | Role |
|---|---|---|
| 0 | `ff/43/01` | DJI vendor (DUML) |
| 1 | `06/01/01` | MTP/PTP |
| 2 | `ff/42/01` | ADB-shaped FunctionFS |

Files can be staged to `Download/` over plain MTP — no special drivers needed
on Windows 10/11.
