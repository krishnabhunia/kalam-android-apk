# Kalam v1.0 — device checklist

Debug-signed sideload build. Enable "Install unknown apps" for your file manager, install `Kalam-1.0.apk`.
Persistence quartet applies where marked ⟳: act → verify → kill+reopen → verify → reboot → verify.

## First run
| # | Check | Expect |
|---|---|---|
| D1 | Fresh install, open | Day tab, Kolkata, today; R.K/Y.G/G.K in fixed order; sun/moon table at bottom with Sun and Moon labels |
| D2 | Notification permission dialog (Android 13+) | Appears once; deny → alerts silent, Error Logs records it |
| D3 | Compare today vs Drik Panchang Kolkata | Kalam times within ~1 min; moonrise/set within ~2 min |

## Day tab
| # | Check | Expect |
|---|---|---|
| D4 | Swipe back / forward | Stops at −30 and +400 |
| D5 | Today / Tomorrow pills from a far page | Jump correctly |
| D6 | Leave open across midnight | Header advances, rows recompute, alarms re-arm |
| D7 | Past kalam today | Greyed, strikethrough, "Ended" |
| D8 | Show-both toggle (Settings→Save) | Secondary observational line appears/disappears |
| D9 | A no-moonrise date (~once a month; try swiping near new-moon dates) | Dash in Rise cell, no crash |

## Week tab
| # | Check | Expect |
|---|---|---|
| D10 | Chips R.K/Y.G/G.K | Table recolours + retimes; last chip remembered ⟳ |
| D11 | Swipe | Stops at 1 back, 5 ahead; Monday-anchored; today row accented |
| D12 | Week convention ≠ alarm convention | Header label + times follow the week setting only |

## Location
| # | Check | Expect |
|---|---|---|
| D13 | Pick each bundled city | Times shift correctly (Kolkata vs Mumbai ~1 h) ⟳ active persists |
| D14 | GPS: deny permission | Row disabled state, log entry, no crash |
| D15 | GPS: grant → fetch → Re-fetch ×2 → edit name → Save | Coordinates update on each re-fetch; saved entry frozen (no re-fetch affordance) |
| D16 | Airplane mode during GPS save | Name falls back to "Current Location" |
| D17 | Save 20 locations, try 21st | Blocked with cap message |
| D18 | Delete the active saved location | Falls back to default location, alarms re-arm, undo restores |

## Alarms & alerts
| # | Check | Expect |
|---|---|---|
| D19 | Slot fires app-killed, screen off | Heads-up notification + sound for ring-duration seconds |
| D20 | Slot fires app foreground | Bottom sheet ≤50 %, Dismiss cuts sound instantly, no duplicate notification |
| D21 | Reboot before a pending slot | Fires after reboot (BootReceiver re-arm) |
| D22 | Revoke exact-alarm grant (system settings) | Amber banner appears; Allow deep-links; alarms degrade inexact |
| D23 | Wednesday or Sunday with G.K-end + R.K-start on | Two notifications, one sound |
| D24 | DND on when a slot fires | Silent (no DND override) |

## Match
| # | Check | Expect |
|---|---|---|
| DM1 | Wrong credentials | Friendly 401 message, logged |
| DM2 | Airplane mode | Network error card, retry works after |
| DM3 | Same pair twice | Second result tagged "cached" |
| DK1–DK5 | Per BACKLOG K16 | Boy card, approx stamp, manual coords, history delete+undo, Clear |

## Known limits (by design)
- Debug-signed: Play Protect may warn on install — expected for sideload.
- No full-screen alarm: face-down phone gets sound + banner, not a wake-up (D16 decision).
- OEM battery killers (Xiaomi/Oppo/Vivo): whitelist the app or alarms may be delayed — same as Remindly.
- DATE_CHANGED broadcast is unreliable on modern Android; midnight re-arm is covered by the in-app 30 s ticker and each alarm's post-fire re-arm.

---

