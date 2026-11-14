# Fold8 Control Lab v1.6

Built from the owner's **Fold8-Control-Lab-v15** source and APK, preserving Clock
Studio, live previews, bubble styles/padding, looks A–H, backups/import/export,
Appearance Studio, the device dashboard, and the root toolbox. This is the
Control Lab continuation; it is not the smaller v0.3 app from the earlier PR.

[Download the v16 artifact](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43576690674/artifacts/14106219555)
or inspect the [successful build](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43576690674)
and [source PR #2](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/2).
Read the signing instructions below before updating an existing v15 installation.

## Changes

- Status-bar double-tap still toggles the **bubble**, as in v15. The recognizer
  now observes the clock's containing window before Samsung can intercept child
  touches. It does not replace touch listeners or force clocks to be clickable.
- Seconds in the pull-down notification/Quick Settings header and panel clocks,
  with a separate setting and optional double-tap seconds toggle.
- Optional gesture haptics and AM/PM, included in settings, looks and backups.
  Old v15 backups still load with compatible defaults for new fields.
- One shared timer, pause on screen-off, no formatting while hidden/transparent,
  and minute cadence when visible clocks have seconds disabled. Settings are
  cached and observed; provider reads and gesture persistence run outside the
  SystemUI main thread. Formatting and unchanged bubble drawables are reused.
- Each OEM hook is optional and independent; Android attach listeners handle
  cleanup. Shade clocks retain their original font, colors and background.

The v15 font/contrast/bubble/padding/inset styling helper code is preserved
verbatim. Unknown date-only, lock-screen and AOD clock classes are not guessed.
No overlay, stock SystemUI APK replacement, kernel change, or automatic root
command is introduced. Existing explicit SystemUI restart/root-tool controls
retain their confirmation and tap requirements.

## Signing and updating v15 without losing its data

The supplied v15 APK uses the owner's existing **phone-local signing key**.
That private key is not included in the source or extractable from an APK.
Cloud/Actions debug signing therefore **cannot install as a normal update over
that v15 APK**. Do not uninstall v15 merely to bypass this mismatch if you want
to retain its private settings, looks and backups.

Actions provides a checksum-pinned **signing-only** helper alongside the
remotely compiled APK. The helper inside the original run 43576690674 is
superseded by `sign-v16-fixed.sh`, delivered in the support chat for that same
APK. Keep the already-downloaded APK and use that replacement helper. Future
Actions builds package the corrected helper as `sign-v16-with-phone-key.sh`.
The corrected helper embeds a verified pure-Java SDK35
signer and invokes your existing OpenJDK 26 binary directly (21/17 fallbacks),
avoiding PATH-selected `java`/`apksigner` wrappers. Java option injection is
cleared only in the signer subprocess. No package install or APK rebuild occurs.

Download `sign-v16-fixed.sh` to Downloads and keep the APK in Downloads/apps. In Termux:

```sh
bash ~/storage/downloads/sign-v16-fixed.sh ~/storage/downloads/apps/Fold8-Control-Lab-v16.apk
```

The helper uses your existing
`~/fold8-seconds-clock/private-signing-key.jks`, verifies the downloaded APK's
exact hash and version, signs locally, checks the certificate against the
supplied v15 certificate, and writes
`Downloads/apps/Fold8-Control-Lab-v16-update.apk` (beside the input APK). It never generates/replaces a key,
compiles, installs, enables a module, runs root, or restarts anything. A missing
key or mismatch stops without saving an update. The private key stays on the phone.

If an older helper prints `Native registration unable to find class ...
ProtoLogNative; aborting`, its Java launcher failed before signature verification.
That is not evidence of a corrupt APK. The fixed helper distinguishes runtime
startup failure, signer failure, input verification failure and certificate
mismatch. It accepts the original already-downloaded v16 APK without a rebuild.

Install the verified `-update.apk` as an update, keep only this module enabled
for **System UI (`com.android.systemui`)** in Vector, then reboot. In Clock
Studio enable **Double-tap clock to toggle bubble**, and select **Apply clock
settings**. For the shade use **Show seconds in Quick Settings** and, if wanted,
**Double-tap Quick Settings clock to toggle seconds**. New gestures remain
opt-in, preserving v15's defaults. Test both cover and inner displays, shade
drags/single taps, screen-off/on, and reboot persistence. Keep your v15 APK and
existing recovery method available. Compatibility remains unverified until
the owner tests this revision on the phone.

The separately supplied unsigned release APK is for users who already have
their own signing workflow. An Actions/debug-signed APK is useful for a fresh
install but is not signed by the owner's phone key.

## Build and evidence

JDK 17, SDK platform 35/build-tools 35.0.0, AGP 8.9.2, and the checksum-pinned
Gradle 8.11.1 wrapper. All compilation can run remotely:

```sh
python3 tests/check_source.py
python3 tests/check_additions.py
./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Actions uploads **Fold8-Control-Lab-v16-UNTESTED** with the signed debug APK,
unsigned release APK, checksums, and phone-signing helper. A `checks` artifact
contains Android test/lint/build reports. See [BUILD_STATUS.md](BUILD_STATUS.md).
Artifacts expire after 30 days. Xposed API stubs are compile-only and checked
absent from the APK; Vector supplies them at runtime. Tests use simulated
Android API 35; they do not prove Samsung Android 17 / One UI 9 hook behavior.

The optional `QSClockHeaderView` / `QSClockPanelView` shade names are supported
by public older Samsung code, not independently verified on this firmware.
Logs use `[Fold8ControlLab]`. Existing Control Center root tools can collect
relevant Vector/SystemUI messages when explicitly tapped.

[Source provenance](docs/SOURCE_PROVENANCE.md), [original v15 guide](docs/V15_README.md),
and [feature catalog](FEATURES.md) record the supplied baseline. The original
Termux **v15** builder is retained for reference; use the remote Gradle workflow
and signing-only helper above for v16. The old `v15-baseline.sha256` hashes are
historical evidence; `clock-core.sha256` pins the intentionally reviewed v16
sources and is not a claim of unchanged hooks or device validation.
