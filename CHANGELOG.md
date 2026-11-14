# v1.6 · Window gestures and shade clock

- Continue from the supplied v15 Control Lab source, preserving its full UI,
  styles, looks, backups, diagnostics and root tools.
- Replace child-view touch listeners with window-level double-tap observation;
  retain status-bar bubble toggles and add opt-in shade seconds gestures.
- Add independent shade seconds, gesture haptics and AM/PM, with backward-compatible backups.
- Share the ticker, stop on screen-off/hidden clocks, cache settings and move
  provider I/O off the UI thread; restore original styling on detach.
- Add Android behavior tests, lint/build checks, source provenance, Actions
  artifacts and a signing-only helper for the owner's original phone key.

# v1.5 · Control Center Edition

- Added Control Center navigation in Clock Studio.
- Added dedicated Clock, Themes, Device, and Root sections.
- Added strict JSON backups, import validation, sharing, diffing, rollback, and six additional named looks C–H.
- Added theme personalization controls: custom accents, accessibility contrast, AMOLED black, layout density, pinned preview, haptic feedback, typography, transparency, glow and radius.
- Added a 39-value read-only device diagnostics dashboard, including battery-side power measurements, temperatures, storage, display, and network status.
- Added private diagnostic history, comparison, clipboard copy and Android sharing.
- Added root/Vector diagnostics and safe recovery shortcuts with no automatic root execution.
- Preserved original v1.3 System UI hooks, settings provider, Xposed entry and v1.4 core previews without modifications.

See `FEATURES.md` for the auditable detailed catalog and limitations.