## v1.1 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.1-1 | Ladder cascade up | Display→Text size: raise Sub-header 14→16 | Header auto-moves 16→17, info strip names the push, preview grows |
| D-v1.1-2 | Ladder range stop | Raise tiers to Display 28 / Header 24 / Sub 22 / Data 20 | + steppers grey out; no tier ever ≤ the one below |
| D-v1.1-3 | Ladder staged | Change sizes → Discard | App text unchanged; reopen shows old values. Save → whole app rescales |
| D-v1.1-4 | Decks | Open a deck in Alerts, switch to Match, return | Alerts still shows that deck open; others collapsed |
| D-v1.1-5 | ← buttons | Tap ← on Settings, History, Saved, Logs, About, GPS | Each goes exactly one level up per the chain |
| D-v1.1-6 | Colours | Week (each kalam) + a >6-row history + match result | Header band matches kalam tint; zebra visible both light & dark; guna/sky header soft blue |
| D-v1.1-7 | Advanced match live | Run Match with real creds (girl 17 Nov 1990 12:40 Kolkata) | 27/36; details table Rasi/Lord/Nakshatra match website screenshot; 8 guna rows with classes |
| D-v1.1-8 | Dosha chips | Same result | Girl chip red "Is Manglik", boy green "Is Not Manglik", union band green "Very Good" |
| D-v1.1-9 | Parser degrade | If any field shows NONE/— | App stays stable; report which fields for parser patch |
| D-v1.1-10 | Cache v2 | Re-run same pair | "cached" tag, no credit spent; history row tap reopens full result |
| D-v1.1-11 | Justified tabs | Home; set ladder to max | Three equal tabs; labels fit (auto-shrink at max) |
| D-v1.1-12 | Duplicate guard | Save same name+DOB twice | Replace/Cancel dialog; Replace overwrites, count unchanged |
| D-v1.1-13 | Credential lock | Settings→Match→Prokerala API | Configured ✓, masked ID, hidden secret; Edit→confirm→fields; Save relocks |
| D-v1.1-14 | Icon | Fresh install (or clear launcher cache) | Sun-disc + wedge on royal blue in launcher; notification icon still bell |
| D-v1.1-15 | Back chain | Walk GPS→Picker→Home and every sub-screen | Matches amended K31 table; only Home exits; Match tab survives Settings round-trip |
| D-v1.1-16 | In-place upgrade | Install 1.1 over 1.0 (no uninstall) | All settings, saved places, alarms, history intact; old history rows open with "no cached payload" log only |

## v1.2 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.2-1 | Retro-fix | Install 1.2 over 1.1; open History → the saved v1.1 row | Full guna table populated from cache, no refetch, no credit spent |
| D-v1.2-2 | Wire names | Same result | Row 5 reads "Graha Maitri"; all 8 rows show girl/boy classes + points |
| D-v1.2-3 | Rasi Lord fix | Details table vs prokerala.com for same pair | Rasi Lord = rasi's lord with vedic name in brackets (not nakshatra's) |
| D-v1.2-4 | K34-A expand | Tap Nadi row → paragraph opens; tap again → closes | Only tapped row toggles; chevron flips |
| D-v1.2-5 | K37 persist | Expand 2 rows + More birth details → kill app → relaunch → reopen result | Same rows and section still open |
| D-v1.2-6 | K35 rows | More birth details expanded | Pada + Nakshatra Lord (vedic) match website |
| D-v1.2-7 | K36 chips | If a Manglik pair available | Red/yellow per has_exception; type suffix after "·" |
| D-v1.2-8 | Union band | Run a Nadi-0 pair (your console sample data) | Band RED despite high score (type "bad") |
| D-v1.2-9 | K33 back | Match → History › → open record → back ×2 | Lands on Match tab; Settings→History path still returns to Settings |
| D-v1.2-10 | Fingerprint | If any NONE remains anywhere | Error logs contain one "shape:" line naming keys — send it |

## v1.3 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.3-1 | Card duration | Day tab, default setting | Titles read "Rahu Kalam - 97 mins" style; name bold, duration regular; status plain |
| D-v1.3-2 | Format setting | Display▸Conventions▸Kalam duration → pick hours form → Save | Both option labels showed today's real duration; after Save all three cards read "1 hour 37 mins" style |
| D-v1.3-3 | Week follows | Same setting, open Week | Duration column header "Duration"; values match Day-card text; toggle back → both revert |
| D-v1.3-4 | Sky Dur | Day tab sky table vs Drik | Sun Dur = sunset−sunrise; Moon Dur matches manual civil-day calc incl. a cross-midnight day and a single-event day (—-event rows still show a duration) |
| D-v1.3-5 | K41 banner | Week, all three chips | Solid colour band with full name above header; light tint header beneath; readable in dark mode |
| D-v1.3-6 | Truncation | Max font ladder + hours format, Week | Duration and Time never clip; Date ellipsizes first |
| D-v1.3-7 | Upgrade | Install 1.3 over 1.2 | Settings/history/collapse-states intact; new setting defaults to mins |

