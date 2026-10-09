package com.krishna.kalam.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offline West Bengal traditional civil dates, imported by tools/import_traditional_calendar.py.
 * Source/provenance and coverage: docs/calendar/traditional-calendar.md.
 * No Drik or Bangladesh fallback: unsupported dates are explicitly unavailable.
 */
internal object TraditionalCalendar {
    private data class Month(val start: LocalDate, val year: Int, val index: Int, val days: Int)
    private val months = listOf(
        Month(LocalDate.parse("2025-04-15"), 1432, 0, 31),
        Month(LocalDate.parse("2025-05-16"), 1432, 1, 31),
        Month(LocalDate.parse("2025-06-16"), 1432, 2, 32),
        Month(LocalDate.parse("2025-07-18"), 1432, 3, 31),
        Month(LocalDate.parse("2025-08-18"), 1432, 4, 31),
        Month(LocalDate.parse("2025-09-18"), 1432, 5, 31),
        Month(LocalDate.parse("2025-10-19"), 1432, 6, 30),
        Month(LocalDate.parse("2025-11-18"), 1432, 7, 29),
        Month(LocalDate.parse("2025-12-17"), 1432, 8, 29),
        Month(LocalDate.parse("2026-01-15"), 1432, 9, 30),
        Month(LocalDate.parse("2026-02-14"), 1432, 10, 30),
        Month(LocalDate.parse("2026-03-16"), 1432, 11, 30),
        Month(LocalDate.parse("2026-04-15"), 1433, 0, 31),
        Month(LocalDate.parse("2026-05-16"), 1433, 1, 31),
        Month(LocalDate.parse("2026-06-16"), 1433, 2, 32),
        Month(LocalDate.parse("2026-07-18"), 1433, 3, 32),
        Month(LocalDate.parse("2026-08-19"), 1433, 4, 31),
        Month(LocalDate.parse("2026-09-19"), 1433, 5, 30),
        Month(LocalDate.parse("2026-10-19"), 1433, 6, 30),
        Month(LocalDate.parse("2026-11-18"), 1433, 7, 29),
        Month(LocalDate.parse("2026-12-17"), 1433, 8, 30),
        Month(LocalDate.parse("2027-01-16"), 1433, 9, 29),
        Month(LocalDate.parse("2027-02-14"), 1433, 10, 30),
        Month(LocalDate.parse("2027-03-16"), 1433, 11, 30),
        Month(LocalDate.parse("2027-04-15"), 1434, 0, 31),
        Month(LocalDate.parse("2027-05-16"), 1434, 1, 32),
        Month(LocalDate.parse("2027-06-17"), 1434, 2, 31),
        Month(LocalDate.parse("2027-07-18"), 1434, 3, 32),
        Month(LocalDate.parse("2027-08-19"), 1434, 4, 31),
        Month(LocalDate.parse("2027-09-19"), 1434, 5, 30),
        Month(LocalDate.parse("2027-10-19"), 1434, 6, 30),
        Month(LocalDate.parse("2027-11-18"), 1434, 7, 30),
        Month(LocalDate.parse("2027-12-18"), 1434, 8, 29),
        Month(LocalDate.parse("2028-01-16"), 1434, 9, 29),
        Month(LocalDate.parse("2028-02-14"), 1434, 10, 30),
        Month(LocalDate.parse("2028-03-15"), 1434, 11, 31),
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
