package com.krishna.kalam

import com.krishna.kalam.core.Bangla
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Civil dates: traditional West Bengal almanac. Tithi: existing Drik vectors. */
class BanglaTest {
    private val kol = ZoneId.of("Asia/Kolkata")

    @Test fun poilaBoishakh1433() =
        assertEquals(LocalDate.of(2026, 4, 15), Bangla.boishakh1(2026, kol))

    @Test fun fifteenAprIsOneBoishakh() {
        val b = Bangla.bengaliDate(LocalDate.of(2026, 4, 15), kol)!!
        assertEquals(1, b.day); assertEquals(0, b.monthIdx); assertEquals(1433, b.year)
        assertEquals("বৈশাখ", b.monthBn); assertEquals("Boishakh", b.monthEn)
    }

    @Test fun fourteenAprIsThirtyChaitra1432() {
        val b = Bangla.bengaliDate(LocalDate.of(2026, 4, 14), kol)!!
        assertEquals(30, b.day); assertEquals(11, b.monthIdx); assertEquals(1432, b.year)
    }

    @Test fun tenSepIsTwentyThreeBhadro1433() {
        val b = Bangla.bengaliDate(LocalDate.of(2026, 9, 10), kol)!!
        assertEquals(23, b.day); assertEquals(4, b.monthIdx); assertEquals(1433, b.year)
        assertEquals("ভাদ্র", b.monthBn)
    }

    @Test fun midnaporeReportedDateAndAshwinBoundary() {
        val vectors = listOf(
            Triple(LocalDate.of(2026, 9, 18), 31, 4),
            Triple(LocalDate.of(2026, 9, 19), 1, 5),
            Triple(LocalDate.of(2026, 9, 28), 10, 5),
            Triple(LocalDate.of(2026, 10, 9), 21, 5)
        )
        for ((date, day, month) in vectors) {
            assertEquals(date.toString(), Bangla.BDate(day, month, 1433), Bangla.bengaliDate(date, kol))
        }
    }

    @Test fun civilCalendarDoesNotChangeWithViewingTimezone() {
        val date = LocalDate.of(2026, 10, 9)
        for (zone in listOf("Asia/Kolkata", "Asia/Dhaka", "UTC", "America/New_York")) {
            assertEquals(Bangla.BDate(21, 5, 1433), Bangla.bengaliDate(date, ZoneId.of(zone)))
        }
    }

    @Test fun unsupportedDatesDoNotSilentlyUseAnotherCalendar() {
        assertNull(Bangla.bengaliDate(LocalDate.of(1900, 1, 1), kol))
        assertNull(Bangla.bengaliDate(LocalDate.of(2100, 1, 1), kol))
        assertNull(Bangla.boishakh1(2100, kol))
    }

    @Test fun allBundledAlmanacDaysAndYearRollovers() {
        val fixture = javaClass.getResourceAsStream("/traditional-months.json")!!
            .bufferedReader().use { it.readText() }
        val rows = Regex("\"year\": (\\d+),\\s*\"month\": (\\d+),\\s*\"start\": \"([0-9-]+)\",\\s*\"days\": (\\d+)")
            .findAll(fixture).toList()
        assertEquals(36, rows.size)
        var expectedNext: LocalDate? = null
        for (row in rows) {
            val (year, month, startText, daysText) = row.destructured
            val start = LocalDate.parse(startText)
            val days = daysText.toInt()
            expectedNext?.let { assertEquals(it, start) }
            for (offset in 0 until days) {
                val date = start.plusDays(offset.toLong())
                assertEquals(date.toString(), Bangla.BDate(offset + 1, month.toInt(), year.toInt()),
                    Bangla.bengaliDate(date, kol))
            }
            expectedNext = start.plusDays(days.toLong())
        }
        assertNull(Bangla.bengaliDate(LocalDate.of(2025, 4, 14), kol))
        assertNull(Bangla.bengaliDate(expectedNext!!, kol))
    }

    @Test fun ashwinKartikRolloverUsesItsOwnBoundary() {
        assertEquals(Bangla.BDate(30, 5, 1433), Bangla.bengaliDate(LocalDate.of(2026, 10, 18), kol))
        assertEquals(Bangla.BDate(1, 6, 1433), Bangla.bengaliDate(LocalDate.of(2026, 10, 19), kol))
    }

    @Test fun dayCountContinuity() {
        var prev = Bangla.bengaliDate(LocalDate.of(2026, 8, 1), kol)!!
        for (i in 2..40) {
            val d = LocalDate.of(2026, 8, 1).plusDays((i - 1).toLong())
            val b = Bangla.bengaliDate(d, kol)!!
            if (b.monthIdx == prev.monthIdx) assertEquals(prev.day + 1, b.day) else assertEquals(1, b.day)
            prev = b
        }
    }

    @Test fun tithiTenSepChaturdashiUpto1033() {
        val t = Bangla.tithiForDay(LocalDate.of(2026, 9, 10), null, kol)!!
        assertEquals(28, t.idx)                      // Krishna Chaturdashi
        assertEquals("Chaturdashi", t.nameEn); assertEquals("কৃষ্ণপক্ষ", t.pakshaBn)
        assertNotNull(t.endLocal)
        val e = t.endLocal!!
        assertEquals(10, e.hour)                     // drikpanchang: 10:33 — allow engine ±15m
        assertTrue("end min ${e.minute}", e.minute in 18..48)
    }

    @Test fun bengaliNumerals() = assertEquals("১৪৩৩", Bangla.bnNum(1433))

    @Test fun tithiNamesTable() {
        assertEquals("Purnima" , Bangla.names(14).second)
        assertEquals("Amavasya", Bangla.names(29).second)
        assertEquals("Pratipada", Bangla.names(0).second)
        assertEquals("Pratipada", Bangla.names(15).second)
    }
}
