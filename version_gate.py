#!/usr/bin/env python3
"""K63 gate: AppVersion.NAME must equal gradle versionName. Exit 1 on drift."""
import re, sys
g = open('app/build.gradle').read()
k = open('app/src/main/java/com/krishna/kalam/AppVersion.kt').read()
gv = re.search(r'versionName\s+"([^"]+)"', g).group(1)
kv = re.search(r'const val NAME = "([^"]+)"', k).group(1)
if gv != kv:
    print(f"VERSION DRIFT: gradle={gv} AppVersion={kv}"); sys.exit(1)
print(f"version gate ok: {gv}")
