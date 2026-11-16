#!/usr/bin/env bash
# Prepare a phone-key-signed update from the downloaded ZIP; never install it.
set -euo pipefail
umask 077
fold8_fail() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
[[ "$#" -le 1 ]] || fold8_fail 'Usage: bash prepare-update-from-archives.sh [VERSION]'
fold8_version="${1:-18}"
[[ "$fold8_version" =~ ^[1-9][0-9]*$ ]] || fold8_fail 'VERSION must be a positive integer.'
fold8_downloads="${FOLD8_DOWNLOADS:-$HOME/storage/downloads}"
[[ -d "$fold8_downloads" ]] || fold8_fail 'Termux storage access is missing. Run termux-setup-storage, grant access, then retry.'
command -v unzip >/dev/null 2>&1 || fold8_fail 'unzip is missing. Run "pkg install unzip", then retry this script.'

fold8_archive=''
shopt -s nullglob
for fold8_candidate in "$fold8_downloads/archives"/Fold8-Control-Lab-v"$fold8_version"*.zip; do
    [[ -f "$fold8_candidate" ]] || continue
    if [[ -z "$fold8_archive" || "$fold8_candidate" -nt "$fold8_archive" ]]; then
        fold8_archive="$fold8_candidate"
    fi
done
[[ -n "$fold8_archive" ]] || fold8_fail "No Fold8-Control-Lab-v${fold8_version} ZIP found in $fold8_downloads/archives. Download that ZIP first."

fold8_work="$(mktemp -d)"
fold8_apk="$fold8_work/Fold8-Control-Lab-v${fold8_version}.apk"
fold8_helper="$fold8_work/sign-v${fold8_version}-with-phone-key.sh"
trap 'rm -f -- "$fold8_apk" "$fold8_helper"; rmdir -- "$fold8_work" 2>/dev/null || true' EXIT
printf 'Using archive: %s\n' "$fold8_archive"
unzip -p "$fold8_archive" "Fold8-Control-Lab-v${fold8_version}.apk" > "$fold8_apk" \
    || fold8_fail "ZIP could not supply its root Fold8-Control-Lab-v${fold8_version}.apk entry."
unzip -p "$fold8_archive" "sign-v${fold8_version}-with-phone-key.sh" > "$fold8_helper" \
    || fold8_fail "ZIP could not supply its root sign-v${fold8_version}-with-phone-key.sh entry."
[[ -s "$fold8_apk" && -s "$fold8_helper" ]] || fold8_fail 'ZIP contains an empty APK or signing helper.'

mkdir -p "$fold8_downloads/apps"
fold8_output="$fold8_downloads/apps/Fold8-Control-Lab-v${fold8_version}-update-$(date +%Y%m%d-%H%M%S)-$$.apk"
[[ ! -e "$fold8_output" ]] || fold8_fail "Output already exists; retry to select a new name: $fold8_output"
FOLD8_OUTPUT_APK="$fold8_output" bash "$fold8_helper" "$fold8_apk"
[[ -s "$fold8_output" ]] || fold8_fail 'Signing helper returned without saving a nonempty update APK.'
printf '\nReady for manual installation:\n%s\nNothing was installed or restarted.\n' "$fold8_output"
