# Fold8 Control Lab v1.5 — Control Center Edition

A serious **on-device-only** expansion of the custom Galaxy Z Fold8 Android app.

- **Clock Studio** remains based on your already working v1.3 seconds/clock-bubble hook and v1.4 live preview.
- **Control Center** introduces Clock, Themes, Device and Root tabs with working actions, local backups, and diagnostic readings.
- **102 specific tools, controls, actions and diagnostics** are documented in `FEATURES.md`, including 39 read-only device readings. This is not 102 new System UI hooks. Nothing in the new tools silently changes the Samsung status bar.
- The Xposed clock hook, configuration provider, and metadata are checksum-pinned to unchanged v1.4 sources.
- Android's own share sheet is used for optional exports; there is **no INTERNET permission**, no background telemetry, and no new sensitive runtime permissions. `ACCESS_NETWORK_STATE` is the only new normal permission, for transport diagnostics.
- Explicit root commands are limited to read-only inspection and are only executed when tapped. The emergency module-disable command is copied, never run by the new toolbox.

## New app-only appearance controls
Custom accent, seven coordinated accent themes, high-contrast text, AMOLED-black background, compact cards, sticky-preview option, haptic feedback, app typography size, card translucency, background-art strength, and button rounding. These are stored locally and applied when the main activity reloads. You can back up, share, restore and import them.

## Clock safety and backups
Copy/share typed JSON, inspect and diff applied settings, verify the provider, compute contrast, estimate clock width, export/import with strict schema checking, save/restore/delete a private backup, and use six new saved looks C–H. Import and restore are confirmed before affecting the saved clock state.

## Device dashboard
39 public-API readings for battery, thermals, RAM, storage, display, Android build, uptime, network and power state. Refresh/copy/share, local history snapshots and comparisons are provided. Not all sensors are available on every device.

## Root toolbox
Explicit root access probes, process/owner checks, installed APK fingerprint, module-loading logs, System UI exceptions, KernelSU module list, SELinux state, application and system settings shortcuts, and recovery command copying. No root command runs at app startup.

## How to build on your Fold8

Use the verified existing Termux Android build toolchain (JDK 26, Android 35 compile jar, Android 34 resource jar, D8/R8, and your original private signing key).

```bash
bash ~/storage/downloads/Fold8-Control-Lab-v15-Termux.sh
```

The builder extracts a checksum-verified ZIP, validates the pinned System UI hook sources, compiles the app, signs it with the **existing** key, verifies the signature against the installed APK, and saves `Downloads/Fold8-Control-Lab-v15.apk`. It does not install, reboot or modify the running clock. Keep v1.4 as a backup.

## Verification status

Source validation, source/hash preservation, shell syntax and bundle round-trip are checked locally. **The full APK build and runtime behavior cannot be checked here without your phone's installed Android toolchain.** Build errors and any unavailable readings should be captured before installation. Do not interpret a signed APK as proof that every runtime feature works.
