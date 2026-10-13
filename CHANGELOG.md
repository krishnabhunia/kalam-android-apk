## 13 October 2026 — one final workflow artifact

- Set the explicit ZIP name on both uploads so the signed ZIP replaces the
  unsigned validation artifact when archive creation is disabled.
- Verify artifact count, upload ID, filename and SHA-256 after each upload;
  block publication if the run retains anything other than the expected ZIP.
- Add regression coverage for duplicate artifacts and mismatched uploads.

## 13 October 2026 — correct PR build and instruction compliance

- Keep signing keys in a branch-restricted environment, outside PR jobs.
  PR validation APKs are unsigned; reviewed manual/main builds are signed.
- Serialize signed workflows and allocate codes above all signed tags.
- Clean superseded owned APK cache files only after verified atomic replacement.
- Configure and verify one protected permanent Android signing key; no private
  key or password is committed. Retain a local Windows-encrypted backup.
- Add required platform folders and move Android source to android/app while
  preserving the Gradle :app module and one unified Software workflow.
- Add default-on daily background update checks, persisted last-check details,
  and an immediate Settings toggle; retain startup/manual checks and optional beta.
- Automatically classify major/minor/patch from unreleased Conventional Commits;
  beta retries now receive higher Android version codes.
- Make release publishing safe to retry without changing an existing tag or APK.
- Delegate legacy local release requests to the same automatic GitHub workflow.
- Record the adopted GitHub documents in docs/instructions; PR title uses cgpt-.

# Kalam — Android BACKLOG

Package `com.krishna.kalam` · Kotlin/Compose · minSdk 26 · targetSdk 34 · JDK21 · Gradle 8.7
Versioning: versionName minor = versionCode (v1.0 → versionCode 1). No gaps.
Status: **QUEUED — no build authorized.** Build only on explicit trigger word.
Related but separate: Python/ICS pipeline in `kalam-calendar` repo stays as-is.

---

## Decisions ledger (locked)

| # | Decision |
|---|---|
| D1 | Cities (bundled, ascending): Bengaluru, Chennai, Delhi, Hyderabad, Kalyan, Kolkata, Mumbai, Pune |
| D2 | GPS: precise (FINE), fetch + repeatable Re-fetch before save; **frozen after save** (rename/delete only) |
| D3 | Saved GPS locations: max 20; name editable; delete via settings, immediate + undo snackbar |
| D4 | Location list: bundled section, separator, saved section (name asc) |
| D5 | Last selected location persists; restored on reopen. First-run default location configurable in settings (initial: Kolkata — proposed) |
| D6 | Kalam order fixed: R.K → Y.G → G.K. Per-kalam visibility toggles. Hidden kalam ⇒ no alerts for it |
| D7 | Date nav: swipe −30 … +400 days; Today + Tomorrow buttons; bounded date picker |
| D8 | Alarms arm for **today only**, regardless of viewed date |
| D9 | Recompute at midnight; keep 10-day in-memory cache (D0–D9) |
| D10 | Past kalams grey out (per alarm convention) |
| D11 | Conventions: compute both geometric + observational. Settings: alarm-convention selector (default geometric) + show-both toggle (default off). Bell icon sits beside the alarm-bound time. Show-both off ⇒ display the alarm-bound time only. Grey-out follows alarm convention |
| D12 | Alert slots: 6 = {R.K, Y.G, G.K} × {start, end}. Start: on/off + offset {OnTime, 15, 30, 45, 60, 75, 90, 105, 120} min. End: on/off, fires at end time, no offset |
| D13 | Pre-sunrise fires (Sat G.K, Thu Y.G with large offsets): fire as-is, no floor |
| D14 | Alert style: start-alerts and end-alerts each {Sound+Vibrate, Silent}; both default Sound+Vibrate |
| D15 | Ring duration: global {3, 5, 7, 10, 15, 20, 30} s (default 5 — proposed). App-managed playback, not channel sound |
| D16 | No full-screen intent, no lock-screen takeover. Foreground: bottom sheet ≤50% height, Dismiss button, swipe-down, single-tap scrim dismiss. Background: heads-up notification, IMPORTANCE_HIGH |
| D17 | No DND override. Playback uses notification audio attributes; DND silences it |
| D18 | Dismiss affects that occurrence only |
| D19 | Settings staged: Save/Discard at top; dirty back-press prompts; buttons disabled when clean; deletes immediate + undo; commit triggers alarm re-arm; draft lost on process death (accepted) |
| D20 | Sunday & Wednesday: G.K end == R.K start (segments 7→8, 4→5). Both notifications post; sound dedupes |
| D21 | Offline-first. Network only for Geocoder naming (fallback "Current Location") |
| D22 | Exact alarms: check `canScheduleExactAlarms()` (12+); banner + deep link when revoked; degraded inexact fallback with visible notice |

