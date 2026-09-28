package com.krishna.kalam

import com.krishna.kalam.core.SunAlarm
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.data.Settings
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** K76 — mask, lead, next-ring across a week, labels, codec 43–48. */
class SunAlarmTest {
    private val kol = ZoneId.of("Asia/Kolkata")
    private fun at(d: LocalDate, hm: String) = d.atTime(LocalTime.parse(hm)).atZone(kol)
    // Stand-in sunrise that drifts +1 min/day from 05:26 on Thu 17-Sep-2026
    private val base = LocalDate.of(2026, 9, 17)
    private val rise: (LocalDate) -> ZonedDateTime? = { d -> at(d, "05:26").plusMinutes(base.until(d, java.time.temporal.ChronoUnit.DAYS)) }

    @Test fun maskValidation() {
        assertTrue(SunAlarm.validMask("1010000")); assertFalse(SunAlarm.validMask("0000000"))
        assertFalse(SunAlarm.validMask("111")); assertFalse(SunAlarm.validMask("1111112"))
    }

    @Test fun dayOkAndToggle() {
        assertTrue(SunAlarm.dayOk("1010000", DayOfWeek.MONDAY)); assertFalse(SunAlarm.dayOk("1010000", DayOfWeek.TUESDAY))
        assertEquals("0010000", SunAlarm.toggle("1010000", DayOfWeek.MONDAY))
        assertEquals(SunAlarm.WEEKENDS, SunAlarm.toggle(SunAlarm.toggle("0000001", DayOfWeek.SATURDAY), DayOfWeek.SATURDAY).let { SunAlarm.toggle(it, DayOfWeek.SATURDAY) })
    }

    @Test fun ringAtClampsLead() {
        val ev = at(base, "05:26")
        assertEquals(at(base, "05:16"), SunAlarm.ringAt(ev, 10))
        assertEquals(at(base, "02:26"), SunAlarm.ringAt(ev, 999))   // clamped to 180
    }

    @Test fun nextRingHonoursMaskAndDrift() {
        val now = at(base, "12:00")                        // Thu noon, today's sunrise passed
        val n = SunAlarm.nextRing(now, true, 10, "1010000", rise)!!   // Mon + Wed only
        assertEquals(LocalDate.of(2026, 9, 21), n.toLocalDate())     // next Monday
        assertEquals("05:20", n.toLocalTime().toString())            // 05:26 + 4 days drift − 10
    }

    @Test fun nextRingTodayIfStillAhead() {
        val now = at(base, "04:00")
        assertEquals(at(base, "05:16"), SunAlarm.nextRing(now, true, 10, SunAlarm.EVERY, rise))
    }

    @Test fun nextRingOffOrInvalidIsNull() {
        assertNull(SunAlarm.nextRing(at(base, "04:00"), false, 0, SunAlarm.EVERY, rise))
        assertNull(SunAlarm.nextRing(at(base, "04:00"), true, 0, "0000000", rise))
        assertNull(SunAlarm.nextRing(at(base, "04:00"), true, 0, SunAlarm.EVERY) { null })   // polar: no events
    }

    @Test fun summariesAndTitles() {
        assertEquals("Every day", SunAlarm.daysSummary(SunAlarm.EVERY)); assertEquals("Weekends", SunAlarm.daysSummary(SunAlarm.WEEKENDS))
        assertEquals("Mon Wed", SunAlarm.daysSummary("1010000")); assertEquals("5 days", SunAlarm.daysSummary("1111010"))
        assertEquals("−10 min", SunAlarm.leadSummary(10, "sunrise")); assertEquals("at sunset", SunAlarm.leadSummary(0, "sunset"))
        assertEquals("Sunrise in 10 min · 05:26", SunAlarm.title("Sunrise", 10, at(base, "05:26")))
        assertEquals("Sunset now · 17:44", SunAlarm.title("Sunset", 0, at(base, "17:44")))
    }

    @Test fun settingsCodecFields43to48() {
        val s = Settings(sunriseAlarm = true, sunriseLead = 10, sunriseDays = "1010000", sunsetAlarm = true, sunsetLead = 0, sunsetDays = "0000011")
        val back = Prefs.decodeSettings(Prefs.encodeSettings(s))
        assertEquals(s, back)
        val old = Prefs.decodeSettings(Prefs.encodeSettings(Settings()).split("\u001f").take(43).joinToString("\u001f"))
        assertFalse(old.sunriseAlarm); assertEquals("1111111", old.sunsetDays)   // pre-1.18 blob: defaults
    }
}
