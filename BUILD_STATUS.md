# Fold8 Control Lab v1.8 validation

## Current v18 status

The owner's read-only diagnostic report confirms installed version 17/name 1.7
and live provider values `enabled=1`, `seconds=1`, `shadeSeconds=1`. Vector
reported failure to load `SecondsHook` at `<clinit>` line 64, the eager static
`Handler.createAsync(Looper.getMainLooper())` initialization in that source.
The failure occurred before the clock hooks ran. The filtered report does not
include the underlying exception type, so it does not establish the more
specific reason handler creation failed.

v18 defers main-handler initialization during Xposed entry loading while
preserving v17's recovery work, all Control Lab screens and the original
phone-local signing workflow. Local v18 validation **passed**. Exact command:

```sh
python /workspace/toolchains/run-gradle.py :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

The full run completed with `BUILD SUCCESSFUL` in 26 seconds. All **55 tests
passed**, with zero failures, errors or skips; lint and debug/release assembly
also passed. Both v18 source contracts passed. Generated APK paths are
`app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release-unsigned.apk`. APK verification
confirmed version 18/name 1.8, a valid v2 signature, the Xposed entry and 181
class definitions with no bundled Xposed API stubs.

The new `HookStartupTest` uses a fresh child class loader, clears the actual
Android main looper with `Looper.clearMainLooperForTest`, invokes the real
entry and gesture setup, restores the looper and verifies clock advancement
from seconds 5 to 6. A negative control temporarily restored the exact old
eager handler field: the test then failed in `SecondsHook.<clinit>` with a
null-looper `NullPointerException`. The fixed source was restored byte for byte
and the full 55-test suite passed again. This reproduces a known vulnerable
startup condition; it does **not** establish that the phone's exception was
the same type, which the filtered report did not include.

All **27** Python signing/preparation regression tests passed. The SDK35 real
signer-JAR test passed for v18, rejecting a wrong certificate and validating a
separate disposable test-certificate fixture. No phone private key was
available in Cloud or modified. Archive preparation reads the downloaded ZIP
from `Downloads/archives` without extracting it into Downloads, then writes a
new verified update into `Downloads/apps` for manual installation.

Hosted v18 validation is **pending**. The workflow is configured to verify
version 18 and produce `Fold8-Control-Lab-v18-UNTESTED` with APKs, checksums and
`sign-v18-with-phone-key.sh`; artifact links will be recorded after that run.
No physical-device test
of v18 has occurred; fixing the reported initialization site is not evidence
that seconds display works on the phone. Recovery cannot guarantee updates if
SystemUI is killed/frozen or Vector has not loaded the module.

## Historical v17 validation

The owner reported that seconds stopped after the previous revision, then
reported that v17 still showed no status-bar seconds after updating and
rebooting. The later diagnostic report identified a hook-load failure during
static main-handler initialization, as described above. v17 addresses
source-level scheduling, wake/lifecycle and styling-failure risks while
preserving the full v15 Control Lab application and its settings.

Local validation **passed** on 2026-11-16. Exact command:

```sh
python /workspace/toolchains/run-gradle.py :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Both `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release-unsigned.apk` were generated.
All **54 tests passed**, with zero failures, errors or skips. The 12 new recovery
tests cover throwing style setters/snapshot getters, transient text writes,
lost callbacks with stale queued state, missed wake events, real sleep, hidden
fold containers, replacement/reattached windows, disabled settings, minute
cadence, Samsung text overwrites and font scale 1.30. Android lint passed.
Both source contracts, 16 signing-helper tests, APK module packaging, version
17/name 1.7 and the debug APK v2 signature passed verification. Hosted validation
also passed as recorded below. Regression tests use simulated Android
API35 and do not establish Samsung Android17 /
OneUI9 compatibility. The owner's v17 test did not show seconds; no Cloud-side
physical device or native Vector-hook test has occurred. Recovery
cannot guarantee updates in a killed/frozen SystemUI process or without Vector
loading the module.

The v17 Actions artifact is named `Fold8-Control-Lab-v17-UNTESTED`,
including a checksum-pinned `sign-v17-with-phone-key.sh`. The owner reported
successful v16 phone-key signing with the corrected OpenJDK26 helper; the
private key remains exclusively on the phone. The same approach is retained
for v17 and cloud compilation does not provide that original signature.

## Hosted v17 build

[GitHub Actions run 43973097163](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43973097163)
completed successfully for source commit `d61c36b882a87fcbb166dd8d3c0c64e136f6cac2`.
The run reports **54 tests**, with zero failures, errors or skips. Both source
contracts, Android lint, debug/release assembly, 16 signing-helper fixtures and
the real SDK35 signer-JAR integration test passed.

[Artifact 14278783547](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43973097163/artifacts/14278783547)
was downloaded and independently checked. The debug APK has version 17/name
1.7, a valid v2 signature, the expected Xposed entry and 180 class definitions,
with no bundled Xposed API stubs. Its SHA-256 is:

`16c498ab19c1cd690e9bb8d1171841580bf98daa1b8e6e67e2c3d94fc144770a`.

