# Kalam — Android

Rahu Kalam · Yamagandam · Gulika Kalam for any place, plus a traditional West Bengal civil calendar,
tithi, world clocks with weather/AQI, and real sunrise / sunset / moonrise / moonset alarms.

Kotlin · Jetpack Compose · minSdk 26 · targetSdk 34.

Civil dates follow the local traditional almanac, with bundled coverage from
15 April 2025 through 14 April 2028. See [calendar provenance](docs/calendar/traditional-calendar.md).
Tithi continues to use the existing astronomical engine.

To keep clocks across reinstalling, use **Settings → Clock backup → Back up clocks** and save the
file in Downloads before uninstalling. After reinstalling, open **Settings → Clock backup → Restore clocks** and select the file. On supported phones, Kalam also requests the
**Keep app data** uninstall option. See [clock backup](docs/clock-backup.md).

## Build

```
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
python3 version_gate.py                      # AppVersion.NAME must equal gradle versionName
./gradlew :app:testDebugUnitTest             # civil-almanac and astronomical golden vectors
gradle :app:assembleDebug
```

## Releases

The single Software workflow tests PRs and produces semantic-version beta APKs.
After merge to main, it publishes the allocated stable version on GitHub.
The single artifact is `Kalam_<version>.zip`; one extraction reveals
`Android/Kalam_<version>.apk`. Windows and macOS are absent and are skipped.
Installable workflow APKs require the permanent signing key in protected GitHub
Actions secrets. Missing signing configuration blocks publication rather than
generating a different debug key. Settings includes GitHub update checks,
optional beta releases, verified download and Android's installation prompt.
See [Android updates and signing setup](docs/android-updates.md).

## Privacy

No analytics, no accounts. Prokerala API credentials (for the Match tab) are entered by
the user at runtime and stored only on the device — nothing is baked into this source.
Birth-detail defaults are blank in a fresh install.

## Verification discipline

Every release passes: unit suite · version gate · `aapt` badging on the *delivered* APK ·
zip-content greps · md5-distinct-from-previous. External data (Bengali calendar, tithi,
weather payloads) is wire-verified at build and baked in as test vectors.