---

## K1 — Project scaffold
Single-activity Compose M3, Navigation, DataStore Preferences, ViewModel per screen. Theme: light/dark.
- **EH:** DataStore read failure → in-memory defaults, app still opens.
- **LOG:** `Logger.e` on store read failure with cause.
- **TEST:** JUnit — defaults mapper. Device — cold start lands on main screen with last/default location.

## K2 — Solar engine port (SolarCalculator.kt)
Line-for-line port of `solar.py` (NOAA). Helpers: `Double.mod360()` (always non-negative), `Math.floorDiv` — bare `%` and integer `/` banned in this file. `Double` only; explicit `toRadians`/`toDegrees`. Zeniths 90.000 / 90.833. Two-pass refinement kept.
Golden vectors: JSON exported from `solar.py` — 8 cities × 366 days, both conventions, plus solstices/equinoxes and one polar row; ship as test resource.
- **EH:** total function — `SunResult.Times | NoSunrise | NoSunset` (|cos ha|>1); invalid tz id → device tz fallback.
- **LOG:** `Logger.e` on tz fallback and vector-resource load failure.
- **TEST:** JUnit — every vector ±1 s (build gate); negative-mod and floor-div cases. Instrumented — same vectors on device. Device checklist — 3 dates vs Drik Panchang (Kolkata, Kalyan, Pune).

## K3 — Kalam engine
`SEGMENT` map (RK 2,7,5,6,4,3,8 · YG 4,3,2,1,7,6,5 · GK 6,5,4,3,2,1,7 Mon→Sun). `computeDay(loc, date, convention)` → per-kalam (start, end, segment) + sunrise/sunset. Pure.
- **EH:** propagate `NoSunrise/NoSunset` as `DayResult.Unavailable(reason)` — never throw to UI.
- **LOG:** none (pure); guards logged by callers.
- **TEST:** JUnit — full 7-weekday × 3-kalam mapping table; Sun/Wed adjacency asserted (D20); segment-1 start == sunrise, segment-8 end == sunset. Device — one day cross-checked against the ICS feed.

## K4 — Data layer
Bundled cities constant (D1, with tz `Asia/Kolkata`). `SavedLocation(id, name, lat, lon, tz, createdAt)` — kotlinx-serialized list in DataStore, cap 20. Persisted: active location ref, full settings. Cache: in-memory map keyed (locationKey, date) holding both conventions, D0–D9, invalidated on midnight/location change.
- **EH:** corrupt store → reset that key to default, toast once; cap breach → save blocked with message; cache miss → compute (cache is optimization only).
- **LOG:** `Logger.e` on parse failure (key name, cause), cap-hit event.
- **TEST:** JUnit — round-trip, cap enforcement, invalidation matrix. Device — persistence quartet: set location/settings → verify → kill+reopen → verify → reboot → verify.

## K5 — Main screen
Rows R.K → Y.G → G.K (hidden rows removed). Each row: name, alarm-bound time range with bell icon when any slot armed; secondary convention line (smaller, labelled) when show-both on. Grey-out when end < now (alarm convention). Header: location name, date, sunrise/sunset line. Settings icon top-right.
- **EH:** `Unavailable` day → honest message row, no crash.
- **LOG:** none.
- **TEST:** Compose tests — visibility, grey-out, icon placement follows convention, show-both off hides secondary. Device — toggle each kalam; change convention → icon moves after Save.

## K6 — Date navigation
HorizontalPager, 431 pages (−30…+400 from today). Today/Tomorrow buttons; DatePicker bounded to same range. Midnight while open: window re-anchors, rows recompute, alarms re-arm (K9).
- **EH:** out-of-range pick clamped + toast.
- **LOG:** `Logger.e` if rollover recompute fails.
- **TEST:** JUnit — page↔date math incl. both bounds, DST-less IST sanity. Device — swipe to both limits; Today/Tomorrow from far pages; leave open across midnight → header advances; past days grey.

