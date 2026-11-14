#!/usr/bin/env python3
"""Source/packaging assertions; does NOT establish runtime feature compatibility."""
import re,hashlib,subprocess
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parent.parent
app=root/'app/src/main'
pkg=app/'java/com/fold8/secondsclock'
manifest=ET.parse(app/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
gradle=(root/'app/build.gradle').read_text()
assert "namespace 'com.fold8.secondsclock'" in gradle
assert "applicationId 'com.fold8.secondsclock'" in gradle
assert manifest.attrib[ns+'versionCode']=='16'
assert manifest.attrib[ns+'versionName']=='1.6'
assert any(x.attrib.get(ns+'name')=='android.permission.ACCESS_NETWORK_STATE' for x in manifest.findall('uses-permission'))
assert not any(x.attrib.get(ns+'name')=='android.permission.INTERNET' for x in manifest.findall('uses-permission'))
activities=manifest.find('application').findall('activity')
assert any(x.attrib.get(ns+'name')=='com.fold8.secondsclock.LabHubActivity' and
           x.attrib.get(ns+'exported')=='false' for x in activities)
assert any(x.attrib.get(ns+'name')=='com.fold8.secondsclock.ControlLabActivity' for x in activities)
assert (app/'assets/xposed_init').read_text().strip()=='com.fold8.secondsclock.SecondsHook'
subprocess.run(['sha256sum','-c','tests/clock-core.sha256'],cwd=root,check=True,stdout=subprocess.DEVNULL)
ui=(pkg/'ControlLabActivity.java').read_text()
hub=(pkg/'LabHubActivity.java').read_text()
diag=(pkg/'LabDiagnostics.java').read_text()
json=(pkg/'LabData.java').read_text()
style=(pkg/'LabUiPrefs.java').read_text()
assert all(x in ui for x in ['LabHubActivity.class','extraUi.stickyPreview','extraUi.oledBlack','extraUi.compact','extraUi.cardOpacity','extraUi.textScale','onActivityResult'])
assert all(x in hub for x in ['ClockConfig.loadRemote','LabData.parseClock','LabUiPrefs.parse','startActivity','ProcessBuilder("su"','waitFor(9, TimeUnit.SECONDS)',
    'Battery', 'restore', 'rootTaskId', 'Settings.ACTION_APPLICATION_DETAILS_SETTINGS'])
assert all(x in json for x in ['JSONObject','inRange','clockJson','parseClock','parseAppearance','commit()'])
assert 'getSharedPreferences(FILE,Context.MODE_PRIVATE)' in style
assert 'android.os.BatteryManager' in diag
assert 'PowerManager' in diag and 'NetworkCapabilities' in diag
assert diag.count('add(out,')>=35,diag.count('add(out,')
assert hub.count('button(')-1>=50,hub.count('button(')
assert hub.count('toggle(')-1>=5
assert hub.count('inputNumber(')-1>=4
catalog=(root/'FEATURES.md').read_text()
items=re.findall(r'^\d+\. ',catalog,re.M)
assert len(items)>=50,len(items)
assert not (app/'AndroidManifest.xml').read_text().count('<uses-permission')>1
assert all(x in ui for x in ['shadeSeconds','doubleTapShade','gestureHaptic','showAmPm'])
assert all(x in json for x in ['shadeSeconds','doubleTapShade','gestureHaptic','showAmPm'])
print('PASS: v16 preserves v15 Control Center, existing diagnostic controls, safe manifest and extended backups; Android/runtime behavior is verified separately.')
print('Action buttons:',hub.count('button(')-1,'new switches:',hub.count('toggle(')-1,
      'numeric controls:',hub.count('inputNumber(')-1,'readings:',diag.count('add(out,'),'catalog entries:',len(items))
