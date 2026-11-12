#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?Set ANDROID_HOME to the SDK installation directory}"
command_tools_zip="${RUNNER_TEMP:-/tmp}/fold8-android-tools.zip"
curl --fail --location --retry 3 \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
  --output "$command_tools_zip"
printf '%s  %s\n' 2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258 "$command_tools_zip" | sha256sum --check --strict
mkdir -p "$ANDROID_HOME/cmdline-tools"
unzip -q "$command_tools_zip" -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/12.0"
# Supply enough license answers without a pipe that can fail with SIGPIPE.
python3 - <<'PY'
import os, subprocess
manager = os.path.join(os.environ['ANDROID_HOME'], 'cmdline-tools/12.0/bin/sdkmanager')
subprocess.run([manager, '--sdk_root=' + os.environ['ANDROID_HOME'], '--licenses'],
               input='y\n' * 100, text=True, check=True)
subprocess.run([manager, '--sdk_root=' + os.environ['ANDROID_HOME'],
                'platforms;android-35', 'build-tools;35.0.0', 'platform-tools'],
               input='y\n' * 100, text=True, check=True)
PY
