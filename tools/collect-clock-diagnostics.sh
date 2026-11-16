#!/usr/bin/env bash
# Read-only phone diagnostics. Run in Termux; root only reads selected data.
# Writes this report and private temporary files, never device/app settings.
set -euo pipefail

fold8_downloads="$HOME/storage/downloads"
[[ -d "$fold8_downloads" ]] || {
    printf 'STOP: Termux storage access is missing. Run termux-setup-storage, grant access, then retry.\n' >&2
    exit 1
}
command -v su >/dev/null 2>&1 || {
    printf 'STOP: su is unavailable in Termux. This report needs your existing root access.\n' >&2
    exit 1
}
fold8_report_dir="$fold8_downloads/Fold8-Diagnostics"
mkdir -p "$fold8_report_dir"
fold8_report="$fold8_report_dir/Fold8-clock-$(date +%Y%m%d-%H%M%S)-$$.txt"
fold8_work="$(mktemp -d)"
trap 'rm -rf -- "$fold8_work"' EXIT

cat > "$fold8_work/collect.sh" <<'ANDROID'
#!/system/bin/sh
PATH=/system/bin:/system/xbin:/vendor/bin
export PATH
# Affect this diagnostic child only; Android tools need their system runtime.
unset CLASSPATH JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS LD_PRELOAD LD_LIBRARY_PATH

section() { printf '\n===== %s =====\n' "$1"; }
limited() {
    if command -v timeout >/dev/null 2>&1; then timeout 12 "$@"; else "$@"; fi
}
module_lines() {
    # Keep Java causes/frames beside a matching module record, rather than
    # dumping arbitrary neighbouring log messages from other applications.
    awk '
        function trace(line) {
            if (tolower(line) !~ /vector|lsposed|lspd|xposed/ &&
                    line !~ /^[[:space:]]*(Caused by:|Suppressed:|at[[:space:]]|[[:alnum:]_.$]+(Exception|Error)|\.\.\.)/)
                return 0
            return line ~ /(^|[[:space:]:])(Caused by:|Suppressed:|at[[:space:]]+[[:alnum:]_.$<>]+\(|[[:alnum:]_.$]+(Exception|Error)(:|[[:space:]]|$)|\.\.\.[[:space:]]+[0-9]+[[:space:]]+more)/
        }
        function emit(number, line) {
            if (number > emitted) { print line; emitted = number }
        }
        {
            if (tolower($0) ~ /fold8controllab|com[.\/]fold8[.\/]secondsclock|fold8 control lab/) {
                first = NR - 1
                while (first > 0 && first >= NR - 8 && trace(previous[first % 8])) first--
                for (i = first + 1; i < NR; i++) emit(i, previous[i % 8])
                emit(NR, $0)
                remaining = 12
            } else if (remaining > 0) {
                if (trace($0)) emit(NR, $0); else remaining = 0
                remaining--
            }
            previous[NR % 8] = $0
        }
    ' | tail -n 160
}

section 'Fold8 clock diagnostics v1; read-only'
date -u
printf 'Diagnostic UID: '; id -u
if [ "$(id -u)" != 0 ]; then
    printf 'STOP: Root was not granted; package, preference and framework reads would be incomplete.\n'
    exit 1
fi
printf 'Device: '; getprop ro.product.model
printf 'Android: '; getprop ro.build.version.release
printf 'Firmware: '; getprop ro.build.display.id
printf 'Time zone: '; getprop persist.sys.timezone
printf 'Uptime seconds: '; cut -d ' ' -f 1 /proc/uptime
fold8_user="$(limited cmd activity get-current-user 2>/dev/null)"
case "$fold8_user" in ''|*[!0-9]*) fold8_user=0;; esac
printf 'Active Android user: %s\n' "$fold8_user"

section 'Installed Control Lab package (expected versionCode=18, versionName=1.8)'
limited dumpsys package com.fold8.secondsclock 2>&1 |
    grep -E 'Unable|not found|codePath=|resourcePath=|versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|userId=|User [0-9]+:'