## v1.4 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.4-1 | K42 layout | Day tab sky table, both conventions | "geometric/observational" under Sun; phase wrapped under Moon; Rise/Set/Dur headers centered over centered values; no clipped text at max ladder |
| D-v1.4-2 | K43 grid | Week, all kalams, both duration formats | Columns proportional + centered; "today" on its own line; hours form wraps (never clips) at max ladder |
| D-v1.4-3 | K44 save block | Run a match | Order Name → Matrimony ID → Mobile → Save; blank name disables with hint; Girl form has no Name field |
| D-v1.4-4 | K45 record | Save with ID + mobile → open from History | Contact block shows both values |
| D-v1.4-5 | K46 legacy | Open a pre-1.4 history row | Contact block shows "-" twice; result still renders |
| D-v1.4-6 | K47 restart | Match → kill app → reopen Match | Whole page restored (fields + result, "cached · restored"), no network; button "Saved ✓" iff that record exists in history |
| D-v1.4-7 | K47 clear | Tap Clear on Girl card | Form, save-block fields AND result vanish; restart shows empty Match tab |
| D-v1.4-8 | K47 derived state | Delete the saved record from History, return to Match | Button flips back to "Save to history" |
| D-v1.4-9 | Upgrade | Install 1.4 over 1.3 | Settings/alarms/history intact; new fields blank; old rows open fine |

## v1.5 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.5-1 | Picker fires | ~~superseded by v1.6~~ — v1.5 source cannot multi-hit (K49 reason) | see D-v1.6-1 |
| D-v1.5-2 | Pick lands | ~~superseded by v1.6~~ | see D-v1.6-2 |
| D-v1.5-3 | Cancel | Reopen picker → Cancel | Prior resolved state untouched |
| D-v1.5-4 | Single hit | POB "Bajkul" → Resolve | No dialog; readable label with state + country |
| D-v1.5-5 | Manual override | Enter lat/lon → Use manual coordinates | Label line drops to coords-only (no stale place name); persists after restart |
| D-v1.5-6 | Zero match | POB gibberish → Resolve | "Lookup failed — enter coordinates manually"; manual path works |
| D-v1.5-7 | Clear/upgrade | Clear wipes label; install 1.5 over 1.4 | Label empty on legacy data; everything else intact |


## v1.6 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.6-1 | Bijapur picker | Girl POB "Bijapur" → Resolve | Dialog: row 1 "Bijapur · Chhattisgarh · India", row 2 "Vijayapura · Karnataka · India" (India-state ascending; wire rename shown) |
| D-v1.6-2 | Pick lands | Tap a row | Resolved line = that label + coords; Match uses them |
| D-v1.6-3 | Alt-name | POB "Baroda" → Resolve | Vadodara-Gujarat FIRST; MP rows next; "Baroda · Michigan · United States" LAST (ordering law live) |
| D-v1.6-4 | Foreign twin honesty | POB "Hyderabad" then "Hyderabad, Pakistan" | Plain: India rows only (expected). Qualified: Sindh/Pakistan row appears (name may be native script, state/country English) |
| D-v1.6-5 | Fallback | Airplane mode → Resolve | Degrades to geocoder/failure text; no crash; Error logs show source |
| D-v1.6-6 | K50 boy | Settings ▸ My details: POB "Kolkata" → Resolve → Save | Coordinates MOVE (v1.0 defect gone); label persists after restart; Discard reverts |
| D-v1.6-7 | Boy manual | Enter lat/lon → Use manual | Label clears; values staged until Save |
| D-v1.6-8 | Upgrade | Install 1.6 over 1.5 | Everything intact; new labels blank until first resolve |