## K7 — Location management
List screen: bundled section (asc), separator, saved section (asc). GPS entry: FINE runtime permission → `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` with 15 s timeout → async Geocoder name (locality → "Current Location" fallback) → editable name → Re-fetch (repeatable) → Save (cap 20). Saved entries frozen (D2). Delete immediate + undo; deleting the active location falls back to default location and re-arms. Timezone for GPS saves: device zone.
- **EH:** permission denied → GPS row disabled with rationale; fetch null/timeout → retry state; Geocoder throw/null → fallback name.
- **LOG:** `Logger.e` on fetch failure, geocode failure, cap hit, active-delete fallback.
- **TEST:** JUnit — name fallback, cap, active-delete fallback. Device — deny path; airplane-mode geocode fallback; re-fetch updates coords pre-save; save→kill→reopen restores; delete active → default; undo restores.

## K8 — Settings (staged)
Top bar: Save / Discard (disabled when clean). Draft in ViewModel; dirty back-press → prompt (Save/Discard/Cancel). Save = atomic DataStore commit → alarm re-arm. Deletes bypass staging (D19).
Inventory: default location; saved-locations manager (own screen ≥6 entries); per-kalam visibility ×3; alarm convention; show-both; 6 slot toggles; 3 start offsets; start style; end style; ring duration; Error Logs entry; About entry.
Grouping: collapsible section per kalam for slot controls.
- **EH:** commit failure → draft retained, error toast.
- **LOG:** `Logger.e` on commit failure.
- **TEST:** JUnit — dirty reducer, discard revert. Device — all three back-press paths; Save applies visibly; Discard reverts; process-death mid-edit loses draft (accepted); delete + undo.

## K9 — Alarm scheduler
6 stable request codes. Arm today-only at: app start, Save, midnight, BOOT_COMPLETED, TIMEZONE_CHANGED, TIME_SET, location change. Skip past fire-times. Hidden kalam ⇒ both its slots suppressed. `setExactAndAllowWhileIdle`. 12+: `canScheduleExactAlarms()` → banner + `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` deep link; revoked → `setWindow` fallback + persistent "alarms approximate" banner. 13+: `POST_NOTIFICATIONS` request with rationale.
- **EH:** every receiver body guarded; scheduling `SecurityException` → fallback path, never crash.
- **LOG:** `Logger.e` on grant-revoked fallback, schedule failure, receiver exception; debug summary per re-arm.
- **TEST:** JUnit — slot-time matrix (offsets, OnTime, pre-sunrise as-is, Sun/Wed collision, suppression by hidden/off). Device — fires app-killed; reboot re-arm; tz change; revoke grant → banner; Doze (`adb deviceidle`) delivery.

## K10 — Alert delivery
Channel `kalam_alerts` IMPORTANCE_HIGH, channel sound OFF, vibration ON. Receiver posts notification; if style=Sound+Vibrate, starts SoundService (FGS, exemption: alarm) → MediaPlayer, notification audio attributes (D17), plays ring-duration seconds → stopSelf. Stop also on Dismiss action and sheet dismissal. Playback dedupe: request ignored while playing (D20) — both notifications still post. Foreground app: bottom sheet (D16) + sound, no duplicate notification. Titles: "R.K starts 11:42" / "R.K ended".
- **EH:** MediaPlayer failure → vibrate-only; FGS start denied → notification-only.
- **LOG:** `Logger.e` on playback failure, FGS denial, dedupe hit (debug).
- **TEST:** JUnit — style/duration selection matrix. Device — fg sheet (tap-out, swipe, button); bg heads-up screen-off; Dismiss cuts sound instantly; DND on → silent; durations 3 s and 30 s; Sun or Wed collision → two notifications, one sound.

## K11 — Diagnostics
`Logger` wrapper → logcat + capped ring-buffer file. Error Logs screen (from settings). "Report Bug/Crash" → email intent to kri.subsc@gmail.com with log attached.
- **EH:** all file IO guarded; logging never crashes the app.
- **LOG:** self-hosting.
- **TEST:** JUnit — ring-buffer rotation. Device — induce a guarded failure → visible in Error Logs → email intent carries attachment.

