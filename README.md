# Kalam — Android

Rahu Kalam · Yamagandam · Gulika Kalam for any place, plus a traditional West Bengal civil calendar,
tithi, world clocks with weather/AQI, and real sunrise / sunset / moonrise / moonset alarms.

Kotlin · Jetpack Compose · minSdk 26 · targetSdk 34.

Civil dates follow the local traditional almanac, with bundled coverage from
15 April 2025 through 14 April 2028. See [calendar provenance](docs/calendar/traditional-calendar.md).
Tithi continues to use the existing astronomical engine.

## Build

```
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
python3 version_gate.py                      # AppVersion.NAME must equal gradle versionName
./gradlew :app:testDebugUnitTest             # civil-almanac and astronomical golden vectors
gradle :app:assembleDebug
```

## Releases

The single Software workflow tests PRs and produces semantic-version beta APKs.
After merge to main, it publishes the next stable bugfix version on GitHub.
The single artifact is `Kalam_<version>.zip`; one extraction reveals
`Android/Kalam_<version>.apk`. Windows and macOS are absent and are skipped.
APKs currently use the debug signing key; stable signing and self-update remain
queued under K83 and are not implemented by this calendar correction.

## Privacy

No analytics, no accounts. Prokerala API credentials (for the Match tab) are entered by
the user at runtime and stored only on the device — nothing is baked into this source.
Birth-detail defaults are blank in a fresh install.

## Verification discipline

Every release passes: unit suite · version gate · `aapt` badging on the *delivered* APK ·
zip-content greps · md5-distinct-from-previous. External data (Bengali calendar, tithi,
weather payloads) is wire-verified at build and baked in as test vectors.
