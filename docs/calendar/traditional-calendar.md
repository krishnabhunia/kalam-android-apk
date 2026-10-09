# Traditional West Bengal civil calendar

On 9 October 2026 the user's Midnapore almanac says **21 Ashwin 1433**.
The old engine produces 22 Ashshin because it uses astronomical Drik solar
longitude with Lahiri ayanamsa and a day-after-sankranti rule. It is not the
Bangladesh revised calendar. West Bengal has multiple almanac traditions;
Drik/Bisuddha Siddhanta and traditional Surya Siddhanta dates can differ.
The user's explicit request supersedes the earlier Drik choice in CLAUDE.md.

## Authority and coverage

The civil date now uses offline month starts from BengaliCalendar.com's
West Bengal calendar, matching the user's reported traditional date. This
is an almanac lookup, **not a new Surya Siddhanta astronomical model** and
not a claim that every West Bengal publisher uses identical dates.

Source read: 9 October 2026. Source URLs are recorded per month in
`app/src/test/resources/traditional-months.json`. Numeric date facts alone
are imported; festival text and tithi calculations are not imported.

- [Ashwin 1433](https://www.bengalicalendar.com/1433/ashshin):
  19 September = 1 Ashwin; 28 September = 10 Ashwin;
  9 October = 21 Ashwin.
- [Bhadro 1433](https://www.bengalicalendar.com/1433/bhadro):
  19 August = 1 Bhadro, so 18 September = 31 Bhadro.
- [Kartik 1433](https://www.bengalicalendar.com/1433/kartik):
  19 October = 1 Kartik; the preceding Ashwin month is 30 days.
- [Boishakh 1433](https://www.bengalicalendar.com/1433/boishakh):
  15 April 2026 = 1 Boishakh 1433.
- [Drik comparison for Kolkata](https://www.drikpanchang.com/bengali/bengali-month-panjika.html?geoname-id=1275004&date=09/10/2026):
  9 October = 22 Ashshin in the Bisuddhasiddhanta calendar.

Bundled years: **1432–1434**, covering **15 April 2025–14 April 2028**.
Outside that range the UI explicitly says the traditional date is unavailable;
it does not silently revert to Drik or Bangladesh dates. Refresh the bundled
almanac before coverage expires. The civil date maps the displayed Gregorian
date, independent of the viewing timezone; sunrise/tithi remain astronomical
and location dependent.

## Refresh and verification

Run `python tools/import_traditional_calendar.py` explicitly to refresh.
The importer validates the numeric grid sequence and Gregorian day numbers,
then derives month lengths from successive first days (some source grids omit
their final cell). It rejects impossible lengths. Review all changed boundaries
against a local printed almanac before extending the supported years.

Regression tests check the reported date, Bhadro/Ashwin and Ashwin/Kartik
boundaries, Bengali New Year, the full bundled day sequence, timezone
independence, and unsupported dates. Existing tithi golden vectors remain.
Phone rendering and installation are separate device checks.
