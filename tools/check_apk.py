"""Refuse an APK that is debuggable, foreign or carries the wrong version.

Usage: python tools/check_apk.py <apk> <versionCode> <versionName>
A debuggable APK lets anyone with USB access read Kalam's private data (run-as),
so it must never be signed with the permanent key or offered as an update.
"""
import os
import pathlib
import re
import subprocess
import sys

PACKAGE = 'com.krishna.kalam'


def problems(badging, code, version):
    """Return every reason the badging output is not a releasable Kalam APK."""
    found = []
    head = re.search(r"^package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'",
                     badging, re.MULTILINE)
    if not head:
        return ['aapt did not report a package line']
    name, actual_code, actual_version = head.groups()
    if name != PACKAGE:
        found.append(f'package is {name}, expected {PACKAGE}')
    if actual_code != str(code) or actual_version != version:
        found.append(f'version is {actual_version} ({actual_code}), expected {version} ({code})')
    if re.search(r'^application-debuggable', badging, re.MULTILINE):
        found.append('APK is debuggable')
    return found


def main(apk, code, version):
    aapt = pathlib.Path(os.environ['ANDROID_HOME']) / 'build-tools' / '34.0.0' / 'aapt'
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
    print(badging.splitlines()[0])
    found = problems(badging, code, version)
    for problem in found:
        print(f'::error::{problem}')
    return 1 if found else 0


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    sys.exit(main(*sys.argv[1:]))
