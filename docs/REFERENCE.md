# Technical reference

[Project overview](../README.md) · [First-time setup](SETUP.md) · [ARM64 apps](64-BIT-APPS.md) · [Quick Settings](QUICK-SETTINGS.md)

**Working ADB on the DJI RC 2 (KATMAI-IDP / rc331) running firmware v10.00.0800 (2026.07.30)** —
no bootloader unlock, no eFuse, no partition writes, fully reversible.

```text
adb connect <RC2-IP>:5555
adb shell id
# uid=1000(system) gid=1000(system) ... context=u:r:system_app:s0
```

| | Stock state | With OpenKatmai |
|---|---|---|
| `adb devices` | `offline` forever | ✅ `device` (USB **and** TCP) |
| `adb shell` | never connects | ✅ `uid=1000(system)` |
| Install APKs | ❌ | ✅ `adb install` |
| System partition | untouched | ✅ untouched (everything lives in `/data`) |
| Bootloader / eFuse | locked / intact | ✅ stays that way |

> ⚠️ This gives a **system-uid shell, not uid=0 root**. Root research status is
> documented honestly in [docs/ROOT-STATUS.md](ROOT-STATUS.md).

---

## 1. Supported devices

| Item | Verified value |
|---|---|
| Device | DJI RC 2 (screen remote), codename **KATMAI-IDP**, board `rc331` |
| SoC | Qualcomm QCS5430 (lahaina) |
| Firmware | **v10.00.0800 (2026.07.30)** |
| Android build | **V14.00.00.04** — `DJI/rc331/rc331:11/V14.00.00.04/4:user/test-keys` |
| Android | 11, test-keys build, **SELinux Permissive** |
| Key properties | `ro.boot.mp_state=production`, `ro.boot.dbg_cnt=` (empty), `ro.adb.secure=0`, `ro.debuggable=1` |

