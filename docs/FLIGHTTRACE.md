# FlightTrace — experimental USB telemetry companion

FlightTrace is an original Android companion prototype for an external phone connected as USB host to an OpenKatmai-enabled DJI RC 2. It reads a version-specific DJI Fly received-data cache programmatically. It does not read coordinates from screenshots and does not implement aircraft controls.

## Current validation

- Adapter targets DJI Fly **1.21.8** only. Other versions are rejected before attachment.
- The earlier direct cache probe returned changing aircraft battery and satellite values and a numeric aircraft position during a grounded session.
- The packaged bridge and complete phone application build successfully, with APK signature and alignment verification.
- Position integrity tests passed. A host integration test passed against the actual RC debugging daemon: UID/device identity, repeated shell streams and restricted helper-upload destinations.
- The packaged bridge and real USB path were tested on a Samsung Galaxy Tab A7 SM-T505N running Android 12. FlightTrace received changing battery values and entered recording mode. Direct JSON capture separately confirmed null aircraft/Home Point coordinates, RC GPS `no_fix`, and no adapter read errors in that session.
- A first USB session stopped after 19 samples. Recovery exposed stale packets and partial payload bytes remaining in the USB endpoint. The updated transport flushes stale endpoint bytes before CNXN and drains bounded stale protocol packets. Long-duration operation, background recording durability, exports and in-flight behavior still require validation.
- After the recovery fix, the tablet remained in receiving/recording state through 55 samples, with changing battery readings. This short test does not establish long-duration stability.
- RC GPS and Home Point can be unavailable. Neither is synthesized from the aircraft position or the phone GPS.

## Live map (v0.2.0)

**Live map** opens a full-screen OpenStreetMap view (osmdroid 6.1.20, SHA-256
pinned in `build.ps1`) with:

- aircraft marker, rotated by the attitude yaw when available;
- Home Point and RC markers (only when those coordinates are confirmed);
- the live recorded track as a polyline (all segments, gaps preserved);
- a status panel: stream freshness, aircraft fix, satellites, GPS signal,
  battery, flight mode, motors, altitude/speed when the cache exposes them,
  computed distance-to-home, and track point count;
- a Follow toggle; the map recenters on the aircraft while it is on.

Tiles are downloaded over HTTPS and cached in app-private storage. No API key
is used. Fields that the cache does not expose render as `—`; no value is
synthesized. The bridge reports candidate altitude/distance keys; unavailable
candidates are listed under `read_errors` in the raw telemetry dialog.

## Build

Run `./src/flighttrace/build.ps1` on Windows with Android SDK platform/build tools 35, JDK 17 and NDK 28.2.13676358. Paths can be supplied as script parameters. The output is `src/flighttrace/build/FlightTrace.apk`. The phone application contains no native libraries; its bundled helper contains the ARM32 agent required by the tested Fly process. The osmdroid AAR is downloaded from Maven Central on first build and verified against a pinned SHA-256. Keep the local signing key private.

## Connect

1. While safely grounded, enable the existing OpenKatmai clean debugging daemon on the RC 2. Apply the documented ADB cgroup detachment fix if Camera View terminates debugging.
2. Install FlightTrace on the external Android phone, and open DJI Fly on the remote.
3. Connect the phone as USB host using an appropriate data/OTG connection. Grant USB access in FlightTrace, then select **Connect USB**. Grant notifications so the recording service is visible.
4. FlightTrace verifies RC identity and Fly version, uploads and installs only its own helper APK, then attaches its read-only cache reader. It never automatically restarts or force-stops DJI Fly.
5. Recording begins after telemetry arrives. Use **Stop** to close the phone connection and recording. The reader lives in the Fly process until that process ends. It does not install a boot service.
6. Use **Export latest recording** for NDJSON, CSV, GPX or GeoJSON. Files remain in the phone's private application storage until exported. Uninstalling the app removes private recordings.

The first phone generates a private random session secret. The stream binds only to remote loopback and is reached through ADB. A different phone cannot take over an existing reader session automatically; restart DJI Fly only when safely grounded before pairing a different installation. Debugging access itself remains privileged and must be managed separately.

The installer creates versioned symlinks under `/data/app/openkatmai-flighttrace/` to its installed helper APK and ARM32 library. These provide agent paths without `=` characters, which otherwise interfere with agent specifications. The ADB destination is `tcp:37684`; it reaches a loopback-only reader. Test readers from older builds can remain alive until the Fly process ends. Neither the symlinks nor the helper start anything at boot.

## Meaning of the displayed data

Aircraft, Home Point and remote GPS are separate records. Invalid or missing coordinates never become zero-valued track points. Track segments break after missing fixes or gaps greater than four seconds. Last seen positions retain their observation time and remain labeled when the stream or aircraft fix is unavailable. The offline plot has no street basemap; Google Maps buttons open the actual available coordinates externally.

The satellite status appears above the plot. Missing telemetry and missing satellite values are distinct from a reported count of zero. Positive counts indicate reported satellite reception, not proof of a valid position. A separate state waits for confirmed aircraft coordinates, and the reported GPS signal level is shown when available. Disconnected streams do not present old satellite counts as current.

Aircraft position eligibility requires connection, valid coordinates and at least four reported satellites. **The underlying cache does not expose a verified measurement timestamp.** A newly received packet therefore does not prove a newly measured GPS fix; observation timestamps describe receipt, not GPS measurement time. This prototype cannot replace DJI Fly's flight display.

Remote coordinates come only from the RC's Android GPS provider. A missing fix remains unavailable. Cached Home Point is not proof of a currently confirmed home location. Battery and flight fields retain null values and read errors in the raw stream.

Warning coverage is partial: wind warning and motor overload cache fields where available. Full DJI HMS warnings are not implemented. No Return-to-Home, takeoff, landing, movement or motor command endpoints exist.

Recordings contain precise private locations. Do not commit recordings, screenshots, pairing secrets, signing keys or vendor APKs to the repository. This prototype has not been validated in flight.
