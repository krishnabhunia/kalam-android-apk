package com.krishna.kalam
import com.krishna.kalam.data.ClockPlace
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.ui.Geo
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** K51. om/ta payloads WIRE-CAPTURED in-container 11-Aug-2026 (Zurich coords), values untouched. */
class ClocksTest {
    private val om = """{"latitude":47.38,"longitude":8.539999,"generationtime_ms":0.0015497207641601562,"utc_offset_seconds":7200,"timezone":"Europe/Zurich","timezone_abbreviation":"GMT+2","elevation":416.0}"""
    private val ta = """{"timeZone":"Europe/Zurich","currentLocalTime":"2026-08-13T07:11:10.2842849","currentUtcOffset":{"seconds":7200,"milliseconds":7200000,"ticks":72000000000,"nanoseconds":7200000000000},"standardUtcOffset":{"seconds":3600,"milliseconds":3600000,"ticks":36000000000,"nanoseconds":3600000000000},"hasDayLightSaving":true,"isDayLightSavingActive":true,"dstInterval":{"dstName":"CEST","dstOffsetToUtc":{"se"""

    @Test fun openMeteoTz() = assertEquals("Europe/Zurich", Geo.parseTimezone(om))
    @Test fun timeapiTz()  = assertEquals("Europe/Zurich", Geo.parseTimezone(ta))
    @Test fun garbageTzNull() { assertNull(Geo.parseTimezone("nope")); assertNull(Geo.parseTimezone("{}")) }

    @Test fun codecRoundtrip() {
        val a = ClockPlace("Z\u00fcrich", "Zurich \u00b7 Switzerland", "Europe/Zurich", 47.3745, 8.5410)
        val b = ClockPlace("Kolkata", "West Bengal \u00b7 India", "Asia/Kolkata", 22.5726, 88.3639)
        val enc = listOf(a, b)
        // encode via write path logic mirror: decode(encode) == identity through Prefs.decodeClocks
        val raw = enc.joinToString("\u001e") { listOf(it.name, it.region, it.tz,
            it.lat.toString(), it.lon.toString()).joinToString("\u001f") }
        assertEquals(enc, Prefs.decodeClocks(raw))
    }

    @Test fun legacyAbsentAndBadRows() {
        assertTrue(Prefs.decodeClocks("").isEmpty())
        val raw = "good\u001fregion\u001fAsia/Kolkata\u001f22.5\u001f88.3\u001ebad\u001frow"
        val out = Prefs.decodeClocks(raw)
        assertEquals(1, out.size); assertEquals("good", out[0].name)
    }

    @Test fun tzSanity() {
        val t = Instant.parse("2026-08-11T09:05:00Z")
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        fun at(z: String) = ZonedDateTime.ofInstant(t, ZoneId.of(z)).format(hm)
        assertEquals("14:35", at("Asia/Kolkata"))
        assertEquals("13:05", at("Asia/Dubai"))
        assertEquals("10:05", at("Europe/London"))
    }

    @Test fun expandedFieldRoundtripAndLegacy() {
        // 6-field row -> expanded honoured; 5-field legacy row -> collapsed default
        val raw6 = "Z\u001fR\u001fAsia/Kolkata\u001f22.5\u001f88.3\u001ftrue"
        assertTrue(Prefs.decodeClocks(raw6)[0].expanded)
        val raw5 = "Z\u001fR\u001fAsia/Kolkata\u001f22.5\u001f88.3"
        assertFalse(Prefs.decodeClocks(raw5)[0].expanded)
    }

    @Test fun sunSegmentVectors() {
        assertEquals("0611\u20131911", Geo.sunSegment("0611", "1911"))
        assertEquals("\u2014", Geo.sunSegment("0611", null))
        assertEquals("\u2014", Geo.sunSegment(null, null))
    }

    /** K54 — wire-captured Open-Meteo current-temperature payload (Kolkata coords, 11-Aug-2026). */
    private val omTemp = """{"latitude":22.601053,"longitude":88.31776,"generationtime_ms":0.024199485778808594,"utc_offset_seconds":0,"timezone":"GMT","timezone_abbreviation":"GMT","elevation":12.0,"current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C"},"current":{"time":"2026-08-15T16:15","interval":900,"temperature_2m":27.5}}"""

    @Test fun temperatureParsesNumericNotUnits() {
        // units entry ("temperature_2m":"°C") must be skipped; numeric 27.5 wins
        assertEquals(27.5, com.krishna.kalam.ui.Geo.parseTemperature(omTemp)!!, 1e-9)
    }
    @Test fun temperatureGarbageNull() {
        assertNull(com.krishna.kalam.ui.Geo.parseTemperature("{}"))
        assertNull(com.krishna.kalam.ui.Geo.parseTemperature("\"temperature_2m\":\"°C\""))
    }
    @Test fun tempsCodecRoundtripAndBadRow() {   // K54, updated for K70 Wx rows
        val m = mapOf("a" to com.krishna.kalam.data.Wx(27.5, 100L),
                      "b" to com.krishna.kalam.data.Wx(-3.0, 200L, 60.0, 8.0, 1.2, 42))
        val enc = m.entries.joinToString("\u001e") { (k, w) ->
            listOf(k, w.temp.toString(), w.epochMin.toString(), w.hum?.toString() ?: "",
                w.wind?.toString() ?: "", w.precip?.toString() ?: "", w.aqi?.toString() ?: "").joinToString("\u001f") }
        assertEquals(m, Prefs.decodeTemps(enc))
        assertEquals(mapOf("a" to com.krishna.kalam.data.Wx(27.5, 100L)),
            Prefs.decodeTemps("a\u001f27.5\u001f100\u001egarbage-row"))
    }

    @Test fun updatedCaptionVectors() {
        // 2026-08-11T11:02:00Z = 16:32 IST -> Krishna's exact format
        val m1 = java.time.Instant.parse("2026-08-11T11:02:00Z").toEpochMilli() / 60000
        assertEquals("Updated 11/Aug/26 1632 hrs",
            com.krishna.kalam.ui.Geo.updatedCaption(m1, ZoneId.of("Asia/Kolkata")))
        val m2 = java.time.Instant.parse("2026-01-05T03:34:00Z").toEpochMilli() / 60000
        assertEquals("Updated 05/Jan/26 0904 hrs",
            com.krishna.kalam.ui.Geo.updatedCaption(m2, ZoneId.of("Asia/Kolkata")))
    }
}
