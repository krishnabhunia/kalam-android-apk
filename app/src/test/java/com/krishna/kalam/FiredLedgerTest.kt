package com.krishna.kalam
import com.krishna.kalam.alarm.FiredLedger
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** K64 — pure key semantics (SharedPreferences path is device-checked). */
class FiredLedgerTest {
    @Test fun keyIsSlotAndDay() = assertEquals("3@2026-08-11", FiredLedger.key(3, LocalDate.of(2026, 8, 11)))
    @Test fun sameSlotDifferentDayDiffers() =
        assertNotEquals(FiredLedger.key(3, LocalDate.of(2026, 8, 11)), FiredLedger.key(3, LocalDate.of(2026, 8, 12)))
    @Test fun sixSlotsSixKeys() {
        val d = LocalDate.of(2026, 8, 11)
        assertEquals(6, (0..5).map { FiredLedger.key(it, d) }.toSet().size)
    }
}
