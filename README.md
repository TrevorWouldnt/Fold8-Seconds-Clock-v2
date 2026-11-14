# Fold8 Seconds Clock

Experimental Vector/Xposed module for Samsung's existing status-bar and
notification/Quick Settings shade clocks. It uses existing TextViews, with no
overlay or replacement of Samsung's SystemUI APK.

**The owner reported that version 0.1 had no visible effect after reboot.**
Version 0.3 adds additional clock acquisition paths, real double-tap handling,
settings, and diagnostics. Its build and simulated tests do not prove it works
on the owner's Android 17 / One UI 9 phone. See [BUILD_STATUS.md](BUILD_STATUS.md).

[Download version 0.3](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43573750099/artifacts/14104769803)
(GitHub sign-in required). Extract and install `app-debug.apk`.
[Build evidence](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/actions/runs/43573750099)
and [source PR #1](https://github.com/TrevorWouldnt/Fold8-Seconds-Clock-v2/pull/1).

## Features

- Separate seconds switches for the status-bar and Quick Settings shade clocks.
- Double-tap either clock to toggle seconds for that clock type on both screens.
  Toggles persist across reboot. Drags, long presses, and multiple fingers do
  not count as double-taps. Existing single clicks are delayed briefly to allow
  the second tap, then delivered if no double-tap follows.
- Optional haptic feedback and AM/PM, plus phone-default, 12-hour, or 24-hour time.
- A launcher settings screen with live controls, reset, light/dark appearance,
  and narrow-screen scrolling.
- One shared timer: seconds when needed, minute cadence with seconds disabled,
  no repainting hidden/transparent clocks, and no timer while the screen is off.
  No wake lock, per-second preference reads, or per-tick settings writes.

The clock position, tint, and font remain Samsung's. The module's formatted
time replaces custom clock/date text; extra seconds can affect available width.
It intentionally skips unknown clock subclasses, date-only views, lock-screen
clocks, and always-on-display views.

## Install on the phone

1. Download **Fold8-Seconds-Clock-UNTESTED** from the successful GitHub Actions run
   and extract `app-debug.apk`. GitHub sign-in is required for artifact downloads.
2. Disable the old **Fold8 Seconds Clock** module in Vector and uninstall that
   APK before installing this one if Android rejects the update. Hosted debug
   signing keys differ between builds. This does not require changing other modules.
3. Install `app-debug.apk`. Open **Fold8 Seconds Clock** from your app list to
   choose settings. Seconds default to on for both clock types.
4. In Vector enable this module, scope it only to **System UI
   (`com.android.systemui`)**, and reboot. Confirm your existing recovery method
   can disable this module before activation.
5. Test seconds on the permanent status bar and after pulling down the
   notification/Quick Settings shade. Double-tap each clock, check each toggle
   independently, try a normal single tap and a shade drag, then fold/unfold,
   turn the screen off/on, and reboot to check persistence.

Rollback: disable this module in Vector and reboot. If SystemUI is inaccessible,
use your already-verified recovery method to disable this module/framework
loading, then reboot. Never install the supplied Samsung APK as a replacement.

## Diagnostics and supported hooks

Collect Vector/SystemUI log lines containing `[Fold8Seconds]`. Version 0.3 logs
the package/process callback, optional hook counts, observed STATUS/SHADE views,
the concrete window touch hook, first paint, toggles, and failures. A missing
optional hook does not remove the others; a failing view does not stop all clocks.

Known targets are `QSClockIndicatorView` (status), `QSClockHeaderView` and
`QSClockPanelView` (shade), with base `QSClock` construction and
`PhoneStatusBarClockManager.mClockView` as acquisition fallbacks. Hooks are scoped
to the main SystemUI process. Window touch events are observed before OEM
dispatch; the module never consumes shade drags or replaces touch listeners.

The owner's bundled `reference/` notes identify the status clock. Shade names
were checked against [public older Samsung source](https://github.com/488315/samsung_framework/tree/30cd25b68f67be792ab78fb46fb9f63af0964522/SystemUI-Jadx/sources/com/android/systemui/statusbar/policy),
which does not verify those names on One UI 9. The uploaded original Samsung APK
exceeds the attachment downloader's 32 MiB limit and has not been re-disassembled.
No proprietary APK/DEX files are committed here.

Settings use a device-protected provider for pre-unlock availability. Nonsecret
reads are public; writes are restricted to this app, platform UID, or the actual
system-installed `com.android.systemui` package/UID. SystemUI caches the settings
and observes changes; provider I/O runs outside the UI thread.

## Build

JDK 17, SDK platform 35 / build-tools 35.0.0, Python 3; checksum-pinned Gradle
8.11.1 wrapper and Android Gradle Plugin 8.9.2:

```sh
./gradlew --no-daemon --max-workers=4 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
```

Output is `app/build/outputs/apk/debug/app-debug.apk`. The application ID is
`com.fold8.secondsclock`. Xposed stubs are compile-only; the DEX verifier checks
that they are absent from the APK. Vector provides the runtime API.

Actions builds `main`, `codex/**`, pull requests, and manual dispatches. The APK
artifact includes its SHA-256; the `checks` artifact contains reports/logs.
Artifacts expire after 30 days. Tests use Robolectric API 35 and deterministic
gesture/time inputs; they do not run Vector's native hooks or Samsung firmware.
