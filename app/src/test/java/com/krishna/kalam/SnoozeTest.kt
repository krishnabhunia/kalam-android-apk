package com.krishna.kalam

import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.alarm.FiredLedger
import com.krishna.kalam.alarm.SoundService
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** K79 — snooze codes, sequence-keyed ledger, ring cap. */
class SnoozeTest {
    private val day = LocalDate.of(2026, 9, 17)

    @Test fun snoozeCodesAreDistinctFromSkyCodes() {
        assertEquals(setOf(10, 11, 12, 13), AlarmScheduler.SNOOZE_CODES)
        assertTrue((AlarmScheduler.SKY_CODES intersect AlarmScheduler.SNOOZE_CODES).isEmpty())
        assertEquals(10, AlarmScheduler.snoozeCodeOf(6))
        assertEquals(6, AlarmScheduler.baseCodeOf(10))
        assertEquals(6, AlarmScheduler.baseCodeOf(6))        // already a base code
    }

    @Test fun ledgerKeysSeparateEachSnooze() {
        assertEquals("6@2026-09-17", FiredLedger.key(6, day))
        assertEquals("10@2026-09-17#1", FiredLedger.key(10, day, 1))
        assertNotEquals(FiredLedger.key(10, day, 1), FiredLedger.key(10, day, 2))
        assertEquals(FiredLedger.key(6, day), FiredLedger.key(6, day, 0))   // seq 0 = original behaviour
    }

    @Test fun snoozeLimitsAndOptions() {
        assertEquals(3, AlarmScheduler.MAX_SNOOZE)
        assertEquals(listOf(5, 10, 15), AlarmScheduler.SNOOZE_MINUTES)
    }

    @Test fun ringCapIsSixtySeconds() = assertEquals(60, SoundService.RING_CAP_SECS)
}
