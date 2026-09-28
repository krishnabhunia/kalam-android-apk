package com.krishna.kalam
import com.krishna.kalam.core.Engine
import com.krishna.kalam.ui.fmtDuration
import com.krishna.kalam.ui.fmtHM
import org.junit.Assert.*
import org.junit.Test

class DurationTest {
    // K38 formatter vectors (BACKLOG)
    @Test fun variantA() { assertEquals("97 mins", fmtDuration(97, false)) }
    @Test fun variantB() { assertEquals("1 hour 37 mins", fmtDuration(97, true)) }
    @Test fun bUnderHour() { assertEquals("58 mins", fmtDuration(58, true)) }
    @Test fun bExactHour() { assertEquals("1 hour", fmtDuration(60, true)) }
    @Test fun bTwoHours() { assertEquals("2 hours", fmtDuration(120, true)) }
    @Test fun bSingularMin() { assertEquals("2 hours 1 min", fmtDuration(121, true)) }
    @Test fun hm() { assertEquals("13h 02m", fmtHM(782)); assertEquals("0h 00m", fmtHM(0)) }

    // K39 moon presence vectors — mirror the 1.2.D2 design numbers
    @Test fun normalDay() { assertEquals(795L, Engine.moonPresence(185, 980, null)) }        // 03:05→16:20 = 13h15
    @Test fun crossMidnight() { assertEquals(692L, Engine.moonPresence(1300, 552, null)) }   // 21:40 rise, 09:12 set = 11h32
    @Test fun riseOnly() { assertEquals(597L, Engine.moonPresence(843, null, null)) }        // 14:03→24:00 = 9h57
    @Test fun setOnly() { assertEquals(430L, Engine.moonPresence(null, 430, null)) }
    @Test fun noEventUpAllDay() { assertEquals(1440L, Engine.moonPresence(null, null, true)) }
    @Test fun noEventDownAllDay() { assertEquals(0L, Engine.moonPresence(null, null, false)) }
    @Test fun unknownIsNull() { assertNull(Engine.moonPresence(null, null, null)) }
}