fold8_package_paths="$(limited cmd package path --user "$fold8_user" com.fold8.secondsclock 2>&1)"
printf '%s\n' "$fold8_package_paths"
printf '%s\n' "$fold8_package_paths" |
while IFS= read -r fold8_path; do
    case "$fold8_path" in
        package:*/base.apk)
            fold8_apk="${fold8_path#package:}"
            printf 'Installed APK checksum:\n'
            sha256sum "$fold8_apk" 2>&1
            for fold8_unzip in /system/bin/unzip /data/data/com.termux/files/usr/bin/unzip; do
                if [ -x "$fold8_unzip" ]; then
                    printf 'Packaged Xposed entry:\n'
                    limited "$fold8_unzip" -p "$fold8_apk" assets/xposed_init 2>&1
                    break
                fi
            done
            ;;
    esac
done

section 'Live read-only settings-provider query'
limited /system/bin/content query --user "$fold8_user" \
    --uri content://com.fold8.secondsclock.settings/config 2>&1
printf 'Provider query exit status: %s (124 means timeout)\n' "$?"

section 'Saved clock preferences (numbers and booleans only)'
for fold8_area in user_de user; do
    fold8_pref="/data/$fold8_area/$fold8_user/com.fold8.secondsclock/shared_prefs/fold8_control_lab.xml"
    printf '\n%s\n' "$fold8_pref"
    if [ -r "$fold8_pref" ]; then
        ls -l "$fold8_pref"
        grep -E '<(boolean|int|long|float)[[:space:]]' "$fold8_pref" || true
    else
        printf 'Not present/readable. Device-protected user_de is the expected location.\n'
    fi
done

section 'Main SystemUI process and selected module/framework mappings'
fold8_pids="$(pidof com.android.systemui 2>/dev/null)"
if [ -z "$fold8_pids" ]; then printf 'No main SystemUI PID found.\n'; fi
for fold8_pid in $fold8_pids; do
    printf '\nSystemUI PID %s\n' "$fold8_pid"
    grep -E '^(Name|State|Pid|PPid|Uid|Threads|VmRSS):' "/proc/$fold8_pid/status" 2>/dev/null
    awk '{print "Process start ticks since boot: " $22}' "/proc/$fold8_pid/stat" 2>/dev/null
    printf 'Matching loaded paths (absence alone does not prove the hook is missing):\n'
    grep -Ei 'fold8|secondsclock|vector|lsposed|lspd|xposed|lsplant' "/proc/$fold8_pid/maps" 2>/dev/null |
        awk '{print $NF}' | sort -u | head -n 50
done

section 'Current power state'
limited dumpsys power 2>/dev/null |
    grep -E 'mWakefulness=|mInteractive=|mIsPowered=|mDisplayReady=' | head -n 12

section 'Recent Android log buffers; only Fold8 module matches'
limited logcat -d -b all -v threadtime -t 10000 2>/dev/null | module_lines
printf '(No matching lines is inconclusive; Vector may keep its own log files.)\n'