## K12 — About
Standard block, always expanded: Krishna Dipayan Bhunia · "Suggestion / Feedback / Bugs / Error" → kri.subsc@gmail.com (tappable) · LinkedIn · Facebook (tappable) · version + release date `dd-MMM-yyyy`. Plus one line naming the two sunrise conventions and the current alarm binding.
- **EH:** no mail app → chooser fallback toast.
- **LOG:** none.
- **TEST:** Device — every link opens; version string matches build.

## K13 — Version & packaging note
v1.0, versionCode 1. Debug keystore for sideload until Play decision; `READ_CALL_LOG`-class concerns not applicable here. **No APK until trigger word.**

---

## Deferred (v2 candidates)
Home-screen widget · ringtone picker · per-kalam ring duration · looping-until-dismiss · end-offset alerts · DND override · Play Store data-safety pass.

---

## K14 — Week tab
Monday-anchored always; pager −1…+5 weeks; one kalam via chips (colour-matched); columns Date | Day | Time; header names week convention (own setting); today accent row, past rows muted; hidden kalams excluded from chips; last-viewed kalam persisted.
- **EH:** Unavailable day renders a dash row; chip list empty (all hidden) degrades to R.K.
- **LOG:** Logger.e on compute failure via engine guards.
- **TEST:** JUnit — weekday mapping + boundary tests (EngineTest). Device — DW1 chip switch recolours table; DW2 swipe stops at −1 and +5; DW3 convention setting changes header + times after Save; DW4 hidden kalam absent from chips.

## K15 — Prokerala client (narrowed: kundli-matching only)
OAuth2 client-credentials token (memory-cached, 401 invalidates); kundli-matching endpoint, Lahiri fixed; SHA-1 pair-key permanent cache (unchanged pair = zero credits); tolerant parser; absent fields -> NONE list.
- **EH:** blank credentials, token failure, 401, non-200, network, parse — each returns a typed Err with a user-readable message; never throws to UI.
- **LOG:** Logger.e on every failure path with HTTP code.
- **TEST:** parser exercised via result rendering (sample values); Device — DM1 bad credentials -> friendly 401 message; DM2 airplane mode -> network error card; DM3 repeat match -> "cached" tag, no credit spend.

## K16 — Match tab
Boy card read-only (defaults hardcoded, editable in settings); girl form with last-entry retention + Clear; TOB-unknown -> 12:00 + amber approximate stamp (propagates to history as "approx"); POB geocode with shown coordinates + manual lat/lon fallback (bounds-validated, future DOB rejected); result: score/36, verdict, SrNo. koota table, zero-point rows in alert colour, NONE rows; named history, delete+undo.
- **EH:** geocoder null/throw -> manual path message; invalid manual coords rejected with log; save requires resolved coordinates.
- **LOG:** Logger.e on geocode failure, rejected inputs.
- **TEST:** Device — DK1 boy card matches settings; DK2 unknown-TOB stamp shown and "approx" lands in history; DK3 geocode offline -> manual entry works end-to-end; DK4 history delete + undo; DK5 Clear empties form.

## K17 — Lunar engine
Truncated Meeus (30 lon/dist + 20 lat terms + additives); topocentric altitude with parallax; h0 = 0.7275·hp − 34′; 10-min scan + 22-step bisection; phase from Sun–Moon elongation. Prototype validated vs ephem: 348 events, worst 24 s. Kotlin port gated by 360 golden vectors ±2 s + illumination ±0.1 %.
- **EH:** total functions — Events(null,null) and Phase("Unavailable") on any throw; no-rise/no-set days are honest nulls rendered as dashes.
- **LOG:** Logger.e on compute failure.
- **TEST:** JUnit — LunarTest vectors (pass). Device — DL1 moon row matches Drik Panchang ±2 min; DL2 a no-moonrise date shows a dash.

---