## v1.7 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.7-1 | Add world clock | Clocks → ＋ → search "Zurich" → pick Switzerland row | "Determining timezone…" then card appears: Europe/Zurich, plausible time vs timeanddate.com |
| D-v1.7-2 | Ordering in add | Search "Baroda" | Vadodara-Gujarat first … Michigan last (law live in the sheet) |
| D-v1.7-3 | Duration semantics | Card for a set-only-moon day | Moon shows — / time / partial duration; compare timeanddate.com moon |
| D-v1.7-4 | Polar degrade | Add "Tromsø" (or "Longyearbyen") | Sun rows may be — / — / — while Moon rows still show; no crash |
| D-v1.7-5 | tz failure honesty | Airplane mode → search added? No: pick a result with data off | Red "clock not added" message; retry works when online |
| D-v1.7-6 | Persistence + tick | Add 2 clocks → kill app → reopen; watch a minute boundary | Clocks intact (no network); times tick on the minute |
| D-v1.7-7 | Saved + dup + cap | Add a SAVED place; try re-adding; add to 10 | Saved adds instantly (no lookup); dup rows dim "added ✓"; button disables at 10 |
| D-v1.7-8 | Delete + snapshot | Delete the saved place in Location settings; check its clock | Clock survives (snapshot); 🗑 removes it |
| D-v1.7-9 | Upgrade | Install 1.7 over 1.6 | Everything intact; Clocks tab empty first run |

## v1.8 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.8-1 | Collapse toggle | Tap a chip, tap again | ▸/▾ flips; collapsed shows name · "HHmm hrs" · sun span · duration only |
| D-v1.8-2 | State persists | Expand one of three, kill app, reopen | Same one expanded, others collapsed |
| D-v1.8-3 | Upgrade default | Install 1.8 over 1.7 with existing clocks | All appear collapsed once |
| D-v1.8-4 | No-event chip | Polar clock in its no-sun season | Collapsed sun span and duration both "—"; expand still shows moon |
| D-v1.8-5 | K52 confirm | 🗑 → Cancel; 🗑 → Remove | Cancel keeps clock; Remove deletes; no direct-delete path anywhere |
| D-v1.8-6 | Ladder | Max font size | Chip wraps/never clips; tap targets usable |

## v1.9 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.9-1 | Temp appears | Open Clocks online | Temps fill in async beside names (warm red), both chip + expanded; "updated HH:mm" on expanded |
| D-v1.9-2 | Staleness gate | Re-enter tab within 30 min; after 30 min | No refetch first time (log shows 0 due); refetch after |
| D-v1.9-3 | Dim + absence | Airplane mode >1 h then open; fresh install offline | Values dim grey (last-known); absent entirely when never fetched — no fakes |
| D-v1.9-4 | Cache survives | Kill app, relaunch offline | Last-known temps show immediately |
| D-v1.9-5 | K55 scale | Add 20+ clocks, fling-scroll at max ladder | Smooth (lazy); minute tick no stutter; counter "n of 100"; add disabled only at 100 |
| D-v1.9-6 | Sanity | Compare 2-3 temps vs open-meteo.com | Within a degree |
| D-v1.9-7 | Upgrade | Install 1.9 over 1.8 | Clocks + states intact; temps start absent then fill |

## v1.10 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.10-1 | Pull refresh | Pull down on Clocks online | Spinner; ALL temps + captions update even if <30 min old (log "manual") |
| D-v1.10-2 | Offline pull | Airplane mode, pull | Spinner ends cleanly; last-known values intact; no crash |
| D-v1.10-3 | Single-flight | Pull twice fast | One batch only; no double log |
| D-v1.10-4 | Caption | Expand a card | "31° Updated 11/Aug/26 1632 hrs" beside temp; region line has NO updated tail |
| D-v1.10-5 | Absent | Clock never fetched | No temp, no caption |
| D-v1.10-6 | Upgrade | Install 1.10 over 1.9 | Clocks/temps intact; captions appear with existing cache times |

