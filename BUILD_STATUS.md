# Validation record

## Version 0.3 revision

- Version 0.1 was built successfully but the owner reported no visible effect
  after reboot. Its on-device result is **unsuccessful**, not verified working.
- Current revision adds status/shade controls, window-level double-tap detection,
  persistent settings and launcher UI, alternate OEM acquisition paths, isolated
  failures, and a shared screen-aware scheduler. No environment configuration was
  reapplied or published during this revision.
- Local checks: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed;
  31 tests, zero failures/errors/skips. APK packaging and v2 signature verified.
- Final code commit: `3883ba91bef7e502968a166429b4f6f68ef079df`.
- [GitHub Actions run 43573750099](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43573750099)
  completed successfully: 31 tests, zero failures/errors/skips, lint, assembly,
  signature and DEX checks. [PR #1](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/1)
  contains this feature revision; main was preserved for review.
- [APK artifact 14104769803](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43573750099/artifacts/14104769803)
  is named `Fold8-Seconds-Clock-UNTESTED`. It contains `app-debug.apk` and its
  checksum. The downloaded artifact's APK checksum, version code 3/name
  `0.3-clocks-and-gestures`, launcher activity, v2 signature, and exclusion of
  Xposed API stubs were independently verified after download.
- Exact Gradle validation command:
  `./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- New device verification: not performed. The exact cause of the owner's 0.1
  failure remains unconfirmed without its Vector log. Additional hooks and
  diagnostics address identified weaknesses but are not proof of compatibility.
- Original build evidence below is historical and describes the 0.1 artifact.

## Successful GitHub Actions build

- Run: [Build research APK #8](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43183294712).
- Final run conclusion: **success**.
- Built code commit: `e187615312e8a0e2f4361ae2cbfaad85f2ee5074`.
- Exact Gradle command (from the repository root):
  `./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Android unit tests: **6 executed, 0 failures, 0 errors, 0 skipped**.
- Android lint and APK assembly: passed.
- Packaging verification: passed; `assets/xposed_init` points to the module;
  actual DEX class definitions contain no bundled Xposed API stubs.
- Android `apksigner verify --verbose`: passed for the debug APK.
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Uploaded artifact: **Fold8-Seconds-Clock-UNTESTED**, ID `13927226926`.
  Download it from the run's Artifacts section, then extract `app-debug.apk`.
  It includes `app-debug.apk.sha256`; GitHub sign-in is needed for downloads.
- Artifact ZIP digest reported by GitHub (this is **not** the APK checksum):
  `448afe323f4734200f2dea3fa059662e1874cbfabfbc40c55a4b1a56fca0025b`.
- An additional `checks` artifact contains build logs, unit-test and lint reports.
- Artifacts are retained for 30 days. The workflow is saved in the repository
  and can rebuild them remotely without a local computer.

The successful run uses Ubuntu 24.04, JDK 17, checksum-pinned Android command-line
tools 12.0, SDK platform 35 / build-tools 35.0.0, AGP 8.9.2, and the
checksum-pinned Gradle 8.11.1 wrapper. Robolectric 4.14.1 simulates API 35, not
Samsung Android 17. Its framework JAR is resolved once through Gradle's cache.

## Local build and limitations

- Source was imported from the supplied `Fold8-Seconds-Clock-Codex-Handoff.zip`.
- A separate local `:app:assembleDebug` succeeded using checksum-verified
  Temurin JDK 17.0.17+10. The resulting local APK's v2 signature and module
  packaging verified. Its application ID is `com.fold8.secondsclock`,
  minSdk 29, targetSdk 35; no launcher activity is expected.
- Local APK SHA-256:
  `13369835d72bc0c3ff4e551716a5db880947b553aa920d3de826b6e5b03d3038`.
  Debug signing differs between local and hosted builds, so do not use this hash
  to check the Actions APK; use the checksum inside its artifact instead.
- Local test/lint execution was blocked by the cloud network policy denying a
  Maven redirect to `gcp.mirrors.maven.org`. GitHub Actions subsequently executed
  and passed the complete checks above; no local passing test run is claimed.
- The GitHub API is blocked locally at `api.github.com`. Git push and public
  GitHub run pages worked, so repository upload and Actions validation completed
  without API access. Draft additions for both hosts were saved; they have not
  been claimed applied or published. Review/save/publish them in environment
  settings if local test reruns or API access are wanted.
- Samsung APK reinspection was blocked by the attachment downloader's 32 MiB
  transfer limit. OEM signatures were reviewed from the bundled text notes;
  the supplied APK was not re-disassembled or independently hash-verified.
  No proprietary Samsung binaries were committed.
- The corrected `findAndHookMethod` return descriptor was checked against the
  upstream XposedBridge source and the compiled module bytecode.

## Concurrent-session audit

At built commit `e187615312e8a0e2f4361ae2cbfaad85f2ee5074`, all **34 tracked
files** were compared byte for byte with a fresh GitHub archive of that exact
commit. There were no differences, extra/missing remote files, uncommitted
changes, or unexpected commits/branches. `git fsck --full` passed. The original
handoff ZIP's SHA-256 remained
`df8154c013df5b96095cb2fe4f56d2663dc4bbbdd0acc5fb3624be233403bfda`.
Only expected Gradle/build outputs were ignored. No reset or force push was used.
Documentation may be updated after the built code commit; it does not change
that run's APK. This audit is evidence at the inspected commit, not a guarantee
against future edits by another session.

## Phone verification

**Not installed or tested on the phone.** A successful build and simulated tests
do not verify Vector loading, Samsung hook compatibility, status-bar layout, or
runtime safety on this firmware. Follow the install and rollback instructions
in README.md and verify your recovery method before activating the module.
