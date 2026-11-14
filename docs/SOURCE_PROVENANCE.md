# Correct starting point

The owner clarified that Fold8 Control Lab v15, rather than the small v0.3
module, is the intended baseline. Original supplied files were inspected and
preserved outside the checkout. Their embedded/extracted source hashes matched.

- Source ZIP SHA-256: `e4c4e03b5a7344f6fb6c45f1808eb6c563fe582f6568025305e2cd90c224bc16`.
- Both supplied v15 Termux scripts SHA-256:
  `45c27b2d69fdf589916ef313aadd3dd34938d169821638b65429baaaed45b5a4`.
- Original custom v15 APK SHA-256:
  `08281e173fedfcc5d760c641a7324506ef20c3ffcd784903c7c11d3938e33824`.
- Original package `com.fold8.secondsclock`, version code 15/name 1.5.
- Original certificate SHA-256:
  `619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf`.
- No signing private key was supplied, extracted, copied or recreated.

The source import matched all four original pinned core hashes before edits.
The original pins remain in `tests/v15-baseline.sha256`. User-requested hook,
configuration/provider and gesture fixes intentionally change those sources;
the reviewed v16 pins are kept separately in `tests/clock-core.sha256`.

Font scaling, contrast calculation, bubble drawable creation, full padding,
inset styling and restoration helpers were compared against v15 and remain
byte-identical. AppearancePrefs.java, LabDiagnostics.java and LabUiPrefs.java
also remain byte-identical. UI, JSON backups and looks were extended rather
than replaced. The existing root toolbox and explicit confirmed restart action
were not executed during cloud development or tests.

No proprietary Samsung SystemUI binary is committed. Its previous signature
notes remain references; public older Samsung sources only identify optional
shade hook names, not confirmed One UI 9 behavior. The original v15 docs are
preserved verbatim as V15_README.md and V15_FEATURES.md.
