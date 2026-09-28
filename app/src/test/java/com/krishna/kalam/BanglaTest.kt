package com.krishna.kalam

import com.krishna.kalam.core.Bangla
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** K67/K68 — golden vectors fetched from drikpanchang (Kolkata, geoname 1275004) at build time. */
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

    @Test fun tenSepIsTwentyFourBhadro1433() {
        val b = Bangla.bengaliDate(LocalDate.of(2026, 9, 10), kol)!!
        assertEquals(24, b.day); assertEquals(4, b.monthIdx); assertEquals(1433, b.year)
        assertEquals("ভাদ্র", b.monthBn)
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
