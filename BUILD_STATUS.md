# Validation record

- Source imported from the supplied `Fold8-Seconds-Clock-Codex-Handoff.zip`.
- Samsung APK could not be transferred: attachment downloader limit is 32 MiB.
  OEM signatures were reviewed from the bundled text notes, not re-disassembled.
- Toolchain: checksum-verified Temurin JDK 17.0.17+10, Gradle 8.11.1,
  Android Gradle Plugin 8.9.2, Android API 35 / build-tools 35.0.0.
- `:app:assembleDebug`: generated a real debug APK locally. Signature verified
  with Android `apksigner` (v2); package `com.fold8.secondsclock`, minSdk 29,
  targetSdk 35. Module entry verified, no Xposed API stub definitions in DEX.
- Local `:app:testDebugUnitTest` and `:app:lintDebug`: initially blocked on
  `org.robolectric:nativeruntime-dist-compat:1.0.16`; Maven redirects to
  `gcp.mirrors.maven.org`, currently denied by the environment network policy.
  No passing test-suite or lint result is claimed from that attempt.
- GitHub Actions API: initially blocked before GitHub by network policy for
  `api.github.com`. Both required hosts have been saved as draft additions.
- GitHub publication and Actions result: pending validation.
- Device/runtime testing: **not performed**. Phone compatibility is unverified.
