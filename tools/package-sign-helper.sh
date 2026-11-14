#!/usr/bin/env bash
# Package a pinned phone helper without rebuilding or modifying the APK.
set -euo pipefail
[ "$#" -eq 3 ] || { printf 'Usage: %s APK SDK35_SIGNER_JAR OUTPUT_SCRIPT\n' "$0" >&2; exit 2; }
clock_package_apk="$1"
clock_package_jar="$2"
clock_package_output="$3"
clock_package_tools="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
clock_package_jar_sha="$(sha256sum "$clock_package_jar")"
[ "${clock_package_jar_sha%% *}" = '00ef9948f843fe395d2440ae3ef41405b8040a6d5d46493bd1902ac0ee6deae7' ] \
    || { printf 'Signer JAR checksum mismatch; no helper written.\n' >&2; exit 1; }
clock_package_apk_sha="$(sha256sum "$clock_package_apk")"
clock_package_temp="$(mktemp -d)"
trap 'rm -f -- "$clock_package_temp/helper.sh"; rmdir -- "$clock_package_temp"' EXIT
sed "s/__APK_SHA256__/${clock_package_apk_sha%% *}/" \
    "$clock_package_tools/sign-v16-with-phone-key.sh" > "$clock_package_temp/helper.sh"
printf '\n__FOLD8_SIGNER_JAR__\n' >> "$clock_package_temp/helper.sh"
base64 "$clock_package_jar" >> "$clock_package_temp/helper.sh"
bash -n "$clock_package_temp/helper.sh"
cp -- "$clock_package_temp/helper.sh" "$clock_package_output"
printf 'Packaged helper for APK SHA-256 %s\n' "${clock_package_apk_sha%% *}"