## v1.11 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.11-1 | Groups | Open Settings | Boxes in order Display/Alerts/Location/Match/Logs/About; no tab chips; BackBar + Save/Discard intact |
| D-v1.11-2 | Nothing lost | Walk every control in every box | Font ladder live-preview, duration format, conventions, alarm rows, active place + Saved places, My details/credentials/Clear — all present + functional |
| D-v1.11-3 | Logs box | Tap Error logs | Existing screen opens; back returns to Settings |
| D-v1.11-4 | About | Tap About header ×2; tap links | Expands ▾ with full block (mail/LinkedIn/Facebook open), collapses ▸; collapsed again after app restart |
| D-v1.11-5 | Deck behaviour | Open a deck in Display, then one in Alerts | Single-open across the page (opening the second closes the first) |
| D-v1.11-6 | Ladder scroll | Largest font | Long page scrolls; headers readable; nothing clipped |
| D-v1.11-7 | Upgrade | Install 1.11 over 1.10 | Settings values intact; dirty-guard (Save/Discard) still works |

## v1.12 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.12-1 | Caption style | Expand a clock card | "Updated …" italic, light brick shade beside the temp |
| D-v1.12-2 | No resting circle | Open Clocks, don't pull | NO refresh circle anywhere; appears only while pulling/refreshing, then vanishes |
| D-v1.12-3 | Clear logs | Error logs → Clear → Cancel; → Clear → Clear | Cancel keeps; confirm empties to "No errors logged", 0 entries, persists after restart; button disabled when empty |
| D-v1.12-4 | Accordion | Expand Display, then Alerts | Display auto-collapses; exactly one open; About stays open below regardless |
| D-v1.12-5 | About fixed | Tap About header repeatedly | Nothing happens — always expanded, links still tappable |
| D-v1.12-6 | Entry state | Leave Settings, re-enter | All five collapsed again |
| D-v1.12-7 | Upgrade | Install 1.12 over 1.11 | Settings values + clocks + temps intact |

