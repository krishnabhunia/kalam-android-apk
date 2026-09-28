# Kalam — Android

Rahu Kalam · Yamagandam · Gulika Kalam for any place, plus a Bengali (WB, drik) calendar,
tithi, world clocks with weather/AQI, and real sunrise / sunset / moonrise / moonset alarms.

Kotlin · Jetpack Compose · minSdk 26 · targetSdk 34.

## Build

```
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
python3 version_gate.py                      # AppVersion.NAME must equal gradle versionName
gradle :app:testDebugUnitTest                # 120 unit tests incl. drikpanchang golden vectors
gradle :app:assembleDebug
```

## Releases

APKs are published under GitHub Releases of this repository. From v1.22 the app
checks for a newer release in the background and offers to download and install it
(see `CHANGELOG.md`, K83).

## Privacy

No analytics, no accounts. Prokerala API credentials (for the Match tab) are entered by
the user at runtime and stored only on the device — nothing is baked into this source.
Birth-detail defaults are blank in a fresh install.

## Verification discipline

Every release passes: unit suite · version gate · `aapt` badging on the *delivered* APK ·
zip-content greps · md5-distinct-from-previous. External data (Bengali calendar, tithi,
weather payloads) is wire-verified at build and baked in as test vectors.