## Release record — v1.0 (10-Aug-2026)
| Item | Verification |
|---|---|
| Solar gate | 1,344 vectors, ±1 s, pass |
| Lunar gate | 360 vectors, ±2 s + illum ±0.1 %, pass |
| Engine | mapping 21/21; Sun+Wed collision exact after boundary-sharing fix; segment-1/8 boundaries exact |
| Bug fixed in release | independent truncation made adjacent segment boundaries differ by 1 s — boundaries now computed once and shared |
| Compile | assembleDebug clean; Kotlin zero warnings surfaced as errors |
| APK | Kalam-1.0.apk · com.krishna.kalam · versionCode 1 · minSdk 26 / target 34 · debug-signed (sideload) |
| NOT verified | anything device-only — see DEVICE-CHECKLIST.md; runs D1–D24 pending on Krishna's hardware |
Status: K1–K13 delivered; K14–K17 delivered. Deferred list unchanged.

---

## v1.1 — approved via "Release" 10-Aug-2026 (designs r1–r3 §UI + home, delivered as HTML)

### K18 — Four-tier font ladder (Model A)
Display 20 (16–28) · Header 16 (13–24) · Sub-header 14 (12–22) · Data 13 (10–20); 1-pt steps; staged draft; live preview; whole Settings screen previews the draft ladder. Model A: cascade only when the ≥1-pt invariant breaks; a change whose cascade would leave any tier's range is rejected (stepper disabled); cascade shows an info strip; Discard reverts cascade, no per-step undo.
- Exceptions: `Ladder.change` returns null on any infeasible move — UI cannot commit an invalid ladder; decode falls back to defaults 20/16/14/13 on corrupt prefs.
- Logging: prefs decode failure → `Logger.e` (existing path covers ladder fields).
- Tests: `LadderTest` ×5 (cascade up/down, no-move, range-reject both ends, invariant after random walk) — green. Device: D-v1.1-1..3.

### K19 — Settings: 5 feature tabs + collapsible decks + ← back
Tabs Display · Alerts · Location · Match · More; chip decks collapsed by default, last-open remembered per tab (session); `BackBar` with Material ← on Settings and every sub-screen.
- Exceptions: unknown deck state harmless (map lookup); dirty-exit guarded by Save/Discard dialog as v1.0.
- Logging: settings commit failure logged, draft kept.
- Tests: state logic is Compose-side; covered by device checks D-v1.1-4..5.

### K20 — Colour rules
Zebra = onSurface 4% overlay on >6-row tables and long lists (week, history, saved, error logs, guna); Week header band = active-kalam tint; koota + sky headers #E9EEF9 / #123472 fixed both modes; alert decks kalam-tinted; canvas neutral.
- Exceptions: none (pure styling). Logging: n/a. Tests: visual — device D-v1.1-6.

### K21 — Advanced match (details + full guna + summary)
`/v2/astrology/kundli-matching/advanced`; details table (Name/DOB/TOB/Place/Janma Rasi/Rasi Lord/Janma Nakshatra/System Used, Girl|Boy), 8-guna table (girl/boy classes, points/max, 0-point red), dosha chips (red Is Manglik · green Not · yellow else/unknown), union band traffic-lit; girl Name optional field (= default history label); `boyName` setting (default "Krishna"); cache-key v2 → old pairs refetch once; history rows store cacheKey and reopen the full result.
- Exceptions: tolerant regex parser — every absent field degrades to NONE/—; 401/404/HTTP/network each get a distinct user message; garbage payload → "format not recognised", nothing cached.
- Logging: token failure, HTTP codes, parse failure, legacy history rows without cache all `Logger.e`.
- Tests: `ParserTest` ×4 (full synthetic advanced payload incl. 27/36 shape, degrade-to-NONE, slight→yellow, garbage-reject) — green. **Honest flag: parser is verified against a synthetic payload only — no live advanced…17856 tokens truncated…ve all four; sheet state hoisted to SkyTable scope.
- Storage: Settings blob 49→55 (moonrise/moonset × alarm, lead, days).

### Release record — v1.19
| Item | Value |
|---|---|
| Artifact | Kalam-1.19.apk · versionCode 20 · debug-signed · in-place installable |
| Gates | **113/113 green** (109 + MoonAlarmTest 4) + version gate |
| Badging | Delivered APK 20/"1.19"; zip CODE_MOONRISE verified; md5 distinct from 1.18 |
| Verified | compile + unit; NOT device-verified |

---

## v1.20 — released 17-Sep-2026 (K78 bug fix: sky alarms were notifications, not alarms)

