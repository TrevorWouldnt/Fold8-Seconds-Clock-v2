#!/data/data/com.termux/files/usr/bin/bash
# Signing only. No compiler, installer, root, reboot, package download or new key.
set -euo pipefail
fail() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
clock_script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
clock_prefix="${PREFIX:-/data/data/com.termux/files/usr}"
clock_input_apk="${1:-$HOME/storage/downloads/apps/Fold8-Control-Lab-v16.apk}"
if [ "$#" -eq 0 ] && [ ! -f "$clock_input_apk" ]; then
    clock_input_apk="$HOME/storage/downloads/Fold8-Control-Lab-v16.apk"
fi
clock_key="${FOLD8_KEYSTORE:-$HOME/fold8-seconds-clock/private-signing-key.jks}"
clock_expected_input='__APK_SHA256__'
clock_expected_signer='00ef9948f843fe395d2440ae3ef41405b8040a6d5d46493bd1902ac0ee6deae7'
clock_expected_cert='619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf'
for tool in sha256sum sed tail base64 mktemp cp; do
    command -v "$tool" >/dev/null || fail "Missing existing tool: $tool"
done
[ -f "$clock_input_apk" ] || fail 'APK not found. Pass its exact path as the first argument.'
clock_input_dir="$(cd -- "$(dirname -- "$clock_input_apk")" && pwd)"
clock_output_apk="${FOLD8_OUTPUT_APK:-$clock_input_dir/Fold8-Control-Lab-v16-update.apk}"
[ ! -e "$clock_output_apk" ] || fail 'An update APK already exists there. Move it aside before retrying.'
[[ "$clock_expected_input" =~ ^[0-9a-f]{64}$ ]] || fail 'Download the packaged, checksum-pinned helper.'
clock_actual_input="$(sha256sum "$clock_input_apk")"
[ "${clock_actual_input%% *}" = "$clock_expected_input" ] || fail 'Input APK checksum does not match this helper.'

# Never fall back to a PATH-selected java/app_process/dalvikvm wrapper.
clock_java="${FOLD8_JAVA:-}"
if [ -z "$clock_java" ]; then
    for clock_jdk in java-26-openjdk java-21-openjdk java-17-openjdk; do
        if [ -x "$clock_prefix/lib/jvm/$clock_jdk/bin/java" ]; then
            clock_java="$clock_prefix/lib/jvm/$clock_jdk/bin/java"
            break
        fi
    done
fi
[ -n "$clock_java" ] && [ -x "$clock_java" ] || fail 'Existing OpenJDK runtime not found. No key or APK was changed.'
clock_sign_dir="$(mktemp -d)"
cleanup() {
    rm -f -- "$clock_sign_dir/apksigner.jar" "$clock_sign_dir/java.log" \
        "$clock_sign_dir/signer.log" "$clock_sign_dir/signed.apk" "$clock_sign_dir/signed.apk.idsig"
    rmdir -- "$clock_sign_dir" 2>/dev/null || true
}
trap cleanup EXIT

# This distribution embeds Google's SDK35 pure-Java signer with an independent pin.
clock_signer_jar="${FOLD8_SIGNER_JAR:-}"
if [ -z "$clock_signer_jar" ]; then
    if [ "$(sed -n '/^__FOLD8_SIGNER_JAR__$/=' "$0")" ]; then
        sed -n '/^__FOLD8_SIGNER_JAR__$/,$p' "$0" | tail -n +2 | base64 -d > "$clock_sign_dir/apksigner.jar" \
            || fail 'Embedded signer could not be decoded.'
        clock_signer_jar="$clock_sign_dir/apksigner.jar"
    elif [ -f "$clock_script_dir/apksigner-v35.jar" ]; then
        clock_signer_jar="$clock_script_dir/apksigner-v35.jar"
    else
        clock_signer_jar="$clock_prefix/share/java/apksigner.jar"
    fi
fi
[ -f "$clock_signer_jar" ] || fail 'Signer JAR missing. Download the complete fixed helper.'
clock_actual_signer="$(sha256sum "$clock_signer_jar")"
[ "${clock_actual_signer%% *}" = "$clock_expected_signer" ] || fail 'Signer JAR checksum mismatch. Download the complete fixed helper.'

clock_java_command() (
    # Only the child is sanitized; the user's Termux configuration is unchanged.
    unset JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS CLASSPATH
    export JAVA_HOME="${clock_java%/bin/java}"
    "$clock_java" "$@"
)
clock_signer() { clock_java_command -jar "$clock_signer_jar" "$@"; }
if ! clock_java_command -version > "$clock_sign_dir/java.log" 2>&1; then
    tail -n 12 "$clock_sign_dir/java.log" >&2
    fail 'OpenJDK did not start. APK verification has not run; no key or APK was changed.'
fi
if ! clock_signer version > "$clock_sign_dir/signer.log" 2>&1; then
    tail -n 12 "$clock_sign_dir/signer.log" >&2
    fail 'Signer did not start. APK verification has not run; no key or APK was changed.'
fi
printf 'Using OpenJDK directly: %s\n' "$clock_java"
if clock_signer verify "$clock_input_apk" > "$clock_sign_dir/signer.log" 2>&1; then :; else
    clock_verify_status=$?
    tail -n 12 "$clock_sign_dir/signer.log" >&2
    if [ "$clock_verify_status" -ge 128 ]; then
        fail 'Signer crashed before completing verification. Keep v15 installed; the key was not touched.'
    fi
    fail 'Signer could not verify this APK. See the error above; no signing was attempted.'
fi
[ -f "$clock_key" ] || fail 'Original phone signing key not found. Keep v15 installed; no key was changed.'
if ! clock_signer sign --ks "$clock_key" --ks-key-alias fold8clock \
        --ks-pass pass:android --key-pass pass:android \
        --out "$clock_sign_dir/signed.apk" "$clock_input_apk" > "$clock_sign_dir/signer.log" 2>&1; then
    tail -n 12 "$clock_sign_dir/signer.log" >&2
    fail 'Signing failed. The existing key and v15 installation were not changed.'
fi
if ! clock_signer verify --print-certs "$clock_sign_dir/signed.apk" > "$clock_sign_dir/signer.log" 2>&1; then
    tail -n 12 "$clock_sign_dir/signer.log" >&2
    fail 'Signed update verification did not complete. No update was saved.'
fi
clock_actual_cert="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$clock_sign_dir/signer.log")"
[ "$clock_actual_cert" = "$clock_expected_cert" ] || fail 'Certificate does not match the supplied v15 APK. No update was saved.'
cp -- "$clock_sign_dir/signed.apk" "$clock_output_apk"
printf 'Verified v15-compatible update saved: %s\nNothing was installed or restarted.\n' "$clock_output_apk"
exit 0
