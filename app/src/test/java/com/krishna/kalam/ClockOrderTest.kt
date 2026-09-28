package com.krishna.kalam

import com.krishna.kalam.ui.ClockOrder
import org.junit.Assert.*
import org.junit.Test

/** K65/K66 — ordering + grouping vectors. */
class ClockOrderTest {
    data class C(val name: String, val region: String, val tz: String)
    private val cs = listOf(
        C("Zurich", "Zurich · Switzerland", "Europe/Zurich"),
        C("Mumbai", "Maharashtra · India", "Asia/Kolkata"),
        C("Bajkul", "Saved", "Asia/Kolkata"),
        C("London", "England · United Kingdom", "Europe/London"),
        C("Kolkata", "West Bengal · India", "Asia/Kolkata"),
        C("Broken", "X · Nowhere", "Not/AZone"))

    @Test fun countryExtraction() {
        assertEquals("India", ClockOrder.countryOf("West Bengal · India"))
        assertEquals("Switzerland", ClockOrder.countryOf("Zurich · Switzerland"))
        assertNull(ClockOrder.countryOf("Saved")); assertNull(ClockOrder.countryOf(""))
    }

    @Test fun continentBuckets() {
        assertEquals("Asia", ClockOrder.continentOf("Asia/Kolkata"))
        assertEquals("Americas", ClockOrder.continentOf("America/New_York"))
        assertEquals("Oceania", ClockOrder.continentOf("Pacific/Auckland"))
        assertEquals("Oceania", ClockOrder.continentOf("Australia/Sydney"))
        assertNull(ClockOrder.continentOf("Not/AZone"))
    }

    @Test fun nameSort() =
        assertEquals(listOf("Bajkul","Broken","Kolkata","London","Mumbai","Zurich"),
            ClockOrder.sort(cs, "NAME", { it.name }, { it.region }, { it.tz }).map { it.name })

    @Test fun timeSortBadTzSinks() {
        val r = ClockOrder.sort(cs, "TIME", { it.name }, { it.region }, { it.tz }).map { it.name }
        assertEquals("Broken", r.last())                       // bad tz sinks
        assertTrue(r.indexOf("London") < r.indexOf("Zurich"))  // UTC+1 < UTC+2 (BST vs CEST)
        assertTrue(r.indexOf("Zurich") < r.indexOf("Mumbai"))
    }

    @Test fun countrySortIndiaFirstStable() {
        val r = ClockOrder.sort(cs, "COUNTRY", { it.name }, { it.region }, { it.tz }).map { it.name }
        assertEquals(listOf("Kolkata","Mumbai"), r.take(2))    // India first, names asc
        assertEquals("Bajkul", r.last())                       // country-less sinks
    }

    @Test fun addedIsIdentity() =
        assertEquals(cs.map { it.name }, ClockOrder.sort(cs, "ADDED", { it.name }, { it.region }, { it.tz }).map { it.name })

    @Test fun groupByCountry() {
        val g = ClockOrder.group(cs, "COUNTRY", { it.region }, { it.tz })!!
        assertEquals(listOf("India","Nowhere","Switzerland","United Kingdom","Other"), g.map { it.first })
        assertEquals(2, g[0].second.size)                      // India count
        assertEquals(listOf("Bajkul"), g.last().second.map { it.name })
    }

    @Test fun groupByContinentOtherLast() {
        val g = ClockOrder.group(cs, "CONTINENT", { it.region }, { it.tz })!!
        assertEquals(listOf("Asia","Europe","Other"), g.map { it.first })
        assertEquals(3, g[0].second.size)
    }

    @Test fun groupNoneIsNull() = assertNull(ClockOrder.group(cs, "NONE", { it.region }, { it.tz }))
}

/** K74/K75 — hierarchy, metric sorts, direction, data gating. */
class ClockOrderV117Test {
    data class C(val name: String, val region: String, val tz: String, val temp: Double?, val aqi: Double?)
    private val cs = listOf(
        C("Zurich", "Zurich · Switzerland", "Europe/Zurich", 18.0, 40.0),
        C("Mumbai", "Maharashtra · India", "Asia/Kolkata", 29.0, 64.0),
        C("Bajkul", "Saved", "Asia/Kolkata", null, null),
        C("Kolkata", "West Bengal · India", "Asia/Kolkata", 31.0, 132.0),
        C("Howrah", "West Bengal · India", "Asia/Kolkata", 30.5, null))
    private fun srt(mode: String, rev: Boolean, m: (C) -> Double?) =
        ClockOrder.sort(cs, mode, rev, { it.name }, { it.region }, { it.tz }, m).map { it.name }

    @Test fun stateExtraction() {
        assertEquals("West Bengal", ClockOrder.stateOf("West Bengal · India"))
        assertNull(ClockOrder.stateOf("India")); assertNull(ClockOrder.stateOf("Saved"))
    }

    @Test fun tempDescNullLast() =
        assertEquals(listOf("Kolkata","Howrah","Mumbai","Zurich","Bajkul"), srt("TEMP", false) { it.temp })

    @Test fun tempAscFlipKeepsNullLast() =
        assertEquals(listOf("Zurich","Mumbai","Howrah","Kolkata","Bajkul"), srt("TEMP", true) { it.temp })

    @Test fun aqiDescTwoNullsKeepAddedOrder() =
        assertEquals(listOf("Kolkata","Mumbai","Zurich","Bajkul","Howrah"), srt("AQI", false) { it.aqi })

    @Test fun nameFlip() =
        assertEquals(listOf("Zurich","Mumbai","Kolkata","Howrah","Bajkul"), srt("NAME", true) { null })

    @Test fun addedIgnoresFlip() = assertEquals(cs.map { it.name }, srt("ADDED", true) { it.temp })

    @Test fun hasDataGate() {
        assertFalse(ClockOrder.hasData(cs, "HUM") { null })
        assertTrue(ClockOrder.hasData(cs, "TEMP") { it.temp })
        assertTrue(ClockOrder.hasData(cs, "NAME") { null })   // non-metric never gated
    }

    @Test fun countryStateTree() {
        val t = ClockOrder.tree(cs, "COUNTRY_STATE", { it.region }, { it.tz })!!
        assertEquals(listOf("India","Switzerland","Other"), t.map { it.name })
        assertEquals(listOf("Maharashtra","West Bengal"), t[0].children.map { it.name })
        assertEquals("India▸West Bengal", t[0].children[1].path)
        assertEquals(3, t[0].count); assertEquals(2, t[0].children[1].items.size)
    }

    @Test fun continentCountryStateThreeLevels() {
        val t = ClockOrder.tree(cs, "CONTINENT_COUNTRY_STATE", { it.region }, { it.tz })!!
        assertEquals(listOf("Asia","Europe"), t.map { it.name })
        val asia = t[0]
        assertEquals(listOf("India","Other"), asia.children.map { it.name })   // Bajkul → Other inside Asia
        assertEquals("Asia▸India▸Maharashtra", asia.children[0].children[0].path)
        assertEquals(4, asia.count)
    }

    @Test fun legacyFlatGroupStillWorks() {
        val g = ClockOrder.group(cs, "COUNTRY", { it.region }, { it.tz })!!
        assertEquals(listOf("India","Switzerland","Other"), g.map { it.first })
    }
}