### Bug (Krishna, 17-Sep): sun/moon alarms only notified; expected a regular alarm like a morning alarm.
Root cause — three layers, all in the fire path:
1. AlarmReceiver gated sound on s.startStyleSound / s.endStyleSound (KALAM settings). Sky slots inherited startStyleSound; with it off, they made no sound at all — pure notification.
2. SoundService used USAGE_NOTIFICATION + TYPE_NOTIFICATION ringtone → notification-stream chime, silenced by notification muting and inaudible next to a real alarm.
3. Playback stopped after s.ringSeconds (~5s) with no dismiss affordance — an alert, not an alarm.

### Fix (sky slots only; kalam alerts unchanged)
- AlarmScheduler.SKY_CODES = {6,7,8,9}; the receiver routes those to the real-alarm path.
- Sky alarms ALWAYS ring — never gated by the kalam style flags.
- SoundService alarm mode: default ALARM ringtone (fallback ringtone → notification) on USAGE_ALARM, looping, repeating vibration waveform (700/600 ms, cancelled on stop), rings until dismissed with a 5-minute runaway cap (RING_CAP_SECS, a guard not the intended stop).
- New channel "kalam_alarms_v1" (IMPORTANCE_HIGH, app-managed sound/vibration) + full-screen alarm notification: CATEGORY_ALARM, PRIORITY_MAX, ongoing, setFullScreenIntent (wakes the screen), "Dismiss" action.
- AlarmActionReceiver handles Dismiss: stops the sound and clears the sky alarm notifications; the in-app AlertSheet dismiss now routes through the same path, so one dismiss always does both.
- Manifest: USE_FULL_SCREEN_INTENT + the new receiver.

### Release record — v1.20
| Item | Value |
|---|---|
| Artifact | Kalam-1.20.apk · versionCode 21 · debug-signed · in-place installable |
| Gates | **116/116 green** (113 + SkyAlarmModeTest 3) + version gate |
| Badging | Delivered APK 21/"1.20"; USE_FULL_SCREEN_INTENT present in the APK; USAGE_ALARM in shipped source; md5 distinct from 1.19 |
| Verified | compile + unit + APK permission check; ringing itself is NOT unit-testable — device proof required |
| Honest limits | (a) Full-screen intent on Android 14+ is granted by the OS to alarm-type apps; if denied it degrades to a heads-up notification, but the sound/vibration still ring. (b) Snooze was NOT added — Dismiss only; a snooze re-fire would collide with the FiredLedger duplicate-suppression key, so it needs its own design round (queue K79 if wanted). (c) Ring cap 5 min is fixed, not yet user-configurable. |

### v1.20 DEFECT (Krishna 17-Sep): sky alarm could not be dismissed — rang in public with no reachable control.
Root cause: (1) Dismiss existed only as a notification action built with icon id 0, which many builds do not render and which is hidden until the notification is expanded; (2) the full-screen intent pointed at the ordinary launcher screen, so waking the phone showed no stop control (the in-app sheet only appears when the app was already foreground); (3) the notification is ongoing, so it cannot be swiped away; (4) 5-minute ring cap made the exposure long. Workaround given to Krishna: Settings → Apps → Kalam → Force stop.
→ K79 queued, design 1.20.D1: dedicated full-screen alarm activity (shows over lock screen, turns screen on) with large STOP + Snooze, volume/power key silences, ring cap 60 s, snooze on codes 10–13 with sequence-keyed ledger, notification fallback with a real-icon Dismiss.

---

## v1.21 — released 17-Sep-2026 (K79 — alarm popup screen, fixes the v1.20 dismiss defect)

### Fix
- NEW ui/AlarmActivity: full-screen alarm screen, launched by the receiver for every sky/snooze fire. setShowWhenLocked + setTurnScreenOn + manifest showOnLockScreen/turnScreenOn, singleTask, excludeFromRecents, own taskAffinity. Big red STOP (54dp) + Snooze; shows time, date, event title/body and the snooze count.
- STOP is reachable four ways: the button, VOLUME up/down/mute, POWER, and back — each stops sound, cancels vibration (SoundService.onDestroy) and clears all sky/snooze notifications.
- Notification is now only the FALLBACK: its action uses a real icon (was icon id 0, which did not render — the core of the defect) and its content/full-screen intent points at AlarmActivity instead of the app launcher screen.
- Ring cap cut 300 s → 60 s.
- Snooze: codes 10–13 (sky base + 4), options 5/10/15 min, max 3 consecutive, never crosses midnight (dropped with a log line). FiredLedger keys gain an optional "#seq" so repeat snoozes are each allowed while genuine duplicate deliveries stay suppressed; prune handles the new key shape.
- SkyAlarmModeTest's cap expectation updated from 300 to 60 (the K78 contract was deliberately superseded).