Older firmware (e.g. V00.01.12.50) has different weaknesses and is covered by other
projects — see [Credits](#8-credits).

## 2. Why stock ADB shows "offline"

DJI's `adbd` carries extra guards that stock ADB cannot pass on this firmware:

1. **Production gate (pre-AUTH).** In `handle_packet(CNXN)` the daemon checks
   `ro.boot.mp_state == "production" && ro.boot.dbg_cnt < 1`. When true it returns
   early — *before* sending `AUTH TOKEN`. The host waits forever: `offline`.
   (Independently verified by static analysis of the exact signed APEX binary,
   SHA-256 `b300d9bb…5422`.)
2. **userlock.** Starting that same binary manually dies with
   `adb get userlock : locked` plus `Failed to get adbd socket`.
3. **Auth socket.** The DJI auth flow expects an init-created socket that only
   exists in the official service environment.

**OpenKatmai does not patch DJI's adbd.** It runs a clean **AOSP `adbd`**
(`adbd-root`, aarch64) that contains none of these guards — because
`ro.adb.secure=0`, the handshake completes immediately, no RSA prompt.

## 3. Quick start

For the complete first-time sequence, including **Storage → Audio → Download →
factory-assistant APK**, see [First-time setup](SETUP.md). For ARM64 apps and
notification-panel shortcuts, continue with [64-bit apps](64-BIT-APPS.md) and
[Quick Settings tiles](QUICK-SETTINGS.md).

### Prerequisites (Windows PC)
- USB-C **data** cable, RC 2 in file-transfer (MTP) mode
- [platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`)
- Stop any running adb server: `adb kill-server`

### Step 1 — get the payload
Download `adbd-root` (clean AOSP adbd, aarch64) from the
[rc2-unlock-toolkit](https://github.com/456erddgr/rc2-unlock-toolkit) repository
(`app/assets/rc331-root-adb/adbd-root`) and **verify it**:

```powershell
(Get-FileHash .\adbd-root -Algorithm SHA256).Hash
# must equal F45E7917C094E6905557057B8F2AF516C658458E675C23EB00DE7FE34CB6B3AB
```

### Step 2 — stage files on the RC 2
Copy over MTP into `Download` on the RC 2's internal storage:
- `adbd-root`
- `scripts/runroot.sh`
- `scripts/restart.sh` (keep this for later boots)
- Your original, compatible DJI factory-assistant APK, if its icon is unavailable

### Step 3 — run once on the RC 2
Open the factory **开发助手 (Dev Assistant / com.dpad.fuli)** → **shell命令测试**
(shell test page) and execute:

```sh
sh /sdcard/Download/runroot.sh
```

The script verifies the payload hash (aborts on mismatch), installs it to
`/data/data/com.dpad.fuli/adbd-r`, stops the stock adbd, opens TCP port 5555 and
starts the clean adbd in the background.

The existing `runroot.sh` also schedules a return to stock ADB after **1,800
seconds (30 minutes)**. This is independent of screen sleep. `restart.sh` has no
such timer, but running it does not cancel an already scheduled timer from
`runroot.sh`. See [First-time setup](SETUP.md#activation-lifetime).

### Step 4 — connect from the PC

```powershell
adb connect <RC2-IP>:5555
adb devices          # -> device  (USB shows "device" too)
adb shell id         # -> uid=1000(system)
```

### Step 5 — stop the auto power-off (important)
The RC 2 powers itself off when idle (toast "تم الإطفاء"), killing your session:

```powershell
adb shell "svc power stayon true"     # stay awake while plugged in
```

## 4. After every reboot — do I need the on-device shell?

| Situation | Shell page needed? |
|---|---|
| First activation | ✅ one line: `sh /sdcard/Download/runroot.sh` |
| After **every** RC 2 reboot | ✅ one line: `sh /sdcard/Download/restart.sh` |
| Everything else (install apps, files, launcher, logcat) | ❌ all from PC |
| If full root is achieved one day | ❌ never again (persistence possible) |

No automatic boot activation has been established by this workflow. Restart the
daemon manually once per boot; an ordinary launcher or tile does not acquire the
factory assistant's system UID. The script stays in internal `Download`; execute
the line above from the assistant's Shell page.

## 5. Everyday commands

```powershell
adb install -r app.apk                     # ordinary/default installation
adb install --abi arm64-v8a -r app64.apk    # explicitly select ARM64 when the APK provides it
adb push file /data/data/com.dpad.fuli/
adb pull /sdcard/Download/report.txt .
adb shell "am start -n fr.neamar.kiss/.MainActivity"
adb shell "logcat -v brief"
```

Installing an ARM64 APK and starting its process are separate steps. On the
verified firmware the primary 64-bit zygote is normally stopped. Follow
[64-bit apps](64-BIT-APPS.md) before launching an ARM64 package. Neither an
ABI override nor a tile can convert 32-bit native libraries into 64-bit ones.

Optional: set a custom launcher as Home (KISS example, verified working):

```powershell
adb shell "cmd package set-home-activity fr.neamar.kiss/fr.neamar.kiss.MainActivity"
```

Revert to DJI Fly as Home:

```powershell
adb shell "cmd package set-home-activity dji.go.v5/com.dji.component.application.activity.DJIPureLaunchActivity"
```

## 6. Repository layout

```
OpenKatmai/
├── README.md                 ← this file
├── LICENSE                   ← MIT (original content only)
├── SHA256SUMS.txt            ← payload identities
├── scripts/
│   ├── runroot.sh            ← first activation (hash-guarded, 30-minute restore)
│   ├── restart.sh            ← re-activation after reboot
│   ├── launch64.sh           ← bounded, per-launch ARM64 preparation
│   └── sethome.sh            ← optional custom-launcher helper
├── src/
│   ├── agent.c               ← ART attach-agent source (root research)
│   └── quick-tiles/          ← generic Quick Settings app source + build script
├── docs/
│   ├── FINDINGS.md           ← the three DJI gates, architecture notes
│   ├── ROOT-STATUS.md        ← uid=0 research: what works, what is blocked and why
│   ├── SETUP.md              ← Storage/Audio, assistant, Shell, first activation
│   ├── 64-BIT-APPS.md        ← installation, runtime preparation, generic helper
│   ├── QUICK-SETTINGS.md     ← build, configure, install and test application tiles
│   └── TROUBLESHOOTING.md
└── payloads/
    └── README.md             ← how to fetch + verify binaries (no blobs committed)
```

## 7. Uninstall (back to 100 % stock)

1. While working ADB is still available, remove any custom tiles and APKs you
   installed, and restore your preferred Home app. See the tile guide for removal.
2. Delete unused helper files while this shell is still available:
   ```powershell
   adb shell "rm /data/data/com.dpad.fuli/adbd-r /data/data/com.dpad.fuli/adbd-p"
   ```
   and remove the staged files from `Download`.
3. `adb shell "svc power stayon false"` restores the default sleep behaviour.
4. Reboot the RC 2 — the clean adbd and temporary runtime preparation end; stock
   adbd returns by itself. Do not expect stock ADB to remain usable for cleanup.

No system/vendor/apex partition is ever modified, so there is nothing to "unflash".

## 8. Credits

- **rc2-unlock-toolkit** ([456erddgr](https://github.com/456erddgr/rc2-unlock-toolkit)) —
  source of the `adbd-root` payload and the capability-bridge design used in root research.
- Independent RC 2 research record — static analysis of the exact APEX `adbd`,
  the production-gate disassembly, and the patch tool (`patch_cnxn_gate.py`).
- **dji-adb** ([Dr-Muh](https://github.com/Dr-Muh/dji-adb)) — direct-USB handshake
  experiments that confirmed the pre-AUTH silence on this unit.
- DJI, DJI RC 2 and DJI Fly are trademarks of SZ DJI Technology Co., Ltd.
  This project is independent security research on a privately owned device,
  not affiliated with or endorsed by DJI.

## 9. Warnings

- **Do not update DJI Fly** after modifying the runtime environment (documented risk
  of the device dropping to fastboot after a Fly update).
- **Never re-lock the bootloader** if you ever unlock it — DJI's chain-of-trust
  check bricks the unit on re-lock.
- **Never write to `/dev/block`.** All work here lives in `/data` and `/sdcard`.
- The adb shell runs as `system` — powerful, not root. Treat it with the same care.