section 'Installed root-framework metadata; matching modules only'
fold8_roots='/data/adb/lspd
/data/adb/lsposed
/data/adb/vector'
for fold8_prop in /data/adb/modules/*/module.prop; do
    [ -r "$fold8_prop" ] || continue
    if grep -Eiq '^(id|name)=.*(vector|lsposed|lspd|xposed|zygisk)' "$fold8_prop"; then
        printf '\n%s\n' "$fold8_prop"
        grep -E '^(id|name|version|versionCode)=' "$fold8_prop"
        fold8_root="${fold8_prop%/module.prop}"
        [ ! -f "$fold8_root/disable" ] || printf 'Root-module disable marker is present.\n'
        [ ! -f "$fold8_root/remove" ] || printf 'Root-module remove marker is present.\n'
        # Zygisk modules' unrelated private files are not scanned.
        if grep -Eiq '^(id|name)=.*(vector|lsposed|lspd|xposed)' "$fold8_prop"; then
            fold8_roots="$fold8_roots
$fold8_root"
        fi
    fi
done

fold8_sqlite=''
for fold8_cli in /system/bin/sqlite3 /data/data/com.termux/files/usr/bin/sqlite3; do
    if [ -x "$fold8_cli" ]; then fold8_sqlite="$fold8_cli"; break; fi
done
fold8_discovered="$(limited find /data/adb -maxdepth 2 -type d \
    \( -iname '*vector*' -o -iname '*lsposed*' -o -iname '*lspd*' \) 2>/dev/null | head -n 12)"
fold8_roots="$fold8_roots
$fold8_discovered"

section 'Vector/LSPosed clock logs and module scope; candidate locations only'
printf '%s\n' "$fold8_roots" | sort -u | head -n 12 |
while IFS= read -r fold8_root; do
    [ -d "$fold8_root" ] || continue
    printf '\nFramework directory: %s\n' "$fold8_root"
    limited find "$fold8_root" -maxdepth 4 -type f \
        \( -name '*.log' -o -name '*.txt' \) 2>/dev/null | sort -r | head -n 6 |
    while IFS= read -r fold8_log; do
        printf '\nFiltered log: %s\n' "$fold8_log"
        tail -n 10000 "$fold8_log" 2>/dev/null | module_lines
        printf 'Startup markers (may be from an earlier SystemUI process):\n'
        limited grep -Ei 'Fold8ControlLab.*(package callback|first v[0-9]+ clock paint)' \
            "$fold8_log" 2>/dev/null | head -n 16
    done
    limited find "$fold8_root" -maxdepth 4 -type f \
        -name '*config*.db' 2>/dev/null | sort | head -n 2 |
    while IFS= read -r fold8_db; do
        printf '\nPossible scope database: %s\n' "$fold8_db"
        if [ -n "$fold8_sqlite" ]; then
            # SQLite can create shared-memory files even with -readonly. Query
            # a private copy plus WAL so the live framework directory is untouched.
            fold8_snapshot="$(mktemp -d "${0%/*}/scope.XXXXXX")" || continue
            fold8_owner="$(stat -c %u "$0")"
            chown "$fold8_owner" "$fold8_snapshot" 2>/dev/null || true
            if ! cp "$fold8_db" "$fold8_snapshot/scope.db"; then
                rmdir "$fold8_snapshot" 2>/dev/null
                continue
            fi
            if [ -f "$fold8_db-wal" ]; then cp "$fold8_db-wal" "$fold8_snapshot/scope.db-wal"; fi
            printf 'Querying a private best-effort DB/WAL snapshot; concurrent writes may affect consistency.\n'
            limited "$fold8_sqlite" -readonly "$fold8_snapshot/scope.db" \
                "SELECT sql FROM sqlite_master WHERE type='table' AND name IN ('modules','scope');" 2>&1
            limited "$fold8_sqlite" -readonly "$fold8_snapshot/scope.db" \
                "SELECT m.module_pkg_name,m.enabled,m.apk_path,s.app_pkg_name,s.user_id FROM modules m LEFT JOIN scope s ON s.mid=m.mid WHERE m.module_pkg_name='com.fold8.secondsclock' LIMIT 20;" 2>&1
            rm -f "$fold8_snapshot/scope.db" "$fold8_snapshot/scope.db-wal" "$fold8_snapshot/scope.db-shm"
            rmdir "$fold8_snapshot"
        else
            printf 'SQLite CLI unavailable: scope was not read; no packages were installed.\n'
        fi
    done
done

section 'Finished'
printf 'No preferences, scope, module state, logs or system files were changed.\n'
printf 'Missing log entries/mappings alone cannot establish that injection failed.\n'
ANDROID

printf 'Collecting read-only diagnostics. Grant Termux root access if prompted.\n'
printf -v fold8_root_command '%q ' /system/bin/sh "$fold8_work/collect.sh"
if su -c "$fold8_root_command" > "$fold8_report" 2>&1; then
    printf '\nReport saved:\n%s\n\nAttach this .txt file in the chat.\n' "$fold8_report"
else
    printf '\nDiagnostics were incomplete. Report saved:\n%s\n\n' "$fold8_report" >&2
    tail -n 12 "$fold8_report" >&2
    exit 1
fi
