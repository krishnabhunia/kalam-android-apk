# Kalam Android — working agreement (read this first)

This file carries the conventions established over v1.0 → v1.21 in chat. Follow them exactly.

## Project constants
- Package `com.krishna.kalam` · Kotlin/Compose · minSdk 26 · targetSdk 34 · compose BOM 2024.06.00.
- Version scheme: `versionName "1.N"` ⇔ `versionCode N+1`, set in `app/build.gradle` **and** `AppVersion.NAME` / `RELEASE_DATE` (`app/src/main/java/com/krishna/kalam/AppVersion.kt`). `version_gate.py` fails the build if they disagree.
- Current released: **v1.21 / versionCode 22** (17-Sep-2026). Next release is **v1.22 / 23**.
- Build: `./gradlew :app:testDebugUnitTest` (120 tests, must be green) then `./release.sh 1.22` (hard-gated chain, see below).

## Non-negotiable process rules (Krishna's)
1. **Build ONLY on the explicit word "Release" or "Build".** A bug report, question, or feature request → root-cause + queue + reply ONLY. Never build on your own initiative. (A past violation is recorded in CHANGELOG v1.13.)
2. **Design before build.** New features get a design round first: an HTML mock-up saved as `docs/designs/kalam-<latest-version>.D<n>-design.html`, with numbered **blocker questions** each carrying a recommendation. "Use recommended" / "defaults" accepts all recommendations. "Add to queue" ≠ build.
3. **Surface conflicts before changes.** If a request contradicts an earlier decision or the verified data, say so explicitly with the evidence — never silently switch.
4. **Companions per item:** exception handling, one log line per event (Logger only has `e(tag,msg,throwable?)`), and unit tests with real vectors.
5. **Wire-verify external data at build** (drikpanchang for the Bengali calendar/tithi, Open-Meteo payloads) and bake the real payloads in as test fixtures.
6. **Honest disclosure.** Say what was NOT verified (anything needing a real phone: alarms ringing, lock-screen behaviour, install-over-upgrade). Never claim a fix that isn't proven in the artifact.
7. **Verify the delivered artifact, not the build dir:** `aapt dump badging` on the APK you hand over must show the expected versionCode/Name; md5 must differ from the previous release.
8. Reply style: terse, tables over prose, numbered blockers.

## Release chain (what `release.sh` enforces)
tests green → `version_gate.py` → assembleDebug → copy APK to `dist/Kalam-1.N.apk` → badging assert on the copy → md5 distinct from `dist/Kalam-1.(N-1).apk` if present → append release record to CHANGELOG.md and device checks to DEVICE-CHECKLIST.md (by hand, same format as existing entries) → commit + tag `v1.N` → push.
Debug-signed until K83 lands (release keystore is **never** committed; `.gitignore` blocks it).

## Verified engines — do not "fix" without new evidence
- Bengali calendar = **drik / Lahiri, WB day-after-sankranti rule** (`core/Bangla.kt`), golden-gated against drikpanchang (Kolkata geoname 1275004): 14-Apr-2026 = 30 Chaitra 1432, 15-Apr = 1 Boishakh 1433, 10-Sep = 24 Bhadro, 28-Sep = 11 Ashshin. Gupta-Press / Surya-Siddhanta almanacs differ by one day near month starts — that is K82, not a bug.
- Alarms: request codes 0–5 kalam, 6–9 sun/moon (real alarms: alarm stream, full-screen `AlarmActivity`, STOP via button/volume/power/back, 60 s cap), 10–13 snooze. `FiredLedger` keys `code@date[#seq]`, fails OPEN.
- Settings blob: 55 positional fields (`data/Prefs.kt`), tolerant decode — append only, never reorder.

## Open queue (as of 28-Sep-2026)
| Item | Status | Spec |
|---|---|---|
| K83 GitHub self-update | Approved (1.21.D2) | `update.json` per release (versionCode, apk url, sha256, notes); WorkManager 24 h check + Settings "Check now"; download → sha256 verify → package installer; stable release keystore (generate once, Krishna keeps it, never in repo); first signed release needs one-time reinstall |
| K82 Calendar authority | Approved, blocked on data | Setting Drik (default) / Traditional (Surya Siddhanta); needs 2–3 Gupta Press dates from Krishna as golden vectors |
| Device checks | Pending on hardware | DEVICE-CHECKLIST.md D-v1.13 … D-v1.21 (D-v1.21-1/2/3 are the alarm-STOP proof) |

## Layout
`app/src/main/java/com/krishna/kalam/` → `core/` (Solar, Lunar, Bangla, SunAlarm, Engine+Logger) · `alarm/Alarms.kt` (Notifs, FiredLedger, AlarmScheduler, receivers, SoundService) · `ui/` (MainActivity+DayTab+SkyTable, Tabs.kt Week/Match, Clocks.kt, Geo.kt+ClockOrder+GroupStage, SettingsUi, SunAlarmUi, AlarmActivity) · `data/Prefs.kt` · `api/Prokerala.kt`. Tests in `app/src/test/`. Design history in `docs/designs/`.
