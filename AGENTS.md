# Codex project instructions

This repository is a **research prototype**, not a known-working APK. The owner has a rooted Samsung Galaxy Z Fold8 running Android 17 / One UI 9 with KernelSU, Zygisk Next, and Vector. Their goal is **seconds in the existing permanent top-left status-bar clock**, with no overlay and no replacement of the signed Samsung SystemUI APK.

- Read `CODEX_HANDOFF.md` and `reference/*.txt` before editing.
- This source was generated from static inspection of the owner's SystemUI APK. It was **not built into an Android APK or tested on device**. Do not claim otherwise.
- Verify hook class and method signatures; review the attach/detach lifecycle and possible UI-thread/performance issues. Any invalid hooks or crashes of `com.android.systemui` may impair device usability.
- Preserve the user’s current root framework and other apps. Do not tell them to test disabling modules one at a time: user explicitly declined that route.
- Never patch or replace the on-disk `/system` SystemUI APK. Hook `com.android.systemui` with existing Vector/Xposed only.
- Build a debug APK and record the exact Gradle command, status, generated artifact path, and whether any runtime/device testing occurred.
- Don't commit the proprietary supplied Samsung-SystemUI.apk or other extracted proprietary binary data. `reference/*.txt` are lightweight class signatures only.
- Keep changes confined to this project and use a GitHub branch and PR if repo permissions/workflow allow. If pushing fails, explain the exact blocker instead of claiming success.
