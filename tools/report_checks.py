#!/usr/bin/env python3
"""Expose useful diagnostics and real test counts in Actions annotations."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def annotate(level, text):
    escaped = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{level}::{escaped}')


reports = list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
total = failures = errors = skipped = 0
for report in reports:
    suite = ET.parse(report).getroot()
    total += int(suite.get('tests', 0))
    failures += int(suite.get('failures', 0))
    errors += int(suite.get('errors', 0))
    skipped += int(suite.get('skipped', 0))
    for case in suite.findall('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            annotate('error', f"{case.get('name')}: {(failure.text or failure.get('message', ''))[:4000]}")
annotate('notice', f'Android unit tests: {total} total, {failures} failures, {errors} errors, {skipped} skipped; device tests were not run.')
if os.environ.get('VALIDATION_OUTCOME') != 'success':
    log = Path('build-validation.log')
    if log.exists():
        annotate('error', 'Gradle output (tail):\n' + log.read_text(errors='replace')[-8000:])
    raise SystemExit(1)
if total - skipped == 0 or failures or errors:
    annotate('error', 'Validation did not execute a passing test suite.')
    raise SystemExit(1)
