# Toolbox 1.1 validation

Tested on the owner's connected DJI RC 2, **v10.00.0800 (2026.07.30)**,
using the existing UID 1000 ADB daemon. **v10.00.0900 (2026.10.20) has not been tested.**

## Actual remote UI checks

- Selected an APK directly in internal Download. The filename contained spaces
  and an apostrophe. Inspection recovered the correct app label and package.
- Confirmed **Install + add**. Package readback showed the new app installed and
  the Quick Settings list gained one dynamically named app tile.
- Tapped that real SystemUI tile. The test app opened and reported a 32-bit process.
- Used **Remove apps**, including source deletion. The test app, its managed tile
  and the recorded Download APK were removed. Package and tile readback confirmed it.
- Added an existing real ARM64 app to a managed tile and cold-launched it by tapping
  the tile. ActivityManager reported the target foreground activity. The helper
  restored primary zygote to `stopped`, secondary remained `running`, policy remained
  `4`, and its lock was absent afterward. A repeated cold launch also passed. This uses the FlyKML preparation approach.
- The original app already existed, so the cleanup preview explicitly preserved it
  while listing its managed shortcut for removal.
- Installed the test fixture again. Replaced only its staged Download APK with a
  different APK, then requested bulk cleanup including source deletion. The test
  application and managed shortcuts were removed, the changed source was kept,
  the pre-existing ARM64 app remained installed, and Toolbox retained the failed
  journal entry with a retry message.
- Restored the original test-source bytes and retried cleanup with **Also uninstall
  OpenKatmai Toolbox last** selected. Source, managed tiles, dedicated helpers and
  Toolbox were removed. Readback showed no test app or companion package and no
  `openkatmai-toolbox-*` helper files. Existing non-Toolbox panel tiles remained.
- Reinstalled the updated companion for the owner after the cleanup test.

No root escalation, bootloader operation, partition write, persistent zygote-policy
change, flight command or aircraft connection was used for these tests.
The UI launch checks do not establish that every APK, SDK, aircraft connection or
background ARM64 subprocess works.

## Regression fixes exercised during testing

- The UID 1000 shell uninstall command throws a null-caller-package exception on
  this build. A bundled, hash-verified Java helper uses Android's package delete API;
  package absence is checked after ordinary removals.
- Launcher queries handle packages with multiple launcher activities by selecting
  an actual package activity rather than Android's resolver.
- ARM64 preparation includes the runtime-ready delay used by the original FlyKML
  approach. Direct `cmd activity` calls and hard timeout escalation avoid leaving
  a shell wrapper/grandchild stuck. The watchdog checks its lock ownership before
  restoring state so an old watchdog cannot stop a later launch.
- The compatibility task is detached from its ADB connection and reports through a
  temporary result file. Short raw-exec polls retrieve the result and remove that
  file. Binder start commands write directly to the job output rather than a nested
  command-substitution pipe. Raw exec also preserves APK bytes during inspection.

## Host checks

`TransportTest` exercises a synthetic loopback daemon, separate from the live tests:

- UID 1000 accepted; wrong UID rejected.
- Oversized ADB packets rejected and late closes on the previous stream tolerated.
- Raw-exec command selection and byte-exact APK transfer, including NUL, CR/LF
  and non-UTF8 bytes.
- Shell quoting, package-command injection rejection and Download path traversal.
- Cleanup ownership matching: pre-existing apps, changed first-install timestamps,
  replaced signing identities and missing proofs cannot be automatically claimed.
- Protected names include DJI Fly and the factory assistant.

The final APK passes Android signature verification and zip alignment.

## Repeat the disposable-app test

A minimal test app with no network or hardware-control code is included:

```powershell
.\src\toolbox\tests\fixture\build.ps1
adb -s SERIAL push .\src\toolbox\tests\fixture\build\Fixture.apk "/sdcard/Download/OpenKatmai Test App.apk"
```

Use the companion UI to install, open through its tile and remove it. The fixture
package is `org.openkatmai.toolbox.fixture`; it is not a flight application.
Test-only copies were removed after validation.

## Version 1.1.1 popup: software-only validation

At the owner's request, no device connection, installation or remote UI test was
performed for this patch. The live checks above describe the earlier 1.1 work.

`TilePromptTest` passed all four Yes/No × checkbox combinations, reopening with saved
preferences, successful-add duplicate suppression, failed/offline addition, storage
failure, retry behavior and cleanup preference handling. Both resource locales
contain the complete popup text. The full Android application compiled and its APK
passed signature and alignment verification. Physical popup layout and behavior
on the remote remain untested for 1.1.1.