The packaged phone-signing helper passed shell syntax validation and pins this
exact APK hash, version 17 and the owner's original certificate. It still
requires the phone-local private key to create a data-preserving update.
[PR #3](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/3) contains
the v17 source. Later documentation-only changes do not change the linked
run's APK. The owner reported no visible status seconds after updating and
rebooting. A read-only Termux diagnostic collector was added separately to
identify the installed version, persisted configuration and framework loading;
that script does not change the APK. No Cloud-side physical device or native
Vector-hook validation occurred.

## Historical v16 evidence

The sections below record v16 results and are not evidence of a v17 build.

## Correct baseline

This revision starts from the owner's Fold8-Control-Lab-v15 source and signed
APK, not the smaller v0.3 module. See [source provenance](docs/SOURCE_PROVENANCE.md)
for input hashes and the v15 certificate. All four original core source pins
matched before intentional changes. Original appearance preferences, diagnostics,
extra UI preference implementation, and bubble/font/contrast/padding/inset
helper code were preserved byte for byte. Original screens, looks and backups
were extended, not replaced. The prior smaller-app PR is superseded.

## Local validation

- Exact command: `./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`.
- Toolchain: JDK 17.0.17+10, Gradle 8.11.1, AGP 8.9.2, Android SDK 35 / build-tools 35.0.0.
- **42 tests executed, zero failures/errors/skips**: configuration/provider and
  backward-compatible backups/presets (8), original UI/tool safety (2), clock
  styling/scaling/ticker lifecycle (5), recognizer (15), window gesture integration (12).
- Android lint passed after correcting a v15 numeric typeface constant. No lint
  baseline or disabled assertion was introduced.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned release: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Debug APK's v2 signature, package/version 16 / 1.6, launcher activity, Xposed
  entry and absence of packaged Xposed API stub class definitions verified.
- Source preservation checks `tests/check_source.py` and `tests/check_additions.py` passed.
- Signing helper shell syntax checked. It has not run with the owner's phone key;
  that private key is unavailable in Cloud and remains on the phone.

## Hosted build

[GitHub Actions run 43576690674](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43576690674)
completed successfully for commit `e037978a768fb7b4ca3813f9606034b1aa37722e`.
It passed both source contracts, all **42 tests** (zero failures/errors/skips),
Android lint, debug/release assembly, signature and DEX packaging checks.

[Artifact 14106219555](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43576690674/artifacts/14106219555)
is named `Fold8-Control-Lab-v16-UNTESTED`. Its downloaded APKs matched SHA256SUMS;
the debug APK's signature/version 16/name 1.6, ControlLabActivity launcher and
absence of Xposed stubs were independently rechecked. Debug APK SHA-256:
`f47138d9998a5c65b79e7f60d24b9240627c0aca6e6fbf189aa3cc72fb84efbf`.
The signing-only helper contains that exact input hash and the supplied v15
certificate hash. Its shell syntax verified; original phone-key signing remains
unexecuted in Cloud because the key is absent.

[PR #2](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/2) contains
the corrected Control Lab continuation. Main remains preserved for review.
Any later documentation-only commit does not change this run's APK.

## Update signing and device limits

### Signing-helper correction

The owner's Termux screenshot showed an ART `ProtoLogNative` registration abort
before signature verification. The original helper invoked a PATH-selected
`apksigner`, while the supplied v15 builder explicitly activated JDK 26. The
replacement selects that existing OpenJDK directly, embeds/pins Google's SDK35
signer JAR, clears Java injection variables only inside child processes, and
does not invoke phone `aapt` or the `apksigner` wrapper. The APK remains unchanged
at SHA-256 `f47138d9998a5c65b79e7f60d24b9240627c0aca6e6fbf189aa3cc72fb84efbf`.

Validation: 14 isolated shell regression tests passed, including wrong PATH
wrappers, Java option injection, tampering, startup/process aborts, certificate
mismatch and key/output preservation. A separately packaged real-JAR test on
Cloud JDK17 rejected a disposable non-phone certificate and then successfully
signed/verified a separate fixture pinned to that disposable test certificate.
The delivered helper retains the owner's original certificate pin; no phone key
was available or modified. It still needs execution on the owner's JDK26 phone.
No Android app code changed or new APK was generated for this helper correction.

Signer JAR SHA-256:
`00ef9948f843fe395d2440ae3ef41405b8040a6d5d46493bd1902ac0ee6deae7`.

The supplied v15 APK certificate SHA-256 is
`619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf`.
Actions signing does not match that certificate. A normal data-preserving update
requires the existing original key. The provided signing-only helper verifies the
input APK/hash/version, signs with the existing phone-local key, compares the
resulting certificate with v15, and only then saves an update APK. It compiles,
installs and restarts nothing. Do not uninstall v15 just to bypass a signature
error without preserving its data.

No physical device or Vector native-hook test occurred. Automated tests use
simulated Android API 35. The optional shade class names are drawn from public
older Samsung code and still need verification on the owner's Android 17 / One
UI 9 firmware. No stock SystemUI APK, kernel, root framework or environment
configuration was changed or published. Existing explicit root-tool/restart
actions were preserved but never executed during development/testing.
