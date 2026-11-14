#!/usr/bin/env python3
"""Exercise the embedded Java signer with a disposable test key, never the phone key."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
apk, jar, jdk = map(lambda p: Path(p).resolve(), sys.argv[1:4])
with tempfile.TemporaryDirectory(prefix="fold8-real-sign-test-") as directory:
    work = Path(directory)
    key = work / "test-only.jks"
    java = jdk / "bin/java"
    environment = os.environ.copy()
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
        environment.pop(name, None)
    subprocess.run([str(jdk / "bin/keytool"), "-genkeypair", "-alias", "fold8clock",
                    "-keystore", str(key), "-storepass", "android", "-keypass", "android",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                    "-dname", "CN=Disposable Signing Helper Test"], env=environment,
                   check=True, capture_output=True)
    original_key_hash = hashlib.sha256(key.read_bytes()).hexdigest()
    helper = work / "packaged-helper.sh"
    subprocess.run(["bash", str(root / "tools/package-sign-helper.sh"), str(apk),
                    str(jar), str(helper)], check=True, capture_output=True)
    output = work / "update.apk"
    environment.update(FOLD8_JAVA=str(java), FOLD8_KEYSTORE=str(key), FOLD8_OUTPUT_APK=str(output))
    # The child must clear options that would otherwise prevent OpenJDK startup.
    environment["JAVA_TOOL_OPTIONS"] = "-invalid-injected-option"
    environment["_JAVA_OPTIONS"] = "-invalid-injected-option"
    environment["JDK_JAVA_OPTIONS"] = "-invalid-injected-option"
    result = subprocess.run(["bash", str(helper), str(apk)], env=environment,
                            capture_output=True, text=True, timeout=30)
    assert result.returncode != 0 and not output.exists(), result.stdout + result.stderr
    assert "Certificate does not match" in result.stderr, result.stdout + result.stderr
    print("PASS: actual SDK signer completed, rejected the non-phone key, saved no update")
    certificate = subprocess.check_output([str(jdk / "bin/keytool"), "-exportcert",
                    "-alias", "fold8clock", "-keystore", str(key), "-storepass", "android"],
                    env={k: v for k, v in environment.items() if k not in
                         ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH")})
    certificate_hash = hashlib.sha256(certificate).hexdigest()
    # A separate fixture pins its own disposable test certificate. Production
    # helper and delivered files retain the owner's original certificate pin.
    fixture = work / "fixture-helper.sh"
    fixture.write_text(helper.read_text().replace(
        "clock_expected_cert='619620638046dda1c81bbdeeca27481443c7f6b0918d782578025c31a031b6bf'",
        "clock_expected_cert='" + certificate_hash + "'"))
    result = subprocess.run(["bash", str(fixture), str(apk)], env=environment,
                            capture_output=True, text=True, timeout=30)
    assert result.returncode == 0 and output.exists(), result.stdout + result.stderr
    verification = subprocess.check_output([str(java), "-jar", str(jar), "verify", "--print-certs", str(output)],
                                           env={k: v for k, v in environment.items() if k not in
                                                ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH")}, text=True)
    assert certificate_hash in verification
    assert hashlib.sha256(key.read_bytes()).hexdigest() == original_key_hash
    assert hashlib.sha256(apk.read_bytes()).hexdigest() in helper.read_text()
    print("PASS: separate test-certificate fixture saved a valid signed APK; existing test key unchanged")
