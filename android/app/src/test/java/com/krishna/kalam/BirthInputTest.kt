package com.krishna.kalam

import com.krishna.kalam.core.BirthInput
import com.krishna.kalam.api.Prokerala
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class BirthInputTest {
    @Test fun blankOrDamagedDateOpensPickerAtDefault() {
        // Fresh install (K84): boyDob == "" used to crash Settings → My details → DOB.
        assertEquals(BirthInput.DEFAULT_DATE, BirthInput.dateOr(""))
        assertEquals(BirthInput.DEFAULT_DATE, BirthInput.dateOr("31-02-1990"))
        assertEquals(LocalDate.of(1992, 7, 14), BirthInput.dateOr("1992-07-14"))
    }

    @Test fun blankOrDamagedTimeOpensPickerAtNoon() {
        assertEquals(12 to 0, BirthInput.timeOr(""))
        assertEquals(12 to 0, BirthInput.timeOr("25:00"))
        assertEquals(12 to 0, BirthInput.timeOr("9"))
        assertEquals(9 to 5, BirthInput.timeOr("09:05"))
        assertNull(BirthInput.parseTime("12:60"))
    }

    @Test fun timeIsAlwaysAsciiEvenOnABengaliPhone() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("bn-IN-u-nu-beng"))
            assertEquals("09:05", BirthInput.hhmm(9, 5))
        } finally { Locale.setDefault(saved) }
    }

    @Test fun legacyNativeDigitTimesAreRepairedBeforeSending() {
        // "০৯:৩০" may already be stored by older builds that formatted with the device locale.
        assertEquals("09:30", BirthInput.normalizeTime("০৯:৩০"))
        assertEquals("09:30", BirthInput.normalizeTime(" 9:30 "))
        assertNull(BirthInput.normalizeTime(""))
    }

    @Test fun cacheKeyIsUnchangedForAsciiAndStableForLegacyDigits() {
        val ascii = Prokerala.cacheKey("1990-01-01", "09:30", 22.5, 88.3, "1995-01-01", "12:00", 19.0, 72.8)
        val legacy = Prokerala.cacheKey("1990-01-01", "০৯:৩০", 22.5, 88.3, "1995-01-01", "12:00", 19.0, 72.8)
        assertEquals(ascii, legacy)
        // Pinned SHA-1 prefix of the v1.21 key format: existing Match history keys must not change.
        assertEquals("2bb40ff241f9745b3d4f", ascii)
    }
}
