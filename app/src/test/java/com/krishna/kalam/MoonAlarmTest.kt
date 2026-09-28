package com.krishna.kalam

import com.krishna.kalam.core.SunAlarm
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.data.Settings
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** K77 — moon alarms: days without an event are skipped; ~50 min/day drift; codec 49–54. */
class MoonAlarmTest {
    private val kol = ZoneId.of("Asia/Kolkata")
    private val base = LocalDate.of(2026, 9, 17)
    private fun at(d: LocalDate, hm: String) = d.atTime(LocalTime.parse(hm)).atZone(kol)
    // Stand-in moonrise: 10:52 on base, +50 min/day; the third day has NO moonrise (real lunar behaviour)
    private val moonrise: (LocalDate) -> ZonedDateTime? = { d ->
        val i = base.until(d, java.time.temporal.ChronoUnit.DAYS).toInt()
        if (i == 2) null else at(d, "10:52").plusMinutes(50L * i)
    }

    @Test fun skipsDayWithoutMoonrise() {
        val now = at(base.plusDays(2), "01:00")                          // the no-moonrise day
        val n = SunAlarm.nextRing(now, true, 5, SunAlarm.EVERY, moonrise)!!
        assertEquals(base.plusDays(3), n.toLocalDate())                 // jumps to the next day
        assertEquals("13:17", n.toLocalTime().toString())               // 10:52 + 150 − 5
    }

    @Test fun driftFiftyMinutesPerDay() {
        val a = SunAlarm.nextRing(at(base, "00:00"), true, 0, SunAlarm.EVERY, moonrise)!!
        val b = SunAlarm.nextRing(at(base.plusDays(1), "00:00"), true, 0, SunAlarm.EVERY, moonrise)!!
        assertEquals(50L, java.time.Duration.between(a.toLocalTime().atDate(base), b.toLocalTime().atDate(base)).toMinutes())
    }

    @Test fun moonTitles() {
        assertEquals("Moonrise in 5 min · 10:52", SunAlarm.title("Moonrise", 5, at(base, "10:52")))
        assertEquals("Moonset now · 22:40", SunAlarm.title("Moonset", 0, at(base, "22:40")))
    }

    @Test fun settingsCodecFields49to54() {
        val s = Settings(moonriseAlarm = true, moonriseLead = 5, moonriseDays = "0000011", moonsetAlarm = true, moonsetLead = 15, moonsetDays = "1111100")
        assertEquals(s, Prefs.decodeSettings(Prefs.encodeSettings(s)))
        val old = Prefs.decodeSettings(Prefs.encodeSettings(Settings()).split("\u001f").take(49).joinToString("\u001f"))
        assertFalse(old.moonriseAlarm); assertEquals("1111111", old.moonsetDays)   // pre-1.19 blob: defaults
    }
}