## v1.13 additions — all PENDING on device (bug-fix proofs)

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.13-1 | About version | Settings → About | "Version 1.13 · 11-Aug-2026" |
| D-v1.13-2 | Single ring | Let a kalam start alert fire naturally | Rings ONCE for ring-seconds; one notification; Error logs may show "duplicate fire suppressed" (that's the fix working) — never a second ring |
| D-v1.13-3 | Rearm safety | After a fire, open the app (triggers rearm) | No re-ring; the remaining slots of the day still fire on time |
| D-v1.13-4 | Boot | Reboot mid-day after a slot fired | Fired slot does NOT re-ring; later slots do |
| D-v1.13-5 | Next day | Same slot next day | Fires normally (ledger is per-date) |
| D-v1.13-6 | Fail-open | (Info) if logs show ledger errors, alarms still ring | Ledger never silences a real alarm |

## v1.14 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.14-1 | About | Settings → About | "Version 1.14 · 10-Sep-2026" |
| D-v1.14-2 | Sort modes | Clocks → Sort chip: each of Added/Name/Time/Country | Order changes correctly; survives app restart |
| D-v1.14-3 | Grouping | Group chip: Country then Continent | Headers with counts; India first; Bajkul (saved) under "Other"; sort works inside groups |
| D-v1.14-4 | Bengali date today | Day tab | Both scripts under the Gregorian date; value matches drikpanchang for your location |
| D-v1.14-5 | Tithi today | Day tab | Paksha+tithi both scripts; "upto" time ≈ drikpanchang (±few min) |
| D-v1.14-6 | Week | Week tab | bn+en date stacked per row + tithi sub-line; a paksha flip mid-week reads correctly |
| D-v1.14-7 | Swipe days | Day tab ‹ › across a month boundary | Bengali month + day roll correctly (day resets to ১) |
| D-v1.14-8 | 3-date drikpanchang audit | Pick 3 dates incl. one near mid-month sankranti | Bengali date + tithi match drikpanchang for all 3 |

## v1.15 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.15-1 | About | Settings → About | "Version 1.15 · 14-Sep-2026" |
| D-v1.15-2 | Metrics grid | Expand a clock | Humidity/Wind/Precip/AQI grid under the time; values plausible vs any weather app |
| D-v1.15-3 | AQI chip | Expand Kolkata + a clean-air city | Band label + color differ sensibly (e.g. Moderate vs Good) |
| D-v1.15-4 | Collapsed unchanged | Collapse cards | Only name·temp°·time·sun line — no new rows |
| D-v1.15-5 | Cache upgrade | Update over v1.14 without clearing data | Old temps show instantly with "—" extras; next refresh fills all metrics |
| D-v1.15-6 | AQI-only failure | Airplane-mode mid-refresh or wait for a flaky moment | Weather still updates when AQI fails; grid shows "—" for AQI only |
| D-v1.15-7 | Pull-to-refresh | Pull down | All metrics + Updated caption refresh together |

## v1.16 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.16-1 | About | Settings → About | "Version 1.16 · 14-Sep-2026" |
| D-v1.16-2 | Two-stage collapse | Group by Country → tap "India" header twice | Tap 1: all Indian cards shrink to one-liners; tap 2: cards vanish, ▸ header remains |
| D-v1.16-3 | Two-stage expand | Tap the ▸ header twice | Tap 1: cards return collapsed; tap 2: all expand |
| D-v1.16-4 | Stage persistence | Set mixed stages, kill app, reopen | Stages restored per group |
| D-v1.16-5 | Independence | Collapse one group | Other groups + their per-card states untouched |
| D-v1.16-6 | Rename | Expand a clock → ✏️ → "Home" → Save | Title "Home", second line "Kolkata · …"; survives restart |
| D-v1.16-7 | Rename restore | ✏️ → clear text → Save | Original title back, region line back to normal |
| D-v1.16-8 | Alias sort | Sort: Name with an alias starting "A…" | Aliased clock sorts by its alias |
| D-v1.16-9 | Old-data upgrade | Install over v1.15 without clearing | Clocks intact (alias empty), groups all expanded |

## v1.17 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.17-1 | About | Settings → About | "Version 1.17 · 15-Sep-2026" |
| D-v1.17-2 | Six group modes | Cycle the Group chip through all six | Old three unchanged; new ones show nested, indented headers with counts |
| D-v1.17-3 | Nested collapse | Continent+Countries+State → tap "West Bengal" ×2, then "Asia" ×2 | Inner header cycles independently; Asia ▸ hides everything beneath incl. sub-headers |
| D-v1.17-4 | Path-scoped stages | Collapse "Other" under one continent | "Other" under another continent unaffected |
| D-v1.17-5 | Metric sort before data | Fresh install (or clear data), add clocks, pick Sort: Temperature | List in Added order, chip "waiting for data"; after refresh → sorted hottest first |
| D-v1.17-6 | Greyed entries | Open Sort menu before any AQI arrives | "Air quality (waiting for data)" disabled; enabled after refresh |
| D-v1.17-7 | Null sinks | One clock offline/no reading, Sort: Humidity | That clock last with "—" |
| D-v1.17-8 | Direction flip | Sort: Temperature → tap Temperature again | Order reverses, chip arrow ↓→↑; survives restart |
| D-v1.17-9 | Flip on Name | Sort: Name → tap again | Z–A |
| D-v1.17-10 | Reload jump | Pull-to-refresh under Temperature sort | Cards reorder on the reload (accepted) |
| D-v1.17-11 | Upgrade | Install over v1.16 without clearing | Sort/group/stages preserved; direction natural |

## v1.18 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.18-1 | About | Settings → About | "Version 1.18 · 17-Sep-2026" |
| D-v1.18-2 | Bells | Day tab, today | 🔕 under Rise and under Set; tap opens the sheet |
| D-v1.18-3 | Sunrise ring | Arm sunrise, lead = custom (few min ahead of now is impossible — use tomorrow), or set sunset with lead so it rings in ~3 min | Rings once at the expected time with "Sunset in X min · HH:MM"; foreground → sheet, background → notification + sound |
| D-v1.18-4 | Single ring | Keep app open across the ring | Exactly one ring (ledger) |
| D-v1.18-5 | Day mask | Set Mon+Wed only on a Thursday | Sheet preview says next Monday; no ring today |
| D-v1.18-6 | Weekends preset | Tap "Weekends" | Sat+Sun chips only; pill "… · Weekends" |
| D-v1.18-7 | Save gate | Untick all days | Save greyed, "Pick at least one day"; ticking one re-enables |
| D-v1.18-8 | Drift | Leave sunrise alarm on 2–3 days | Ring time shifts by the sunrise drift (compare with the Rise cell each morning) |
| D-v1.18-9 | Other days | Swipe to tomorrow | Bells greyed, "alarms are for today"; today's alarm unaffected |
| D-v1.18-10 | Boot | Reboot with an armed sun alarm | Still rings (BootReceiver re-arm) |
| D-v1.18-11 | Upgrade | Install over v1.17 without clearing | Both sun alarms default OFF; kalam alarms unchanged |

## v1.19 additions — all PENDING on device

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.19-1 | About | Settings → About | "Version 1.19 · 17-Sep-2026" |
| D-v1.19-2 | Moon bells | Day tab, today | 🔕 under Moon Rise and Moon Set; sheet titled "Moonrise alarm"/"Moonset alarm" |
| D-v1.19-3 | Moon ring | Arm whichever moon event is a few minutes ahead, lead 0 | Rings once: "Moonrise now · HH:MM" (or Moonset) |
| D-v1.19-4 | No-event day | Find a day showing "—" for moonrise (swipe ahead to locate, then wait for it) | No ring that day; preview had shown the following day |
| D-v1.19-5 | Drift | Keep a moon alarm on for 2 days | Ring time moves ~50 min later each day, matching the cell |
| D-v1.19-6 | Independence | Arm sunrise + moonset with different masks | Each rings on its own days only |
| D-v1.19-7 | Upgrade | Install over v1.18 without clearing | Sun alarms preserved; moon alarms default OFF |

## v1.20 additions — all PENDING on device (these are the decisive ones)

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.20-1 | About | Settings → About | "Version 1.20 · 17-Sep-2026" |
| D-v1.20-2 | Real alarm sound | Arm a sky alarm a few minutes ahead, lock the phone | Loud ALARM-stream ringtone (alarm volume, not notification), keeps ringing — not a single chime |
| D-v1.20-3 | Screen wake | Same, screen off | Full-screen alarm appears / screen lights up with Dismiss |
| D-v1.20-4 | Dismiss | Tap Dismiss | Sound and vibration stop immediately; ongoing notification clears |
| D-v1.20-5 | In-app dismiss | Ring with the app open, close the sheet | Sound stops AND no leftover alarm notification |
| D-v1.20-6 | Notification-mute | Silence notifications (not alarms), then ring | Still rings — proves the alarm stream is in use |
| D-v1.20-7 | Kalam alerts unchanged | Let a normal kalam slot fire | Same short alert behaviour as before (respects its style flags) |
| D-v1.20-8 | Runaway guard | Let it ring untouched | Stops by itself after ~5 minutes |

## v1.21 additions — PENDING (D-v1.21-1..4 are the defect proof)

| # | Check | Steps | Pass condition |
|---|---|---|---|
| D-v1.21-1 | Alarm screen on lock | Arm a sky alarm ~2 min ahead, lock the phone, pocket it | Screen wakes to the alarm box with a large STOP — no unlock needed |
| D-v1.21-2 | STOP works | Tap STOP | Sound + vibration stop instantly; notification gone |
| D-v1.21-3 | Volume-key stop | Let it ring, press volume down without looking | Stops immediately (the public-embarrassment path) |
| D-v1.21-4 | Auto-stop | Let it ring untouched | Silent by 60 s |
| D-v1.21-5 | Snooze | Tap Snooze → 5 min | Screen closes, rings again in 5 min showing "Snoozed 1/3" |
| D-v1.21-6 | Snooze limit | Snooze three times | Third snooze offers no further snooze; stops for the day |
| D-v1.21-7 | Ring while using the phone | Let it fire with the screen on | Alarm screen comes to the front with STOP |
| D-v1.21-8 | Notification fallback | If the screen ever fails to appear | Notification shows a visible STOP action that works |
# Android updater — pending real-device checks (10 October 2026)

- Configure the permanent protected signing key before publishing an installable APK.
- Back up existing clocks before any one-time change from an unavailable old key.
- Check a same-key, higher-code APK installs over Kalam and preserves clocks,
  saved places, settings, Match history and alarm configuration.
- Verify the App updates card starts collapsed with title/arrow only.
- Check stable-only default, persisted beta opt-in, explicit check, startup check,
  offline/rate-limit messaging, release notes and checksum/certificate errors.
- Verify permission handoff for installing unknown apps and the system install
  confirmation. Cancel permission/install; Kalam and its data must remain intact.
- Confirm a different-key APK is blocked before opening the installer.
