# Follow-up messages to Codex (use only when relevant)

## After Codex imports the ZIP but hasn't built
Please finish importing and reviewing the project, fix any build or hook mistakes, and run an actual `:app:assembleDebug` build. Commit the files and workflow to `TrevorWouldnt/Fold8-Seconds-Clock-v2` if authorized. Provide the specific GitHub Actions run and APK artifact location. Do not treat syntax checks as an APK build.

## If build fails
Read the exact Gradle/GitHub Actions error logs, make the smallest necessary source or workflow changes, and rerun the build. Repeat until there is an actual APK artifact, or explain precisely why you are blocked. Do not assert success without a real artifact.

## Once the APK is available
Give me short step-by-step instructions for installing the debug APK on my rooted Galaxy Z Fold8 using Vector. Include where to download the artifact, exact System UI scope (`com.android.systemui`), restart requirement, what success should look like, how to check Vector logs for `[Fold8Seconds]`, and how to disable the module and reboot if System UI crashes. Do not tell me to uninstall or disable my other customization modules one by one.

## If Codex asks for the firmware APK
I can privately attach `Samsung-SystemUI.apk` from this ChatGPT conversation. It is my original ~65 MB package. Verify the hash in `CODEX_HANDOFF.md`. Use it ONLY to verify clock class and method signatures; do not commit it to the public GitHub repository and do not try to install it onto the phone.

## If Codex says it cannot see the uploaded handoff ZIP
Use the attached `Fold8-Seconds-Clock-source.zip` instead (same original Android project without the extended handoff notes), or ask me to reattach `Fold8-Seconds-Clock-Codex-Handoff.zip`. Do not generate or guess a substitute project without checking source contents.
