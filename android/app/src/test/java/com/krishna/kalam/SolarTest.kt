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
    @Test fun polarGuard() {
        val res = Solar.sunTimes(LocalDate.of(2026, 12, 21), 78.0, 15.0, ZoneId.of("UTC"))
        assertTrue(res is Solar.SunResult.NoEvent)
    }
}
