package com.krishna.kalam
import com.krishna.kalam.core.Solar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class SolarTest {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    @Test fun goldenVectors() {
        val zone = ZoneId.of("Asia/Kolkata")
        val rows = javaClass.classLoader!!.getResourceAsStream("solar_vectors.csv")!!
            .bufferedReader().readLines()
        assertTrue(rows.size > 1000)
        var checked = 0
        for (r in rows) {
            val p = r.split(",")
            val zen = if (p[4] == "G") Solar.ZENITH_GEOMETRIC else Solar.ZENITH_OBSERVATIONAL
            val res = Solar.sunTimes(LocalDate.parse(p[3]), p[1].toDouble(), p[2].toDouble(), zone, zen)
            assertTrue("no event for $r", res is Solar.SunResult.Times)
            val t = res as Solar.SunResult.Times
            fun secs(s: String): Int { val q = s.split(":"); return q[0].toInt()*3600 + q[1].toInt()*60 + q[2].toInt() }
            assertTrue("sunrise off: $r got ${t.sunrise.format(fmt)}",
                abs(secs(t.sunrise.format(fmt)) - secs(p[5])) <= 1)
            assertTrue("sunset off: $r got ${t.sunset.format(fmt)}",
                abs(secs(t.sunset.format(fmt)) - secs(p[6])) <= 1)
            checked++
        }
        assertEquals(rows.size, checked)
    }
    /** Clock minutes are on the noon offset; anchoring them at midnight's offset shifted DST-change days by 1 h. */
    @Test fun daylightSavingChangeDaysAreNotShiftedByAnHour() {
        fun minutes(t: java.time.ZonedDateTime) = t.hour * 60 + t.minute
        for ((zone, lat, lon, change) in listOf(
            Quad("America/New_York", 40.7128, -74.0060, LocalDate.of(2026, 3, 8)),
            Quad("America/New_York", 40.7128, -74.0060, LocalDate.of(2026, 11, 1)),
            Quad("Europe/London", 51.5072, -0.1276, LocalDate.of(2026, 3, 29)),
            Quad("Europe/London", 51.5072, -0.1276, LocalDate.of(2026, 10, 25)))) {
            val z = ZoneId.of(zone)
            val on = Solar.sunTimes(change, lat, lon, z) as Solar.SunResult.Times
            val next = Solar.sunTimes(change.plusDays(1), lat, lon, z) as Solar.SunResult.Times
            // Both days share the post-change offset, so wall-clock times differ by ~1–3 min, never ~60.
            assertTrue("$zone $change sunrise ${on.sunrise} vs ${next.sunrise}",
                abs(minutes(on.sunrise) - minutes(next.sunrise)) <= 4)
            assertTrue("$zone $change sunset ${on.sunset} vs ${next.sunset}",
                abs(minutes(on.sunset) - minutes(next.sunset)) <= 4)
            assertEquals(change, on.sunrise.toLocalDate())
        }
    }
    private data class Quad(val zone: String, val lat: Double, val lon: Double, val date: LocalDate)

    @Test fun polarGuard() {
        val res = Solar.sunTimes(LocalDate.of(2026, 12, 21), 78.0, 15.0, ZoneId.of("UTC"))
        assertTrue(res is Solar.SunResult.NoEvent)
    }
}