### Release record — v1.21
| Item | Value |
|---|---|
| Artifact | Kalam-1.21.apk · versionCode 22 · debug-signed · in-place installable |
| Gates | **120/120 green** (116 + SnoozeTest 4) + version gate |
| Badging | Delivered APK 22/"1.21"; aapt xmltree confirms ui.AlarmActivity AND showOnLockScreen inside the delivered APK; md5 distinct from 1.20 |
| Verified | compile + unit + APK manifest inspection; the screen appearing over a locked phone is device-only proof |

### 1.21.D1 — **REJECTED outright (Krishna 28-Sep): "Completely rejected and ignore."**
K80 (Date Bridge content in the Day tab) and K81 (per-group font sizes) are dropped. No part of that design is to be carried forward, and the Date Bridge screenshots are not a reference for Kalam. Kalam v1.21 stands as released; the Day tab, its Bengali/tithi lines and the existing font settings stay exactly as they are. The calendar-authority question raised in that design (drik vs Surya Siddhanta) is dropped with it — Kalam keeps its drikpanchang-verified Lahiri engine unless Krishna raises it himself.

### Bengali date "bug" (Krishna 28-Sep: app shows 11 Ashwin, expected 10) — ROOT-CAUSED, NOT A DEFECT
drikpanchang (Kolkata, fetched 28-Sep) confirms Kalam exactly: 17 Sep = 31 Bhadro, 18 Sep = 1 Ashshin, 28 Sep = 11 Ashshin. Kanya sankranti is 17 Sep 07:58 IST (drik); the WB day-after rule puts 1 Ashshin on 18 Sep. Surya-Siddhanta almanacs (Gupta Press, Date Bridge) place the sankranti ~1 day later → 1 Ashwin on 19 Sep → 10 Ashwin today. Two traditions, one-day gap near month boundaries only. Kalam's engine is drik/Lahiri and drikpanchang-verified; nothing is mis-mapped.
→ **K82 QUEUED (Krishna: "Add to queue")**: Calendar-authority setting — Drik (current, default) / Traditional Surya Siddhanta — with its own golden vectors. Awaiting design round.

