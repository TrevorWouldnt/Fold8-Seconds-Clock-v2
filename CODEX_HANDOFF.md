# Fold8 seconds clock: verified handoff to Codex

## Repository and outcome
- GitHub: `TrevorWouldnt/Fold8-Seconds-Clock-v2`
- Goal: build an installable **Android APK** which, when installed and scoped via existing Vector/Xposed, makes Samsung's **actual permanent top-left status bar clock** display current time **with seconds**, updating every second; no overlays or whole-status-bar replacements.
- This is a **research prototype**. No successful APK build or device run has been verified by this chat. The user's earlier project source zip was created, but the ChatGPT GitHub connector could not push it (403 `Resource not accessible by integration`). Codex may be able to work with the repository if its own environment has write access.

## Device and prior tests
- Rooted Samsung Galaxy Z Fold8; Android 17 / One UI 9; KernelSU, Zygisk Next, Vector, Termux.
- `settings put secure clock_seconds 1` returned `1` but did not display seconds.
- Samsung Good Lock QuickStar shows seconds in expanded Quick Panel, not the always-on status bar clock.
- One UI X v1.12.0 displayed options `h:mm:ss` and `Update status bar clock every second`, persisted them, but no visible change. Battery customization also failed. Vector diagnostic log confirmed `Loading Vector/Xposed for com.android.systemui` and `Loaded module io.github.soclear.oneuix successfully`.
- Vector logs also warned about missing remote `preference.json` in some cases; not proven causal.
- ClockSeconds and StatusBarTweak were tried; did not work for user. The user explicitly DOES NOT want more one-by-one conflict isolation or repeated standard settings tests.
- Essentials app can reposition/hide clock, but its seconds toggle does not apply on this firmware.

## Reverse engineering performed
The owner's original 67,392,192-byte `Samsung-SystemUI.apk` was statically inspected. Its SHA-256:
`4acf1d7c2d9313fa951d33ac585231d7ef489942dd6d9983dec86a258262de6b`.
Relevant *observed class structures* in `classes3.dex`, summarized in `reference/`:
- `com.android.systemui.statusbar.phone.PhoneStatusBarClockManager` has `mClockView: QSClockIndicatorView`.
- `com.android.systemui.statusbar.policy.QSClockIndicatorView` extends `QSClock`, which extends Android TextView. It declares `notifyTimeChanged(QSClockBellSound)` and `onAttachedToWindow()`.
- `com.android.systemui.statusbar.policy.QSClockBellTower` produces clock data containing both `TimeText` and `TimeTextWithSeconds`.
- Earlier method inspection suggested the visible `QSClockIndicatorView.notifyTimeChanged(...)` uses `TimeText`, not `TimeTextWithSeconds`. **Verify this from the exact APK if necessary**, since signature extracts alone do not prove method data flow.
- A separate AOSP-style Clock supports `clock_seconds`; Samsung uses a different status-bar view.

## Existing project
- Android Gradle Plugin 8.9.2 / Gradle 8.11.1 / JDK 17 / compileSdk 35.
- Legacy Xposed module entry: `app/src/main/assets/xposed_init`, Java class `app/src/main/java/com/fold8/secondsclock/SecondsHook.java`.
- Xposed compilation uses a local minimal Java stubs submodule as `compileOnly`. **Do not package the stub Xposed classes inside the Android APK.** The actual runtime API is supplied by Vector.
- `.github/workflows/build-apk.yml` builds `:app:assembleDebug` and uploads artifact `Fold8-Seconds-Clock-UNTESTED`.
- The original source had its Java syntax checked against local stubs, but **that is not an Android build**. Codex must validate Gradle/manifest/API behavior.

## What Codex should do
1. Inspect all project files and `reference/*.txt`. Review the hook strategy and Samsung class signatures. The original hook targets `QSClockIndicatorView.onAttachedToWindow`, `notifyTimeChanged(QSClockBellSound)`, and `QSClock.onDetachedFromWindow`, using a TextView ticker and Android DateFormat. Fix any signature or lifecycle mistakes before shipping.
2. Perform a real Gradle Android **debug APK build**, fix compilation issues, and provide logs or evidence. Ensure `xposed_init` and metadata are packaged, and compile-only stub classes are excluded from APK.
3. Ensure the hook runs only in the main `com.android.systemui` process, uses the existing clock TextView (no overlay), updates on second boundaries, honors 12/24-hour time and date/locale changes where feasible, cancels callbacks on detach, and fails harmlessly if an OEM API differs.
4. Confirm the GitHub Actions workflow is valid and works; if access permits, commit files to the repository and run Actions; make a PR if appropriate. Provide the actual build artifact or workflow URL, but **never imply it has been tested on the phone**.
5. Provide a short on-phone install plan: download debug APK, install normally, Vector → Modules → enable `Fold8 Seconds Clock`, scope only `com.android.systemui`, reboot, verify seconds; give safe rollback (disable module and reboot) and ask the user to confirm a KernelSU module recovery path before activation.

## Boundaries
- **Do not attempt to flash, replace, re-sign, or modify Samsung's stock SystemUI.apk** on the device.
- **Do not upload/commit Samsung-SystemUI.apk** to the public GitHub repo; use it as private reference if the user separately attaches it in Codex.
- Do not ask the user to make a repo again; `TrevorWouldnt/Fold8-Seconds-Clock-v2` exists.
- Do not ask the user to install more Git clients or perform routine one-at-a-time module troubleshooting.
- Do not claim success merely because the build passes: only user on-device validation proves the seconds display works.
