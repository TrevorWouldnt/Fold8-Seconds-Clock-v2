#!/data/data/com.termux/files/usr/bin/bash
# Signing only. Never compiles, installs, reboots, or generates/replaces a key.
set -euo pipefail
fail() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
clock_input_apk="${1:-$HOME/storage/downloads/Fold8-Control-Lab-v16.apk}"
clock_key="$HOME/fold8-seconds-clock/private-signing-key.jks"
clock_output_apk="$HOME/storage/downloads/Fold8-Control-Lab-v16-update.apk"
clock_expected_input='__APK_SHA256__'
clock_expected_cert='619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf'
for tool in apksigner aapt sha256sum sed; do command -v "$tool" >/dev/null || fail "Missing existing tool: $tool"; done
[ -f "$clock_input_apk" ] || fail "Download the v16 APK first, or pass its path as the argument."
[ -f "$clock_key" ] || fail 'Original phone signing key not found. Keep v15 installed; no key was changed.'
[ ! -e "$clock_output_apk" ] || fail 'An update APK already exists in Downloads. Move it aside before retrying.'
[[ "$clock_expected_input" =~ ^[0-9a-f]{64}$ ]] || fail 'Use the checksum-pinned helper from the Actions artifact.'
clock_actual_input="$(sha256sum "$clock_input_apk")"
[ "${clock_actual_input%% *}" = "$clock_expected_input" ] || fail 'Input APK checksum does not match this helper.'
apksigner verify "$clock_input_apk" || fail 'Input APK signature is invalid.'
clock_badging="$(aapt dump badging "$clock_input_apk")"
[[ "$clock_badging" == *"package: name='com.fold8.secondsclock' versionCode='16'"* ]] || fail 'Wrong app/version.'
clock_sign_dir="$(mktemp -d)"
trap 'rm -f -- "$clock_sign_dir/signed.apk" "$clock_sign_dir/signed.apk.idsig"; rmdir -- "$clock_sign_dir" 2>/dev/null || true' EXIT
apksigner sign --ks "$clock_key" --ks-key-alias fold8clock \
    --ks-pass pass:android --key-pass pass:android \
    --out "$clock_sign_dir/signed.apk" "$clock_input_apk"
apksigner verify "$clock_sign_dir/signed.apk" || fail 'Signed update verification failed.'
clock_actual_cert="$(apksigner verify --print-certs "$clock_sign_dir/signed.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[ "$clock_actual_cert" = "$clock_expected_cert" ] || fail 'Certificate does not match the supplied v15 APK. No update was saved.'
cp -- "$clock_sign_dir/signed.apk" "$clock_output_apk"
printf 'Verified v15-compatible update saved: %s\nNothing was installed or restarted.\n' "$clock_output_apk"
