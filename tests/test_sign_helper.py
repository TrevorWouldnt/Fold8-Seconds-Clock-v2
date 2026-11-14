#!/usr/bin/env python3
"""Run the phone signing helper against isolated signer/runtime fixtures.

The fixture never uses a real signing key or Android runtime. It exercises the
shell's path selection, integrity checks, failure handling and output rules.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import textwrap
import unittest


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

    def render_helper(self, apk_hash=None, jar_hash=None):
        contents = HELPER.read_text()
        contents = contents.replace("__APK_SHA256__", apk_hash or hashlib.sha256(self.apk.read_bytes()).hexdigest())
        contents = contents.replace(SDK_SIGNER_SHA256, jar_hash or hashlib.sha256(self.jar.read_bytes()).hexdigest())
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


if __name__ == "__main__":
    unittest.main()
