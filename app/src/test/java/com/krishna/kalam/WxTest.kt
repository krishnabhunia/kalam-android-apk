package com.krishna.kalam

import com.krishna.kalam.data.Prefs
import com.krishna.kalam.data.Wx
import com.krishna.kalam.ui.Geo
import org.junit.Assert.*
import org.junit.Test

/** K70 — codec v1→v2, parsers (real Open-Meteo payloads baked 14-Sep-2026), AQI bands. */
class WxTest {
    private val FS = "\u001f"; private val RS = "\u001e"

    @Test fun v1RowDecodesWithNullExtras() {
        val m = Prefs.decodeTemps("k1${FS}31.4${FS}29000000")
        assertEquals(Wx(31.4, 29000000L), m["k1"])
    }

    @Test fun v2RoundtripAndBlanks() {
        val row = listOf("k2", "29.6", "29000100", "77.0", "5.0", "0.0", "100").joinToString(FS)
        val m = Prefs.decodeTemps(row)
        assertEquals(Wx(29.6, 29000100L, 77.0, 5.0, 0.0, 100), m["k2"])
        val partial = Prefs.decodeTemps(listOf("k3", "20.0", "29000200", "", "", "", "").joinToString(FS))
        assertEquals(Wx(20.0, 29000200L), partial["k3"])
    }

    @Test fun badRowNeverKillsMap() {
        val m = Prefs.decodeTemps("garbage${RS}k4${FS}18.2${FS}29000300")
        assertEquals(1, m.size); assertEquals(18.2, m["k4"]!!.temp, 1e-9)
    }

    @Test fun parseRealPayload() {   // exact wire capture, Kolkata 14-Sep-2026
        val j = """{"current_units":{"temperature_2m":"°C","relative_humidity_2m":"%","precipitation":"mm","wind_speed_10m":"km/h"},"current":{"time":"2026-09-14T13:30","interval":900,"temperature_2m":29.6,"relative_humidity_2m":77,"precipitation":0.00,"wind_speed_10m":5.0}}"""
        assertEquals(29.6, Geo.parseTemperature(j)!!, 1e-9)
        assertEquals(77.0, Geo.parseNum(j, "relative_humidity_2m")!!, 1e-9)
        assertEquals(5.0, Geo.parseNum(j, "wind_speed_10m")!!, 1e-9)
        assertEquals(0.0, Geo.parseNum(j, "precipitation")!!, 1e-9)
        val aq = """{"current_units":{"us_aqi":"USAQI"},"current":{"time":"2026-09-14T13:00","interval":3600,"us_aqi":100}}"""
        assertEquals(100, Geo.parseNum(aq, "us_aqi")!!.toInt())
    }

    @Test fun aqiBands() {
        assertEquals("Good", Geo.aqiBand(50)); assertEquals("Moderate", Geo.aqiBand(100))
        assertEquals("Sensitive", Geo.aqiBand(132)); assertEquals("Unhealthy", Geo.aqiBand(200))
        assertEquals("Very Unhealthy", Geo.aqiBand(300)); assertEquals("Hazardous", Geo.aqiBand(301))
    }
}
