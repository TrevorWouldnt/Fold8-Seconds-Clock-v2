# Fold8 Seconds Clock

Experimental legacy Xposed module for the **existing Vector framework** on a
rooted Samsung Galaxy Z Fold8 (Android 17 / One UI 9). It writes seconds into
Samsung's existing `QSClockIndicatorView` TextView. It creates no overlay and
does not patch, re-sign, or replace Samsung's SystemUI APK.

**An APK build is not proof of device compatibility. No phone testing has been
performed.** See [BUILD_STATUS.md](BUILD_STATUS.md) for the validation record.

## Hook and behavior

The supplied `reference/*.txt` identify
`com.android.systemui.statusbar.policy.QSClockIndicatorView`, its
`onAttachedToWindow()` and `notifyTimeChanged(QSClockBellSound)` methods, and
`PhoneStatusBarClockManager.mClockView`. These notes came from earlier static
analysis; they are not independently verified disassembly in this repository.
The separately uploaded Samsung APK exceeds this environment's 32 MiB transfer
limit and was not available for renewed inspection. It is not committed here.

The module:

- Loads only in the main `com.android.systemui` process.
- Updates attached clock views on the main UI thread, aligned to wall-clock
  second boundaries; corrects Samsung's subsequent clock notifications.
- Uses Android attach-state listeners for cleanup, rather than assuming an
  undocumented Samsung detach method. Each display's attached view gets a ticker.
- Respects 12/24-hour settings, locale and timezone changes, and skips painting
  hidden views, an off display, or the always-on display. No wake lock is used.
- Logs with `[Fold8Seconds]`, and stops further writes for that SystemUI process
  after the first unexpected runtime failure.

It preserves the existing view's position, font and tint. Its simple time text
replaces QuickStar date/custom text and omits AM/PM for a compact 12-hour clock.
Extra width may affect icon space or clipping; this must be tested on both
screens. Other modules writing to the same view can still overwrite its text.

## Build and checks

Requirements: JDK 17, Android SDK platform 35 and build-tools 35.0.0, Python 3.
The checked-in Gradle 8.11.1 wrapper verifies its distribution SHA-256.

```sh
./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Output: `app/build/outputs/apk/debug/app-debug.apk`, application ID
`com.fold8.secondsclock`. This is a standard debug-signed, installable APK,
with no launcher activity; manage it in Vector or Android Settings.

The Robolectric tests exercise clock formatting, settings/locale/timezone
changes, second alignment, ticking, detach/reattach, and hidden/sleeping views.
They do not emulate Samsung SystemUI or prove the OEM hook signatures.
`tools/verify_apk.py` checks actual DEX class definitions to ensure the minimal
compile-only Xposed API stubs are **not bundled**. Their runtime implementation
comes from Vector. The `findAndHookMethod` return type must be
`XC_MethodHook.Unhook`; returning `Object` in a stub produces a different JVM
method descriptor and fails at runtime, even if compilation succeeds.

GitHub Actions builds on pushes to `main`, pull requests, or manual dispatch.
Open the successful **Build research APK** run under
[Actions](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/workflows/build-apk.yml),
then download artifact **Fold8-Seconds-Clock-UNTESTED** and extract
`app-debug.apk`. A checksum is included. GitHub sign-in may be needed to download
artifacts. Artifacts expire after 30 days; rerun the workflow when necessary.
Debug signing keys can differ between local builds and separate Actions runs;
if Android reports an incompatible update, disable the module and reboot,
uninstall the previous build, and install the new one.

## Install on the phone

1. Before activation, confirm you can use your existing KernelSU/Vector recovery
   method to disable this module if SystemUI will not start. No framework changes
   or other-module isolation tests are required.
2. Download the build artifact on your phone, extract it, and install
   `app-debug.apk` through Android's package installer. Allow installation from
   that browser/file manager when Android asks.
3. In **Vector → Modules**, enable **Fold8 Seconds Clock**. Select only
   **System UI (`com.android.systemui`)** as its scope. Reboot.
4. Watch the permanent status-bar clock for at least a minute. Verify seconds
   advance each second, including across a minute boundary. Test the cover and
   inner screens, folding/unfolding, screen off/on, and your 12/24-hour setting.
5. Check Vector's SystemUI/module logs for `[Fold8Seconds] installed hooks` and
   `[Fold8Seconds] attached status-bar clock ticker`. A `disabled ... after error`
   entry includes the exception and means the module stopped writing.

Rollback: disable **this module** in Vector and reboot, then uninstall it if
wanted. If SystemUI is inaccessible, use your already-verified recovery method
to disable this module or the framework's loading of it, then reboot.
Never install the supplied Samsung-SystemUI APK as a replacement.

`CODEX_HANDOFF.md`, `AGENTS.md`, and `reference/` retain the original handoff
context; follow-up message templates are historical notes, not build evidence.
