#!/usr/bin/env python3
"""Run the phone signing helper against isolated signer/runtime fixtures.

The fixture never uses a real signing key or Android runtime. It exercises the
shell's path selection, integrity checks, failure handling and output rules.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
import zipfile


ROOT = Path(__file__).resolve().parent.parent
HELPER = ROOT / "tools/sign-v16-with-phone-key.sh"
EXPECTED_CERT = "619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf"
SDK_SIGNER_SHA256 = "00ef9948f843fe395d2440ae3ef41405b8040a6d5d46493bd1902ac0ee6deae7"
JAVA_INJECTIONS = ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH")


class SignHelperTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="fold8 signing test ")
        self.work = Path(self.directory.name)
        self.apps = self.work / "downloads/apps"
        self.apps.mkdir(parents=True)
        self.apk = self.apps / "Fold8-Control-Lab-v16.apk"
        self.apk.write_bytes(b"verified test APK input\n")
        self.key = self.work / "existing phone private key.jks"
        self.key_bytes = b"test fixture key: must never be overwritten\n"
        self.key.write_bytes(self.key_bytes)
        self.output = self.apps / "Fold8-Control-Lab-v16-update.apk"
        self.jar = self.work / "apksigner-v35.jar"
        self.jar.write_bytes(b"test fixture signer jar\n")
        self.log = self.work / "java-calls.jsonl"
        self.prefix = self.work / "termux prefix"
        self.bin = self.prefix / "bin"
        self.bin.mkdir(parents=True)
        self.java = self.prefix / "lib/jvm/java-26-openjdk/bin/java"
        self.java.parent.mkdir(parents=True)
        self.write_executable(self.java, self.fake_java())
        # PATH wrappers are deliberately broken. The helper must pick the full
        # OpenJDK path and launch the verified JAR, never either wrapper.
        for name in ("java", "apksigner"):
            self.write_executable(self.bin / name,
                                  "#!/bin/sh\nprintf 'WRONG PATH WRAPPER\\n' >&2\nexit 97\n")
        self.write_executable(self.bin / "aapt",
                              "#!/bin/sh\nprintf 'UNEXPECTED AAPT WRAPPER\\n' >&2\nexit 97\n")
        self.helper = self.work / "sign-v16-with-phone-key.sh"
        self.render_helper()
        self.environment = os.environ.copy()
        self.environment.update({
            "PREFIX": str(self.prefix),
            "FOLD8_KEYSTORE": str(self.key),
            "FOLD8_OUTPUT_APK": str(self.output),
            "FOLD8_TEST_LOG": str(self.log),
            "FOLD8_TEST_CERT": EXPECTED_CERT,
            "PATH": str(self.bin) + os.pathsep + self.environment.get("PATH", ""),
            "TMPDIR": str(self.work),
        })
        # Keep the real HOME intact. The production helper exposes narrow path
        # selectors so fixtures do not need to masquerade as a user's home.
        self.environment.pop("FOLD8_JAVA", None)
        self.environment.pop("FOLD8_SIGNER_JAR", None)
        for name in JAVA_INJECTIONS:
            self.environment[name] = "injected-test-value-must-not-reach-java"

    def tearDown(self):
        if self.key.exists():
            self.assertEqual(self.key_bytes, self.key.read_bytes(), "Existing signing key changed")
        self.directory.cleanup()

    @staticmethod
    def write_executable(path, contents):
        path.write_text(contents)
        path.chmod(0o700)

    def render_helper(self, apk_hash=None, jar_hash=None, app_version=16):
        contents = HELPER.read_text()
        contents = contents.replace("__APK_SHA256__", apk_hash or hashlib.sha256(self.apk.read_bytes()).hexdigest())
        contents = contents.replace(SDK_SIGNER_SHA256, jar_hash or hashlib.sha256(self.jar.read_bytes()).hexdigest())
        contents = contents.replace("__APP_VERSION__", str(app_version))
        self.helper.write_text(contents)

    def fake_java(self):
        return "#!" + sys.executable + "\n" + textwrap.dedent("""\
            import json, os, pathlib, sys
            args = sys.argv[1:]
            injections = [key for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH') if key in os.environ]
            with open(os.environ['FOLD8_TEST_LOG'], 'a') as log:
                log.write(json.dumps({'binary': sys.argv[0], 'args': args, 'injections': injections}) + '\\n')
            if injections:
                print('Unsafe injected Java environment reached the runtime', file=sys.stderr)
                raise SystemExit(96)
            mode = os.environ.get('FOLD8_TEST_MODE', '')
            if '-jar' not in args:
                if mode == 'crash_java':
                    print('Native registration unable to find class com/android/internal/protolog/ProtoLogNative; aborting', file=sys.stderr)
                    raise SystemExit(134)
                print('openjdk 26 test runtime')
                raise SystemExit(0)
            start = args.index('-jar')
            if not pathlib.Path(args[start + 1]).is_file():
                raise SystemExit(95)
            commands = args[start + 2:]
            if not commands or commands[0] in ('--version', 'version'):
                if mode == 'crash_signer':
                    print('Native registration unable to find class com/android/internal/protolog/ProtoLogNative; aborting', file=sys.stderr)
                    raise SystemExit(134)
                print('apksigner 35.0.0 test fixture')
                raise SystemExit(0)
            if commands[0] == 'sign':
                if mode == 'sign_failure':
                    print('Fixture signing failed', file=sys.stderr)
                    raise SystemExit(1)
                output = pathlib.Path(commands[commands.index('--out') + 1])
                output.write_bytes(pathlib.Path(commands[-1]).read_bytes() + b'fixture signature\\n')
                raise SystemExit(0)
            if commands[0] == 'verify':
                apk = pathlib.Path(commands[-1])
                if mode == 'crash_verify':
                    print('Native registration unable to find class com/android/internal/protolog/ProtoLogNative; aborting', file=sys.stderr)
                    raise SystemExit(134)
                if mode == 'verify_failure':
                    print('DOES NOT VERIFY', file=sys.stderr)
                    raise SystemExit(1)
                if mode == 'signed_verify_failure' and apk.name == 'signed.apk':
                    print('Signed output does not verify', file=sys.stderr)
                    raise SystemExit(1)
                if '--print-certs' in commands:
                    cert = '0' * 64 if mode == 'cert_mismatch' else os.environ['FOLD8_TEST_CERT']
                    print('Signer #1 certificate SHA-256 digest: ' + cert)
                else:
                    print('Verifies')
                raise SystemExit(0)
            raise SystemExit(94)
            """)

    def run_helper(self, mode="", extra_environment=None):
        environment = self.environment.copy()
        environment["FOLD8_TEST_MODE"] = mode
        if extra_environment:
            environment.update(extra_environment)
        return subprocess.run(["bash", str(self.helper), str(self.apk)], env=environment,
                              text=True, capture_output=True, timeout=15)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def assert_failed_safely(self, result):
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertFalse(self.output.exists(), result.stdout + result.stderr)
        self.assertEqual(self.key_bytes, self.key.read_bytes())

    def test_success_uses_existing_jdk_verified_jar_and_sanitized_environment(self):
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(self.apk.read_bytes() + b"fixture signature\n", self.output.read_bytes())
        calls = self.calls()
        self.assertTrue(calls)
        self.assertTrue(all(not call["injections"] for call in calls))
        signer_calls = [call["args"] for call in calls if "-jar" in call["args"]]
        self.assertTrue(signer_calls)
        self.assertTrue(all(str(self.jar) in args for args in signer_calls))
        sign = next(args for args in signer_calls if "sign" in args)
        self.assertEqual(str(self.key), sign[sign.index("--ks") + 1])
        self.assertNotIn("WRONG PATH WRAPPER", result.stdout + result.stderr)
        self.assertNotIn("UNEXPECTED AAPT WRAPPER", result.stdout + result.stderr)
        self.assertTrue(all(call["binary"] == str(self.java) for call in calls))

    def test_explicit_java_and_jar_paths_are_supported(self):
        alternate = self.work / "alternate JDK/bin/java"
        alternate.parent.mkdir(parents=True)
        self.write_executable(alternate, self.fake_java())
        jar = self.work / "alternate signer.jar"
        jar.write_bytes(self.jar.read_bytes())
        result = self.run_helper(extra_environment={"FOLD8_JAVA": str(alternate), "FOLD8_SIGNER_JAR": str(jar)})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(any(str(jar) in call["args"] for call in self.calls()))

    def test_v17_output_uses_its_own_name_and_keeps_existing_v16_update(self):
        old_update = self.apps / "Fold8-Control-Lab-v16-update.apk"
        old_update.write_bytes(b"previous v16 update to preserve")
        self.output = self.apps / "Fold8-Control-Lab-v17-update.apk"
        self.environment.pop("FOLD8_OUTPUT_APK", None)
        self.render_helper(app_version=17)
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(self.apk.read_bytes() + b"fixture signature\n", self.output.read_bytes())
        self.assertEqual(b"previous v16 update to preserve", old_update.read_bytes())
        self.assertIn("v17 update saved", result.stdout)

    def test_v18_output_preserves_both_older_update_versions(self):
        old_updates = [self.apps / f"Fold8-Control-Lab-v{version}-update.apk" for version in (16, 17)]
        for old_update in old_updates:
            old_update.write_bytes(b"previous update to preserve")
        self.output = self.apps / "Fold8-Control-Lab-v18-update.apk"
        self.environment.pop("FOLD8_OUTPUT_APK", None)
        self.render_helper(app_version=18)
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(self.apk.read_bytes() + b"fixture signature\n", self.output.read_bytes())
        self.assertTrue(all(path.read_bytes() == b"previous update to preserve" for path in old_updates))
        self.assertIn("v18 update saved", result.stdout)

    def test_packager_rejects_invalid_version_without_writing_output(self):
        output = self.work / "invalid-helper.sh"
        result = subprocess.run(["bash", str(ROOT / "tools/package-sign-helper.sh"),
                                 str(self.apk), str(self.jar), str(output), "17;invalid"],
                                text=True, capture_output=True, timeout=15)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertFalse(output.exists())

    def test_existing_update_is_not_overwritten(self):
        self.output.write_bytes(b"existing update to keep")
        result = self.run_helper()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b"existing update to keep", self.output.read_bytes())
        self.assertFalse(any("sign" in call["args"] for call in self.calls()))

    def test_changed_apk_is_rejected_before_signing(self):
        self.apk.write_bytes(b"different APK")
        result = self.run_helper()
        self.assert_failed_safely(result)
        self.assertIn("checksum", (result.stdout + result.stderr).lower())
        self.assertFalse(any("sign" in call["args"] for call in self.calls()))

    def test_changed_signer_jar_is_rejected_before_launch(self):
        self.jar.write_bytes(b"different signer")
        result = self.run_helper()
        self.assert_failed_safely(result)
        self.assertFalse(any("-jar" in call["args"] for call in self.calls()))

    def test_runtime_abort_is_not_reported_as_invalid_apk(self):
        result = self.run_helper("crash_java")
        self.assert_failed_safely(result)
        output = result.stdout + result.stderr
        self.assertIn("APK verification has not run", output)
        self.assertNotIn("Input APK signature is invalid", output)

    def test_signer_abort_is_not_reported_as_invalid_apk(self):
        result = self.run_helper("crash_signer")
        self.assert_failed_safely(result)
        self.assertNotIn("Input APK signature is invalid", result.stdout + result.stderr)

    def test_verify_process_abort_is_not_a_signature_verdict(self):
        result = self.run_helper("crash_verify")
        self.assert_failed_safely(result)
        self.assertNotIn("Input APK signature is invalid", result.stdout + result.stderr)
        self.assertFalse(any("sign" in call["args"] for call in self.calls()))

    def test_missing_original_key_cannot_generate_or_replace_one(self):
        missing = self.work / "missing original key.jks"
        result = self.run_helper(extra_environment={"FOLD8_KEYSTORE": str(missing)})
        self.assert_failed_safely(result)
        self.assertFalse(missing.exists())
        self.assertFalse(any("sign" in call["args"] for call in self.calls()))

    def test_signature_failure_never_reaches_signing(self):
        result = self.run_helper("verify_failure")
        self.assert_failed_safely(result)
        self.assertFalse(any("sign" in call["args"] for call in self.calls()))

    def test_supported_older_jdk_is_used_without_any_path_wrapper(self):
        alternate = self.prefix / "lib/jvm/java-21-openjdk/bin/java"
        alternate.parent.mkdir(parents=True)
        self.write_executable(alternate, self.fake_java())
        self.java.rename(self.java.with_name("disabled-java"))
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(all(call["binary"] == str(alternate) for call in self.calls()))

    def test_signing_failure_saves_no_update(self):
        self.assert_failed_safely(self.run_helper("sign_failure"))

    def test_signed_output_must_verify_before_it_is_saved(self):
        self.assert_failed_safely(self.run_helper("signed_verify_failure"))

    def test_certificate_mismatch_saves_no_update(self):
        result = self.run_helper("cert_mismatch")
        self.assert_failed_safely(result)
        self.assertIn("certificate", (result.stdout + result.stderr).lower())


class ArchivePreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="fold8 archive test ")
        self.work = Path(self.directory.name)
        self.downloads = self.work / "downloads with spaces"
        self.archives = self.downloads / "archives"
        self.archives.mkdir(parents=True)
        self.private = self.work / "private tmp"
        self.private.mkdir()
        self.environment = os.environ.copy()
        self.environment.update(FOLD8_DOWNLOADS=str(self.downloads), TMPDIR=str(self.private))
        self.wrapper = ROOT / "tools/prepare-update-from-archives.sh"
        self.bash = shutil.which("bash")

    def tearDown(self):
        self.directory.cleanup()

    def archive(self, filename, payload=b"selected APK fixture", helper=True, version=18):
        path = self.archives / filename
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(f"Fold8-Control-Lab-v{version}.apk", payload)
            if helper:
                archive.writestr(f"sign-v{version}-with-phone-key.sh", textwrap.dedent("""\
                    #!/usr/bin/env bash
                    set -euo pipefail
                    [[ "${FOLD8_PREPARE_TEST_FAILURE:-}" != 1 ]] || exit 23
                    [[ -f "$1" ]]
                    [[ "$FOLD8_OUTPUT_APK" == "$FOLD8_DOWNLOADS/apps/"* ]]
                    [[ ! -e "$FOLD8_OUTPUT_APK" ]]
                    [[ "${FOLD8_PREPARE_TEST_NO_OUTPUT:-}" != 1 ]] || exit 0
                    cp -- "$1" "$FOLD8_OUTPUT_APK"
                    """))
        return path

    def run_wrapper(self, *arguments):
        return subprocess.run([self.bash, str(self.wrapper), *arguments], env=self.environment,
                              text=True, capture_output=True, timeout=15)

    def test_newest_matching_zip_is_used_without_extracting_into_downloads(self):
        older = self.archive("Fold8-Control-Lab-v18-old.zip", b"older APK")
        newest = self.archive("Fold8-Control-Lab-v18-new.zip", b"newest APK")
        other_version = self.archive("Fold8-Control-Lab-v17-new.zip", b"wrong version", version=17)
        for timestamp, path in enumerate((older, newest, other_version), start=100):
            os.utime(path, (timestamp, timestamp))
        apps = self.downloads / "apps"
        apps.mkdir()
        old_update = apps / "Fold8-Control-Lab-v18-update.apk"
        old_update.write_bytes(b"existing update")
        result = self.run_wrapper()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        outputs = list(apps.glob("Fold8-Control-Lab-v18-update-*.apk"))
        self.assertEqual(1, len(outputs))
        self.assertEqual(b"newest APK", outputs[0].read_bytes())
        self.assertEqual(b"existing update", old_update.read_bytes())
        self.assertEqual({"archives", "apps"}, {path.name for path in self.downloads.iterdir()})
        self.assertFalse(list(self.private.iterdir()))

    def test_missing_zip_reports_the_archives_location(self):
        result = self.run_wrapper()
        self.assertNotEqual(0, result.returncode)
        self.assertIn(str(self.archives), result.stderr)
        self.assertFalse(list(self.private.iterdir()))

    def test_missing_helper_stops_before_creating_an_output_and_cleans_up(self):
        self.archive("Fold8-Control-Lab-v18-incomplete.zip", helper=False)
        result = self.run_wrapper()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("root sign-v18-with-phone-key.sh entry", result.stderr)
        self.assertFalse((self.downloads / "apps").exists())
        self.assertFalse(list(self.private.iterdir()))

    def test_version_argument_selects_the_requested_release(self):
        self.archive("Fold8-Control-Lab-v17.zip", b"version17 APK", version=17)
        result = self.run_wrapper("17")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(b"version17 APK", next((self.downloads / "apps").glob("*.apk")).read_bytes())

    def test_invalid_version_is_rejected_before_any_extraction(self):
        result = self.run_wrapper("18;invalid")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("positive integer", result.stderr)
        self.assertFalse(list(self.private.iterdir()))

    def test_missing_unzip_gives_install_instruction_without_installing_anything(self):
        self.environment["PATH"] = str(self.work / "no commands")
        result = self.run_wrapper()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("pkg install unzip", result.stderr)
        self.assertFalse(list(self.private.iterdir()))

    def test_helper_failure_is_preserved_and_private_extraction_is_removed(self):
        self.archive("Fold8-Control-Lab-v18.zip")
        self.environment["FOLD8_PREPARE_TEST_FAILURE"] = "1"
        result = self.run_wrapper()
        self.assertEqual(23, result.returncode, result.stdout + result.stderr)
        self.assertFalse(list((self.downloads / "apps").iterdir()))
        self.assertFalse(list(self.private.iterdir()))

    def test_helper_success_without_an_apk_is_not_reported_as_ready(self):
        self.archive("Fold8-Control-Lab-v18.zip")
        self.environment["FOLD8_PREPARE_TEST_NO_OUTPUT"] = "1"
        result = self.run_wrapper()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("without saving a nonempty update APK", result.stderr)
        self.assertNotIn("Ready for manual installation", result.stdout)
        self.assertFalse(list(self.private.iterdir()))


class DiagnosticModuleFilterTest(unittest.TestCase):
    def test_related_exception_cause_and_stack_are_kept_without_adjacent_private_logs(self):
        source = (ROOT / "tools/collect-clock-diagnostics.sh").read_text()
        function = source.split("module_lines() {", 1)[1].split("\n}\n", 1)[0]
        program = "module_lines() {" + function + "\n}\nmodule_lines\n"
        logs = "\n".join([
            "11-16 01:02:03 E PrivateApp: unrelated private text before failure",
            "11-16 01:02:03 E PrivateApp: java.lang.RuntimeException: unrelated private exception",
            "11-16 01:02:04 E Vector: Failed to load class com.fold8.secondsclock.SecondsHook",
            "11-16 01:02:04 E Vector: java.lang.ExceptionInInitializerError",
            "11-16 01:02:04 E Vector:     at java.lang.Class.classForName(Native Method)",
            "11-16 01:02:04 E Vector: Caused by: java.lang.RuntimeException: No Looper",
            "11-16 01:02:04 E Vector:     at android.os.Handler.<init>(Handler.java:200)",
            "11-16 01:02:04 E Vector:     at com.fold8.secondsclock.SecondsHook.<clinit>(SecondsHook.java:64)",
            "11-16 01:02:05 E PrivateApp: unrelated private text after failure",
            "11-16 01:02:05 E PrivateApp: Caused by: java.lang.RuntimeException: unrelated private cause",
            "11-16 01:02:06 I Vector: [Fold8ControlLab] v18 package callback: com.android.systemui",
        ]) + "\n"
        result = subprocess.run(["sh", "-c", program], input=logs, text=True, capture_output=True, timeout=5)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("ExceptionInInitializerError", result.stdout)
        self.assertIn("Caused by: java.lang.RuntimeException: No Looper", result.stdout)
        self.assertIn("Handler.<init>", result.stdout)
        self.assertIn("SecondsHook.java:64", result.stdout)
        self.assertIn("v18 package callback", result.stdout)
        self.assertNotIn("unrelated private text", result.stdout)
        self.assertNotIn("unrelated private exception", result.stdout)
        self.assertNotIn("unrelated private cause", result.stdout)

    def test_unprefixed_cause_before_the_matching_module_frame_is_kept(self):
        source = (ROOT / "tools/collect-clock-diagnostics.sh").read_text()
        function = source.split("module_lines() {", 1)[1].split("\n}\n", 1)[0]
        logs = "\n".join([
            "java.lang.ExceptionInInitializerError",
            "    at java.lang.Class.classForName(Native Method)",
            "Caused by: java.lang.RuntimeException: No Looper",
            "    at android.os.Handler.<init>(Handler.java:200)",
            "    at com.fold8.secondsclock.SecondsHook.<clinit>(SecondsHook.java:64)",
        ]) + "\n"
        result = subprocess.run(["sh", "-c", "module_lines() {" + function + "\n}\nmodule_lines\n"],
                                input=logs, text=True, capture_output=True, timeout=5)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(logs, result.stdout)


if __name__ == "__main__":
    unittest.main()
