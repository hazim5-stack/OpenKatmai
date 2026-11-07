# Root (uid=0) research status on V14.00.00.04

Current capability: `uid=1000(system)` shell via clean AOSP adbd.
This file records, honestly, what was tried for uid=0, what worked, and the
exact remaining blocker.

## What works

- Stock APEX `adbd` exported and hash-matched to the documented v07 payload
  (`b300d9bb…5422`) — the same binary ships in V14.
- Single-instruction CNXN-gate patch reproduced byte-identical to the
  independently audited artifact (`3fceaa17…225f`) via `patch_cnxn_gate.py`
  (semantic patcher, refuses anything but the exact instruction at
  file offset `0x90460`: `cset w21, lt` → `mov w21, wzr`).
- Runtime proof that the gate patch alone is insufficient (userlock/socket),
  which is why the clean adbd is used instead.
- `am attach-agent` is **accepted** by ActivityManager (exit 0) and ART
  attempts the load — the injection channel itself is alive on V14.

## The blocker: linker namespace

`system_server` on V14 is a **32-bit** process whose agent loads execute in the
`com_android_art` linker namespace. From `/linkerconfig/ld.config.txt`:

```text
namespace.com_android_art.isolated        = true
namespace.com_android_art.search.paths    = /apex/com.android.art/${LIB}
namespace.com_android_art.permitted.paths = /system/${LIB}
namespace.com_android_art.permitted.paths += /apex/com.android.art/javalib
```

Both permitted roots are dm-verity read-only partitions. Any agent stored
under `/data/...` is rejected at `dlopen`:

```text
Agent attach failed (result=1): Unable to dlopen /data/data/com.dpad.fuli/libagent64.so:
dlopen failed: "...libagent64.so" is 64-bit instead of 32-bit        # arch lesson first
... needed or dlopened by ".../libnativeloader.so" is not accessible
for the namespace "com_android_art"                                   # the real wall
```

On the older V00.01.12.50 firmware this attack surface was open (that is how
rc2-unlock-toolkit bootstraps). On V14 it is closed for `system_server`.
App processes (e.g. DJI Fly) do accept agents from their own `apk_data_file`
directories — but apps run with an empty capability set, so they are useless
as a privilege bridge.

## Custom agent (src/agent.c)

A minimal ART agent is provided for completeness: on `Agent_OnAttach` it
`fork()`s, raises every capability into the **ambient** set
(`prctl(PR_CAP_AMBIENT, PR_CAP_AMBIENT_RAISE, …)`) and `execl`s a script, so
the child would survive `execve` with full capabilities — if the library could
be loaded at all. Build for **32-bit**:

```sh
$ANDROID_NDK/toolchains/llvm/prebuilt/<host>/bin/armv7a-linux-androideabi30-clang \
    -shared -fPIC -O2 -o libagent.so agent.c
```

It currently has no loadable path on V14 (see above). It is kept as reference
material in case a writable, namespace-permitted path is ever found
(e.g. a writable APEX overlay, or a future firmware with looser ld.config).

## Ruled out on V14

| Route | Result |
|---|---|
| `djilink` Binder command injection | service **removed** in V14 (`Service djilink: not found`) |
| Factory/`atfwd`/Qualcomm diag services | none present in `service list` |
| setuid binaries on stock partitions | none found |
| `/dji_apk` write as uid 1000 | `root:root drwxrwxr-x` — denied |
| JDWP attach to system_server | no jdwp endpoints visible from this shell |
| USB debugging toggle / wireless adb / tcpip 5555 | cannot bypass the pre-AUTH gate (documented) |
| First-packet `AUTH RSAPUBLICKEY` | not attempted; state-changing, non-default hypothesis |

## Remaining theoretical paths (not pursued here)

1. **Bootloader unlock + Magisk-patched boot.** Reliable, documented elsewhere,
   but `fastboot flashing unlock` **burns an eFuse tamper bit permanently** and
   is linked to DJI Fly failing its integrity check (error 15). Declined.
2. installd/filesystem race or symlink exploitation for root-executed writes —
   exploit research, fragile and out of scope.
3. A legitimate writable path inside a permitted namespace root (none found).

## Safety properties maintained throughout

- No partition writes, no `fastboot flash`, no eFuse, no bootloader change.
- system_server crashes during agent experiments only trigger the normal
  Android runtime restart — the device recovers by itself (observed twice).
- The initial activation script verifies the installed daemon hash and aborts
  on mismatch. The existing restart script reuses that file and does not perform
  a fresh hash check; verify the installed payload again if its integrity is in
  doubt. The generic launch helper's deployment guide also requires comparing
  host/device hashes.
