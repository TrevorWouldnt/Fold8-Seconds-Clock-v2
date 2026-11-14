# Fold8 Control Lab v1.6 validation

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
