"""Import numeric West Bengal civil month boundaries; never import festival/tithi text.

Run explicitly when refreshing the bundled almanac. No network is used by the app.
"""
import concurrent.futures
import datetime as dt
import html
import json
import pathlib
import re
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
MONTHS = ['boishakh', 'joishtho', 'asharh', 'srabon', 'bhadro', 'ashshin',
          'kartik', 'ogrohaeon', 'poush', 'magh', 'falgun', 'choitro']

def fetch(key):
    year, idx = key
    url = f'https://www.bengalicalendar.com/{year}/{MONTHS[idx]}'
    with urllib.request.urlopen(url, timeout=30) as response:
        page = response.read().decode('utf-8')
    cells = re.findall(r'<div id="nday"[^>]*>.*?<div id="fest"', page, re.S)
    numbers = []
    for cell in cells:
        n = re.search(r'<div id="nday"[^>]*>(.*?)</div>', cell, re.S)
        e = re.search(r'<div id="eday"[^>]*>(.*?)</div>', cell, re.S)
        if n and e:
            clean = lambda s: html.unescape(re.sub('<[^>]+>', '', s)).strip()
            bn, en = clean(n[1]), clean(e[1])
            if bn.isdigit() and en.isdigit():
                numbers.append((int(bn), int(en)))
    first = next(i for i, pair in enumerate(numbers) if pair[0] == 1)
    numbers = numbers[first:]
    # Stop when an adjacent month is shown in the grid.
    end = next((i for i in range(1, len(numbers)) if numbers[i][0] == 1), len(numbers))
    numbers = numbers[:end]
    assert 29 <= len(numbers) <= 32, (url, len(numbers))
    assert [n for n, _ in numbers] == list(range(1, len(numbers)+1)), url
    greg_year = year + 593 + (idx >= 9)
    greg_month = (idx + 3) % 12 + 1
    start = dt.date(greg_year, greg_month, numbers[0][1])
    assert all((start+dt.timedelta(days=i)).day == day for i, (_, day) in enumerate(numbers)), url
    return dict(year=year, month=idx, start=start.isoformat(), days=len(numbers), source=url)

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        rows = sorted(pool.map(fetch, [(y, i) for y in range(1432,1435) for i in range(12)] + [(1435,0)]),
                      key=lambda r: r['start'])
    for left, right in zip(rows, rows[1:]):
        # The source sometimes omits the final grid cell; successive first days
        # define the civil month length, not the number of rendered cells.
        left['days'] = (dt.date.fromisoformat(right['start'])-dt.date.fromisoformat(left['start'])).days
        assert 29 <= left['days'] <= 32, (left, right)
    rows = rows[:-1]
    target = ROOT/'app/src/test/resources/traditional-months.json'
    target.write_text(json.dumps(rows, indent=2)+'\n', encoding='utf-8')
    lines = '\n'.join(f'        Month(LocalDate.parse("{r["start"]}"), {r["year"]}, {r["month"]}, {r["days"]}),' for r in rows)
    (ROOT/'app/src/main/java/com/krishna/kalam/core/TraditionalCalendar.kt').write_text('''package com.krishna.kalam.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offline West Bengal traditional civil dates, imported by tools/import_traditional_calendar.py.
 * Source/provenance and coverage: docs/calendar/traditional-calendar.md.
 * No Drik or Bangladesh fallback: unsupported dates are explicitly unavailable.
 */
internal object TraditionalCalendar {
    private data class Month(val start: LocalDate, val year: Int, val index: Int, val days: Int)
    private val months = listOf(
'''+lines+'''
    )

    fun date(d: LocalDate): Bangla.BDate? {
        val month = months.lastOrNull { !d.isBefore(it.start) } ?: return null
        val offset = ChronoUnit.DAYS.between(month.start, d).toInt()
        if (offset !in 0 until month.days) return null
        return Bangla.BDate(offset + 1, month.index, month.year)
    }

    fun newYear(gregYear: Int): LocalDate? =
        months.firstOrNull { it.index == 0 && it.start.year == gregYear }?.start
}
''', encoding='utf-8')
    print(f'Imported {len(rows)} validated months: {rows[0]["start"]} through '
          f'{dt.date.fromisoformat(rows[-1]["start"])+dt.timedelta(days=rows[-1]["days"]-1)}')
