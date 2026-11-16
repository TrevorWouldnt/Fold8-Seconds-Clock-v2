#!/data/data/com.termux/files/usr/bin/bash
# Local builder for Fold8 Control Lab v15. Requires the previously working
# v6 toolchain; DOES NOT install, enable hooks, or modify any system files.
set -Eeuo pipefail
say(){ printf '\n[Fold8ControlLab v15] %s\n' "$*"; }
stop(){ printf '\n[Fold8ControlLab v15] STOP: %s\n' "$*" >&2; exit 1; }

: "${PREFIX:=/data/data/com.termux/files/usr}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$HOME/fold8-seconds-clock"
SDK35="$HOME/android-sdk/android.jar"
SDK34="$WORK/tools/android-34.jar"
R8="$WORK/tools/r8lib-8.6.17.jar"
KEY="$WORK/private-signing-key.jks"
JAVA_DIR="$PREFIX/lib/jvm/java-26-openjdk"
OUT="$ROOT/Fold8-Control-Lab-v15.apk"

for f in "$SDK35" "$SDK34" "$R8" "$KEY"; do
  [ -s "$f" ] || stop "Missing $f; don't rerun old installers."
done
for f in "$JAVA_DIR/bin/java" "$JAVA_DIR/bin/javac" "$JAVA_DIR/bin/jar" "$JAVA_DIR/bin/javap"; do
  [ -x "$f" ] || stop "Java 26 tool missing: $f"
done
for cmd in unzip zip sha256sum aapt2 aapt apksigner; do
  command -v "$cmd" >/dev/null || stop "Missing tool: $cmd"
done
unzip -tq "$SDK35" >/dev/null || stop "Invalid Android 35 SDK jar"
unzip -tq "$SDK34" >/dev/null || stop "Invalid Android 34 SDK jar"
unzip -tq "$R8" >/dev/null || stop "Invalid R8/D8 jar"
# Pin the reviewed module/provider/config sources to prevent accidental changes
# to a SystemUI hook during a rebuild.
(cd "$ROOT" && sha256sum -c tests/clock-core.sha256 >/dev/null) \
  || stop "Clock hook/provider files changed; refusing to build an unreviewed SystemUI modification."

export JAVA_HOME="$JAVA_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
TMP="$(mktemp -d "$ROOT/.build-v15-XXXXXXXX")" || stop "Cannot create a temporary directory"
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/classes" "$TMP/dex" "$TMP/package/assets"

say 'Compiling v15 Control Center with 50+ tools, v13 hook preserved...'
find "$ROOT/app/src/main/java" "$ROOT/xposed-api-stubs/src/main/java" -type f -name '*.java' -print0 | \
  xargs -0 "$JAVA_DIR/bin/javac" --release 8 -classpath "$SDK35" -d "$TMP/classes" \
  || stop 'Java compilation failed. Nothing installed.'
"$JAVA_DIR/bin/javap" -p "$TMP/classes/com/fold8/secondsclock/SecondsHook.class" | \
  grep -F 'handleLoadPackage' >/dev/null || stop 'Xposed entry missing from compiled code.'
"$JAVA_DIR/bin/jar" cf "$TMP/module.jar" -C "$TMP/classes" com/fold8/secondsclock
"$JAVA_DIR/bin/jar" cf "$TMP/stubs.jar" -C "$TMP/classes" de/robv/android/xposed

say 'Compiling the Android DEX with the newer Google D8 already on your phone...'
"$JAVA_DIR/bin/java" -cp "$R8" com.android.tools.r8.D8 --release --min-api 29 \
  --lib "$SDK35" --classpath "$TMP/stubs.jar" --output "$TMP/dex" "$TMP/module.jar" \
  || stop 'DEX compilation failed.'
[ -s "$TMP/dex/classes.dex" ] || stop 'DEX file missing.'

say 'Packaging a standalone APK using the cached Android 34 resource table...'
aapt2 link --manifest "$ROOT/app/src/main/AndroidManifest.xml" -I "$SDK34" \
  --min-sdk-version 29 --target-sdk-version 35 -o "$TMP/unsigned.apk" \
  || stop 'Android manifest packaging failed.'
cp "$TMP/dex/classes.dex" "$TMP/package/classes.dex"
cp "$ROOT/app/src/main/assets/xposed_init" "$TMP/package/assets/xposed_init"
(cd "$TMP/package" && zip -q "$TMP/unsigned.apk" classes.dex assets/xposed_init) \
  || stop 'Could not insert Android DEX/Xposed entry.'
if command -v zipalign >/dev/null 2>&1; then
  zipalign -f -p 4 "$TMP/unsigned.apk" "$TMP/aligned.apk" || stop 'zipalign failed.'
else
  cp "$TMP/unsigned.apk" "$TMP/aligned.apk"
fi

say 'Signing with your EXISTING private clock-module key...'
apksigner sign --ks "$KEY" --ks-key-alias fold8clock \
  --ks-pass pass:android --key-pass pass:android \
  --out "$TMP/signed.apk" "$TMP/aligned.apk" || stop 'APK signing failed.'
apksigner verify "$TMP/signed.apk" || stop 'APK signature verification failed.'
aapt dump badging "$TMP/signed.apk" | grep -F "package: name='com.fold8.secondsclock' versionCode='15'" \
  || stop 'Unexpected package name or version code.'
for member in classes.dex assets/xposed_init; do
  unzip -Z -1 "$TMP/signed.apk" | grep -Fx "$member" >/dev/null || stop "Missing $member"
done

# Confirm this really updates the installed v6, rather than creating a package
# with the same name but a different signing certificate.
say 'Checking signing certificate against the installed Control Lab APK...'
INSTALLED_APK="$(su -c 'pm path com.fold8.secondsclock' 2>/dev/null | sed -n 's/^package://p' | head -n 1)"
[ -n "$INSTALLED_APK" ] || stop 'The existing Fold8 Seconds Clock app was not found; refusing to assume it is safe to update.'
su -c "cat '$INSTALLED_APK'" > "$TMP/installed.apk" || stop 'Cannot read installed APK for certificate comparison.'
get_cert(){ apksigner verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1; }
OLD_CERT="$(get_cert "$TMP/installed.apk")"
NEW_CERT="$(get_cert "$TMP/signed.apk")"
[ -n "$OLD_CERT" ] && [ "$OLD_CERT" = "$NEW_CERT" ] \
  || stop 'Certificate mismatch or verification failed. No update saved.'

cp "$TMP/signed.apk" "$OUT" || stop 'Could not save APK.'
if [ -w "$HOME/storage/downloads" ]; then
  cp "$OUT" "$HOME/storage/downloads/Fold8-Control-Lab-v15.apk" || stop 'Could not copy to Downloads.'
fi
say 'BUILD AND SIGNATURE CHECKS PASSED; APK NOT INSTALLED.'
ls -lh "$OUT"
sha256sum "$OUT"
echo 'Saved to Downloads/Fold8-Control-Lab-v15.apk'
echo 'Keep your working v14 APK as backup. Before updating, disable the module in Vector and confirm your recovery path.'
