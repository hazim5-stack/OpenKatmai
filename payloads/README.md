# Payloads

**No binaries are committed to this repository.** Fetch them from their original
sources and verify the SHA-256 against [`../SHA256SUMS.txt`](../SHA256SUMS.txt)
before placing them on a device.

## `adbd-root` (required)

Clean AOSP `adbd` (aarch64, Android 11), free of the DJI production gate,
userlock and custom auth socket.

- Source: [rc2-unlock-toolkit](https://github.com/456erddgr/rc2-unlock-toolkit)
  → `app/assets/rc331-root-adb/adbd-root`
- SHA-256: `f45e7917c094e6905557057b8f2af516c658458e675c23eb00de7fe34cb6b3ab`
- Size: 1,497,232 bytes

PowerShell verification:

```powershell
(Get-FileHash .\adbd-root -Algorithm SHA256).Hash -eq `
  'F45E7917C094E6905557057B8F2AF516C658458E675C23EB00DE7FE34CB6B3AB'
# must print: True
```

## DJI binaries (never fetch from here, never commit)

The compatible factory-assistant APK (`com.dpad.fuli`) is also not distributed
here. See [First-time setup](../docs/SETUP.md) for the locally inspected version,
hash and the owner's Storage → Audio → Download installation route. A renamed
third-party APK is not a replacement for the original signed assistant.

The stock `adbd` and the single-instruction CNXN-gate-bypass derivative are
DJI proprietary / derivative works. They are referenced by hash only for
reproducibility and must not be redistributed.
