#!/usr/bin/env bash
# Hard-gated release chain. Usage: ./release.sh 1.22   (versionCode = N+1 is derived)
set -euo pipefail
V="${1:?usage: ./release.sh 1.N}"; N="${V#1.}"; CODE=$((N+1)); PREV="1.$((N-1))"
: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
AAPT="$(ls "$ANDROID_HOME"/build-tools/*/aapt | tail -1)"
echo "== gate 0: tree matches requested version =="
grep -q "versionCode $CODE; versionName \"$V\"" app/build.gradle || { echo "app/build.gradle not at $V/$CODE"; exit 1; }
python3 version_gate.py
echo "== gate 1: unit tests =="
./gradlew --no-daemon -q :app:testDebugUnitTest
T=$(find app/build/test-results -name '*.xml' -exec grep -h '<testsuite' {} + | awk -F'"' '{s+=$4; f+=$8} END {print s" tests, "f" failures"}'); echo "$T"; [[ "$T" == *" 0 failures" ]]
echo "== assemble =="
./gradlew --no-daemon -q :app:assembleDebug
mkdir -p dist && cp app/build/outputs/apk/debug/app-debug.apk "dist/Kalam-$V.apk"
echo "== gate 2: badging on the DELIVERED apk =="
B="$("$AAPT" dump badging "dist/Kalam-$V.apk" | grep '^package')"; echo "$B"
[[ "$B" == *"versionCode='$CODE' versionName='$V'"* ]]
if [[ -f "dist/Kalam-$PREV.apk" ]]; then
  echo "== gate 3: md5 distinct from $PREV =="
  [[ "$(md5sum "dist/Kalam-$PREV.apk" | cut -d' ' -f1)" != "$(md5sum "dist/Kalam-$V.apk" | cut -d' ' -f1)" ]]
fi
sha256sum "dist/Kalam-$V.apk" | tee "dist/Kalam-$V.sha256"
echo "ALL GATES PASSED → dist/Kalam-$V.apk   (now: update CHANGELOG.md + DEVICE-CHECKLIST.md, commit, tag v$V, push)"