### K83 — GitHub release channel + in-app self-update (Krishna 28-Sep) — QUEUED, design 1.21.D2
Repo krishnabhunia/kalam-calendar exists (public, push OK from Krishna's side) but is the ICS-feed project (kalam_ics.py, rk/yg/gk.ics, monthly refresh workflow) with ZERO GitHub Releases and no Android source. Session has clone/read; push from this session is REFUSED (Claude GitHub App not installed for the org) — Krishna must push/publish, or install the app.
## 1.21.1 beta — traditional Midnapore calendar correction (9 October 2026)

- Change Bengali civil dates from Drik/Lahiri to bundled traditional West Bengal
  almanac boundaries: 9 October 2026 is 21 Ashwin 1433. Both Day and Week share
  the same lookup; this is not a blanket one-day subtraction.
- Label the calendar authority in Day. Bundled years 1432–1434 cover 15 April
  2025–14 April 2028. Dates outside coverage explicitly say unavailable.
- Retain the existing tithi/sunrise/solar computations. Record calendar sources
  and the distinction between Drik, traditional West Bengal, and Bangladesh.
- Add reported-date, boundary, full-coverage, timezone and unavailable-date tests.
- Add one Android workflow with automatic bugfix semantic versions, PR betas,
  stable releases on main, and one ZIP containing `Android/Kalam_<version>.apk`.
## 1.22.0 beta — keep clocks across reinstalling (9 October 2026)

- Request Android's Keep app data uninstall option on supported phones.
- Add clock-only JSON backup and restore using the system document picker;
  a file saved in Downloads survives uninstalling Kalam.
- Preserve all clock identities, locations, aliases, expanded states and
  ordering/grouping preferences; restore confirms the count and commits atomically.
- Reject damaged/foreign/oversized backups before modifying existing data.
- Document that a backup is needed before uninstalling and that automatic
  Android restore depends on device backup settings and stable signing identity.
- Allocate a minor semantic version for this feature. Device reinstall checks
  are still pending; no claim of unconditional automatic restore is made.
# 1.24.0 beta — Android updates and permanent signing (10 October 2026)

- Confirm different signing certificates in the published 1.22.0 and 1.23.0 APKs.
- Require one protected signing key and pinned certificate for installable
  workflow artifacts; refuse publication when signing is not configured.
- Add a collapsible App updates section in Settings with installed version,
  manual GitHub check, persisted optional beta switch, verified download and
  Android installer. Check once on app startup.
- Validate semantic version/channel, ZIP digest/size, exact APK payload,
  package, versionCode/versionName and installed certificate before installation.
- Keep PR build tokens read-only, publish stable releases after merge, and allow
  maintainers to explicitly publish optional beta releases with workflow dispatch.
- Produce one directly downloadable ZIP and tag source with delivered metadata.
- Resolve release lint failure caused by the old Fragment dependency from Play Services.
- Local verification: 137 Android tests, 7 Python tests, release APK build/lint,
  correct package/version/non-debuggable manifest and two identical signing runs
  with a local test key. Permanent production signing setup and real-device
  install/update/data-retention checks remain pending.
# Audit fixes — Android 9 updater, alarm reliability, crash fixes (13 October 2026)

Follows 1.24.0. Version is allocated by CI from the commit types (fix → patch).

| SrNo. | Area | Fix |
| --- | --- | --- |
| 1 | Updater | Android 9: the downloaded APK's certificates were never read (`GET_SIGNATURES` missing), so every update would have been refused as "different signing key". |
| 2 | Updater | Key rotation (APK Signature Scheme v3) is accepted when the new key's lineage contains the installed certificate; plain key changes are still refused. Verification runs off the main thread with Download/Install disabled meanwhile. A re-check keeps a verified download of the same version. |
| 3 | CI | `tools/check_apk.py` refuses a debuggable APK, a foreign package or a wrong versionCode/versionName for both the validation and the signed APK; a test pins that only `signed-release` reads secrets and never runs for PRs. |
| 4 | Crash | Settings → Match → My details → DOB/TOB crashed on a fresh install (blank values, K84) — the state right after the signing-key reinstall. |
| 5 | Alarms | Deleting the saved place that was active and/or default left a dangling id; `rearmToday` returned silently and no alarm was armed again. Alarms, the fired-ledger day and the main screen now share one place-resolution rule; UNDO restores the previous active/default choice. |
| 6 | Alarms | Next-day re-arm self-alarm 30 s after the place's midnight, only while some alert is enabled (DATE_CHANGED is not delivered on Android 8+; the 30 s ticker needs the app open). MY_PACKAGE_REPLACED re-arms after an update. BootReceiver and AlarmReceiver keep the process alive (goAsync) until arming has finished. |
| 7 | Alarms | STOP from the notification/sheet now clears snoozed alarm cards (210–213). Power key stops the alarm (screen-off; ignored after the 60 s cap and for 1.5 s after the screen wakes). API 26 lock-screen flags. The screen may sleep after the cap; the alarm card stays. |
| 8 | Logs | Logger is initialised in an Application class, so background alarm events reach Error logs. |
| 9 | Match | Times are written with ASCII digits (Bengali/Marathi phone language produced `০৯:৩০` and an HTTP 400); legacy values are repaired before sending; Prokerala calls time out (15 s/30 s). Cache keys are unchanged. |
| 10 | Backup | Restore rejects duplicate clock locations (Clocks list crash) and deeply nested or non-standard JSON (single quotes/comments) that made Android's parser overflow its stack (crash). |
| 11 | Data | A corrupt prefs file resets to defaults instead of crashing on every launch. |
| 12 | Engines | Solar: DST-change days were shifted by one hour in DST zones (India unchanged; golden vectors still pass). Tithi and the duration preview recompute when the place/convention changes. |
| 13 | Docs | 1.22/1.23 migration runbook (do **not** keep app data), required reviewer on `android-signing`, clipboard-history warning. |

Local verification: Android unit tests and Python tool tests (see PR). Not verified on a device: everything in DEVICE-CHECKLIST "Audit fixes".
