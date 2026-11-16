# Fold8 Control Lab v1.8

Built from the owner's **Fold8-Control-Lab-v15** source and APK, preserving Clock
Studio, live previews, bubble styles/padding, looks A–H, backups/import/export,
Appearance Studio, the device dashboard, and the root toolbox. This is the
Control Lab continuation; it is not the smaller v0.3 app from the earlier PR.

The owner's diagnostic report confirms that v17/1.7 was installed and its live
settings provider had customization, status seconds and shade seconds enabled.
Vector reported that `SecondsHook` failed to load at static initialization,
at the eager main-handler creation in `SecondsHook.<clinit>` line 64. This
happened before clock hooks could run; the filtered report does not include the
underlying exception type. v1.8 defers handler initialization instead of creating
it while Vector loads the entry class. All 55 tests, lint and both APK builds
passed locally and in GitHub Actions; phone testing is pending.
See [BUILD_STATUS.md](BUILD_STATUS.md) for evidence.
Read the signing instructions below before updating an existing installation.

[Download the v18 APK and signing helper ZIP](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43982331860/artifacts/14283353123)
from the [successful build](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43982331860).
The source is available in [PR #4](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/4).

The [read-only Termux collector](tools/collect-clock-diagnostics.sh) gathers installed
version/Xposed entry, clock settings, SystemUI mappings and filtered Vector logs
into `Downloads/Fold8-Diagnostics`. Grant the existing Termux root request when
prompted and attach its text report. It changes no clock settings, module scope
or system files and performs no restart. Missing logs alone are inconclusive;
scope reads are included only when an existing SQLite CLI is available.

## Changes

- Defer main-handler creation until module execution so loading the Xposed entry
  class does not eagerly access Android's main looper.
- Retain v17's bounded ticker recovery, styling isolation and full Control Lab
  screens and settings. Fixing hook startup is required before those recovery
  paths can run; v18 still needs on-phone validation.
- Time writes are isolated from optional styling failures so a broken visual
  customization does not suppress the core clock update.
- A low-frequency watchdog recovers a stopped ticker, reacquires tracked clocks,
  and checks for missed wake events. Window observer bindings are renewed after
  root changes. Recovery respects intentionally disabled status/shade seconds.
- Recovery and error paths are bounded; hidden clocks avoid continuous formatting,
  and no wake lock or background service is introduced. A killed/frozen SystemUI
  or unloaded Vector hook cannot be made to update by this code.
- Status-bar double-tap still toggles the **bubble**, as in v15. The recognizer
  now observes the clock's containing window before Samsung can intercept child
  touches. It does not replace touch listeners or force clocks to be clickable.
- Seconds in the pull-down notification/Quick Settings header and panel clocks,
  with a separate setting and optional double-tap seconds toggle.
- Optional gesture haptics and AM/PM, included in settings, looks and backups.
  Old v15 backups still load with compatible defaults for new fields.
- One shared clock timer plus a low-frequency recovery check, pause on screen-off,
  no formatting while hidden/transparent,
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

## Signing and updating without losing its data

The supplied v15 APK uses the owner's existing **phone-local signing key**.
That private key is not included in the source or extractable from an APK.
Cloud/Actions debug signing therefore **cannot install as a normal update over
that v15 APK**. Do not uninstall v15 merely to bypass this mismatch if you want
to retain its private settings, looks and backups.

Actions provides a checksum-pinned **signing-only** helper alongside the
remotely compiled APK. v18 builds package it as `sign-v18-with-phone-key.sh`.
Use the helper delivered with the same APK; its input hash is specific to that
build. The helper embeds a verified pure-Java SDK35
signer and invokes your existing OpenJDK 26 binary directly (21/17 fallbacks),
avoiding PATH-selected `java`/`apksigner` wrappers. Java option injection is
cleared only in the signer subprocess. No package install or APK rebuild occurs.

For downloads automatically sorted into folders, keep the v18 artifact ZIP in
`Downloads/archives`. Run this in Termux to fetch the
[preparation script](tools/prepare-update-from-archives.sh) from the exact
reviewed source commit into a temporary file, prepare the update and remove the
temporary script:

```sh
(
  set -eu
  fold8_prepare="$(mktemp "$TMPDIR/fold8-prepare.XXXXXX")"
  trap 'rm -f -- "$fold8_prepare"' EXIT
  curl -fL --retry 2 'https://raw.githubusercontent.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/e927b0770ab95ee219052f9f7994540b6c82417c/tools/prepare-update-from-archives.sh' -o "$fold8_prepare"
  bash "$fold8_prepare" 18
)
```

This selects the newest `Fold8-Control-Lab-v18*.zip` in `Downloads/archives`,
reads its APK and signing helper into a private temporary directory, then saves
the verified signed update as `Downloads/apps/Fold8-Control-Lab-v18-update-<timestamp>-<pid>.apk`.
The ZIP is not extracted into Downloads and existing APKs are not overwritten.
Open the resulting APK from Samsung My Files for manual installation.

If the APK and same-build signing helper are already downloaded separately,
the direct alternative is:

```sh
bash ~/storage/downloads/sign-v18-with-phone-key.sh ~/storage/downloads/apps/Fold8-Control-Lab-v18.apk
```

The helper uses your existing
`~/fold8-seconds-clock/private-signing-key.jks`, verifies the downloaded APK's
exact hash and version, signs locally, checks the certificate against the
supplied v15 certificate. When invoked directly, its default output is
`Downloads/apps/Fold8-Control-Lab-v18-update.apk` (beside the input APK).
The archive preparation script selects a new timestamped output instead.
Neither script generates/replaces a key,
compiles, installs, enables a module, runs root, or restarts anything. A missing
key or mismatch stops without saving an update. The private key stays on the phone.
If an update output already exists, the helper leaves it untouched.

If an older helper prints `Native registration unable to find class ...
ProtoLogNative; aborting`, its Java launcher failed before signature verification.
That is not evidence of a corrupt APK. The fixed helper distinguishes runtime
startup failure, signer failure, input verification failure and certificate
mismatch. The owner subsequently reported successful execution of the corrected
v16 helper on OpenJDK 26. The v18 helper uses that same signing approach.

Install the verified `-update.apk` as an update, keep Fold8 Control Lab enabled
and scope it to **System UI (`com.android.systemui`)** in Vector, then reboot. In Clock
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

Actions uploads **Fold8-Control-Lab-v18-UNTESTED** with the signed debug APK,
unsigned release APK, checksums, and phone-signing helper. A `checks` artifact
contains Android test/lint/build reports. See [BUILD_STATUS.md](BUILD_STATUS.md).
Artifacts expire after 30 days. Xposed API stubs are compile-only and checked
absent from the APK; Vector supplies them at runtime. Tests use simulated
Android API 35; they do not prove Samsung Android 17 / One UI 9 hook behavior.

The optional `QSClockHeaderView` / `QSClockPanelView` shade names are supported
by public older Samsung code, not independently verified on this firmware.
Logs use `[Fold8ControlLab]`. Existing Control Center root tools can collect
relevant Vector/SystemUI messages when explicitly tapped.

The previous [v17 build](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43973097163)
passed 54 simulated Android tests and produced
[artifact 14278783547](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43973097163/artifacts/14278783547).
[PR #3](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/3) preserves
that source. Its subsequent on-phone hook-load failure is recorded above; a
successful build did not establish Vector compatibility.

[Source provenance](docs/SOURCE_PROVENANCE.md), [original v15 guide](docs/V15_README.md),
and [feature catalog](FEATURES.md) record the supplied baseline. The original
Termux **v15** builder is retained for reference; use the remote Gradle workflow
and signing-only helper above for v18. The old `v15-baseline.sha256` hashes are
historical evidence; `clock-core.sha256` pins the intentionally reviewed current
sources and is not a claim of unchanged hooks or device validation.
