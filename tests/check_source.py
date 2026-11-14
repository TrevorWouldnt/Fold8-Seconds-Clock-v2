#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess
root=Path(__file__).resolve().parent.parent
app=root/'app/src/main'
a='{http://schemas.android.com/apk/res/android}'
m=ET.parse(app/'AndroidManifest.xml').getroot()
gradle=(root/'app/build.gradle').read_text()
assert "namespace 'com.fold8.secondsclock'" in gradle
assert "applicationId 'com.fold8.secondsclock'" in gradle
assert m.attrib[a+'versionCode']=='16'
assert "versionCode 16" in gradle
assert (app/'assets/xposed_init').read_text().strip()=='com.fold8.secondsclock.SecondsHook'
activity=(app/'java/com/fold8/secondsclock/ControlLabActivity.java').read_text()
for token in ('pinnedPreview','previewLightBackground','bubbleAutoSwitch','WindowInsets.Type.statusBars()', 'bubbleInsetSeek','confirmApplyAndRestart','previewTick','addPresetRow','LocalPresets.load','copyClockSummary','LabHubActivity.class'):
 assert token in activity, token
hook=(app/'java/com/fold8/secondsclock/SecondsHook.java').read_text()
for token in ('needsWallpaperContrast','wallpaper', 'InsetDrawable','desiredSizePx','backgroundMatches','expectedLeft','styleBubble'):
 assert token.lower() in hook.lower(),token
config=(app/'java/com/fold8/secondsclock/ClockConfig.java').read_text()
for token in ('bubbleAuto','bubbleBorder','bubbleInset','putBoolean("bubbleAuto", false)',
              'shadeSeconds','doubleTapShade','gestureHaptic','showAmPm','toggleShadeRemote'):
 assert token in config,token
assert 'Binder.getCallingUid()' in (app/'java/com/fold8/secondsclock/ClockSettingsProvider.java').read_text()
# Guard against the previous silent padding clamp, in both live view and preview.
assert 'Math.min(c.bubblePadding' not in hook
assert 'Math.min(bubblePaddingSeek.getProgress()+2' not in activity
assert 'clock.setBackground(bubble);\n            clock.setPadding(expectedLeft' in hook
assert 'int pad=dp(bubblePaddingSeek.getProgress()+2)' in activity
subprocess.run(['sha256sum','-c','tests/clock-core.sha256'],cwd=root,check=True,stdout=subprocess.DEVNULL)
assert 'DateFormat.format(pattern,System.currentTimeMillis())' in activity
assert 'LocalPresets.save(this,slot,config)' in activity
assert 'ClockConfig.save(this, stored)' not in activity
assert 'MODE_PRIVATE' in (app/'java/com/fold8/secondsclock/LocalPresets.java').read_text()
print('PASS: v16 source contract preserves v15 clock styling and Control Center; Android/runtime behavior is verified separately.')
