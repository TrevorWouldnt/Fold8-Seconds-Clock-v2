# Fold8 Control Lab v1.6 · Existing v1.5 capabilities and additions

This catalog describes real source-level implementations, not a speculative roadmap. They require an Android build and a device test before being treated as proven on the Fold8. Several tools are *read-only readings*, not System UI modifications. Numbers are provided so work can be audited, not to inflate the scope with empty toggles.

## Clock workflow / protection

1. Open Clock Studio from the new Control Center.
2. Inspect the exact saved clock and bubble configuration.
3. Show differences between the current clock and safe defaults.
4. Check the cross-process settings provider used by the Xposed hook.
5. Compute an actual WCAG contrast ratio for chosen opaque text/pill colors.
6. Recommend black or white text from the selected bubble luminance.
7. Estimate the horizontal clock width and notification-space demand.
8. Export a structured clock configuration as JSON to the clipboard.
9. Share a clock configuration through Android's share sheet.
10. Save a private clock backup without touching the running hook.
11. Inspect a clock backup without applying it.
12. Compare the clock backup with active saved preferences.
13. Restore a backup only after validation and confirmation.
14. Import externally copied clock JSON with schema and range validation.
15. Read when the last private clock backup was made.
16. Delete an obsolete private backup only with confirmation.
17. Save six additional named clock looks (C–H).
18. Load any of those looks after explicit confirmation.

## App-only Appearance Studio additions

19. Set a custom accent color by hex value.
20. Select from seven calibrated accent palettes.
21. Enable a high-contrast app text mode.
22. Use a pure-black AMOLED app background.
23. Switch to compact card spacing.
24. Turn the persistent pinned clock preview on/off.
25. Use optional haptic feedback on Control Center actions.
26. Choose an exact app typography scale between 85 and 125%.
27. Adjust card translucency without changing Samsung's status bar.
28. Tune the decorative glow strength.
29. Choose the rounding of app action buttons.
30. Export the new appearance settings as JSON.
31. Share the appearance settings through Android.
32. Save a private appearance backup.
33. Inspect the saved appearance backup.
34. Restore a validated appearance backup with confirmation.
35. Import appearance settings from the clipboard.
36. Reset only the additional UI options, leaving the clock untouched.
37. Copy a combined report of original and new appearance settings.

## Diagnostics from Android's public APIs

38. Battery charge percentage.
39. Charging, discharging, or full state.
40. Charger connection source (USB, AC, wireless, dock, or battery).
41. OS-reported battery health status.
42. Battery temperature sensor.
43. Battery terminal voltage.
44. Battery charge/discharge current when available.
45. Estimated battery-side power in watts (not charger input).
46. Remaining battery charge counter when the kernel exposes it.
47. Battery Saver state.
48. Operating-system thermal-pressure level.
49. Device uptime since its last boot.
50. Total device RAM.
51. Available RAM.
52. Android's low-memory indicator.
53. Total internal storage volume.
54. Currently available internal storage.
55. Current display resolution in pixels.
56. Display pixel-density configuration.
57. Current display refresh rate.
58. Screen orientation.
59. Smallest-width dp, useful for folding layouts (not a hinge sensor).
60. System font-scale factor.
61. System dark-mode setting.
62. Android release version.
63. Android security patch level.
64. Current Samsung firmware build label.
65. Linux kernel release.
66. Hardware manufacturer and model.
67. Supported CPU ABIs.
68. Number of CPU cores available to the app.
69. Current language locale.
70. Time-zone identifier.
71. Screen sleep timeout.
72. Active Wi-Fi transport.
73. Active cellular transport.
74. Active VPN transport.
75. Whether the active connection is marked metered.
76. Refresh the diagnostic dashboard on demand.
77. Copy a diagnostic snapshot.
78. Share a diagnostic snapshot via Android's standard share sheet.
79. Save a private diagnostic snapshot.
80. Compare the current snapshot against the stored snapshot.

## Root development and recovery tools

81. Check root identity with an explicit `su -c id` test.
82. Check whether Samsung System UI is running and obtain its PID.
83. Inspect System UI's process owner.
84. Read Samsung's System UI package path.
85. Inspect Control Lab's installed APK path.
86. Read Control Lab's installed version metadata.
87. Calculate SHA-256 of the installed module APK.
88. Inspect recent Fold8-specific Xposed hook messages.
89. Find relevant clock-hook errors.
90. Inspect Vector's recent module load events.
91. Read recent System UI crash lines from Android logcat.
92. List installed KernelSU module directory names.
93. Read SELinux enforcement state.
94. Copy the emergency module-disable command without executing it.
95. Open the module's Android app-info/recovery screen.
96. Open Android developer options.
97. Open Android display settings.
98. Open Android battery-saver settings.
99. Open Android accessibility settings.
100. Open this app's notification settings.
101. Copy the last root diagnostic result.
102. Share the last root diagnostic result if the user chooses.

## v1.6 additions

103. Add seconds independently to the Quick Settings header/panel clock.
104. Optionally double-tap a shade clock to toggle its seconds; the existing status-bar gesture still toggles its bubble.
105. Enable optional haptic confirmation after either clock gesture.
106. Show optional AM/PM in 12-hour time; new options persist in looks/backups and old v15 imports remain supported.

**Safety scope:** The original v15 source and core hashes were verified before import. The v16 hook, provider and configuration are intentionally updated for these requested fixes; original pins remain in `tests/v15-baseline.sha256`, and reviewed new pins are separate. Existing styling helpers and appearance/diagnostic preference implementations were preserved. Nothing automatically installs, restarts SystemUI, modifies the kernel or edits system files. Existing root toolbox actions and the explicitly confirmed restart action require direct user taps. Backup deletion, preset overwrites, and restoration/import confirmation remain intact.

**Limits:** Depending on Samsung firmware, current/charge-counter readings or certain log files may be unavailable. The dashboard says so; it doesn't pretend unavailable readings are zero. The power estimate is battery-side, not a measurement of charger wattage. Real device compatibility and performance are pending on-phone build and testing.
