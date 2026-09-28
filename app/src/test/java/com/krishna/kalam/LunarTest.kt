package com.krishna.kalam
import com.krishna.kalam.core.Lunar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class LunarTest {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    @Test fun goldenVectors() {
        val zone = ZoneId.of("Asia/Kolkata")
        val rows = javaClass.classLoader!!.getResourceAsStream("lunar_vectors.csv")!!
            .bufferedReader().readLines()
        assertTrue(rows.size >= 300)
        fun secs(s: String): Int { val q = s.split(":"); return q[0].toInt()*3600 + q[1].toInt()*60 + q[2].toInt() }
        for (r in rows) {
            val p = r.split(",")
            val d = LocalDate.parse(p[3])
            val ev = Lunar.moonEvents(d, p[1].toDouble(), p[2].toDouble(), zone)
            if (p[4] == "-") assertNull("expected no rise: $r", ev.rise)
            else { assertNotNull("missing rise: $r", ev.rise)
                assertTrue("rise off: $r got ${ev.rise!!.format(fmt)}",
                    abs(secs(ev.rise!!.format(fmt)) - secs(p[4])) <= 2) }
            if (p[5] == "-") assertNull("expected no set: $r", ev.set)
            else { assertNotNull("missing set: $r", ev.set)
                assertTrue("set off: $r got ${ev.set!!.format(fmt)}",
                    abs(secs(ev.set!!.format(fmt)) - secs(p[5])) <= 2) }
            val ph = Lunar.moonPhase(ZonedDateTime.of(d.atTime(12, 0), zone))
            assertTrue("illum off: $r got ${ph.illumPct}", abs(ph.illumPct - p[6].toDouble()) <= 0.1)
        }
    }
}
