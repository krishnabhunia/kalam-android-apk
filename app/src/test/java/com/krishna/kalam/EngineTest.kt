package com.krishna.kalam
import com.krishna.kalam.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    // Mon..Sun expected segments
    private val expected = mapOf(
        Kalam.RK to listOf(2, 7, 5, 6, 4, 3, 8),
        Kalam.YG to listOf(4, 3, 2, 1, 7, 6, 5),
        Kalam.GK to listOf(6, 5, 4, 3, 2, 1, 7))
    @Test fun weekdayMapping() {
        val monday = LocalDate.of(2026, 8, 10)
        for (i in 0..6) for (k in Kalam.entries)
            assertEquals("$k day$i", expected[k]!![i], Engine.segmentFor(k, monday.plusDays(i.toLong())))
    }
    @Test fun sundayAndWednesdayCollision() {
        for (d in listOf(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 12))) {
            val r = Engine.computeDay(d, 22.5726, 88.3639, zone, Convention.GEOMETRIC) as DayResult.Ok
            val gk = r.spans.first { it.kalam == Kalam.GK }
            val rk = r.spans.first { it.kalam == Kalam.RK }
            assertEquals("GK end == RK start on $d", gk.end.toInstant(), rk.start.toInstant())
        }
    }
    @Test fun boundarySegments() {
        // Saturday GK = segment 1 starts at sunrise; Sunday RK = segment 8 ends at sunset
        val sat = Engine.computeDay(LocalDate.of(2026, 8, 15), 22.5726, 88.3639, zone, Convention.GEOMETRIC) as DayResult.Ok
        assertEquals(sat.sky.sunrise.toInstant(), sat.spans.first { it.kalam == Kalam.GK }.start.toInstant())
        val sun = Engine.computeDay(LocalDate.of(2026, 8, 16), 22.5726, 88.3639, zone, Convention.GEOMETRIC) as DayResult.Ok
        val rkEnd = sun.spans.first { it.kalam == Kalam.RK }.end.toInstant()
        assertTrue(Math.abs(rkEnd.epochSecond - sun.sky.sunset.toInstant().epochSecond) <= 1)
    }
    @Test fun slotArithmetic() {
        val r = Engine.computeDay(LocalDate.of(2026, 8, 15), 22.5726, 88.3639, zone, Convention.GEOMETRIC) as DayResult.Ok
        val gk = r.spans.first { it.kalam == Kalam.GK }   // starts at sunrise
        val fire = gk.start.minusMinutes(120)             // 120-min offset fires pre-sunrise (D13)
        assertTrue(fire.isBefore(r.sky.sunrise))
    }
}
