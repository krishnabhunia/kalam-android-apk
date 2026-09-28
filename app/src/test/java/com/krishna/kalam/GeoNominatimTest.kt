package com.krishna.kalam
import com.krishna.kalam.ui.Geo
import org.junit.Assert.*
import org.junit.Test

/** K49 vectors. bij/bar/hyd are WIRE-DERIVED: captured live from Nominatim in-container on
 *  11-Aug-2026, fields slimmed, values untouched. X/Y/HydPK ordering vectors are Krishna's
 *  locked examples (synthetic PlaceCands). */
class GeoNominatimTest {
    private val bij = """[{"lat": "18.7935679", "lon": "80.8159390", "name": "Bijapur", "addresstype": "town", "display_name": "Bijapur, Chhattisgarh, India", "address": {"town": "Bijapur", "state": "Chhattisgarh", "country": "India", "country_code": "in"}}, {"lat": "16.8269911", "lon": "75.7175387", "name": "Vijayapura", "addresstype": "city", "display_name": "Vijayapura, Bijapur Taluk, Vijayapura, Karnataka, 586101, India", "address": {"city": "Vijayapura", "state": "Karnataka", "country": "India", "country_code": "in"}}]"""
    private val bar = """[{"lat": "22.2973142", "lon": "73.1942567", "name": "Vadodara", "addresstype": "city", "display_name": "Vadodara, Vadodara Rural Taluka, Vadodara, Gujarat, 390001, India", "address": {"city": "Vadodara", "state": "Gujarat", "country": "India", "country_code": "in"}}, {"lat": "41.9570248", "lon": "-86.4843483", "name": "Baroda", "addresstype": "village", "display_name": "Baroda, Berrien County, Michigan, 49101, United States", "address": {"village": "Baroda", "state": "Michigan", "country": "United States", "country_code": "us"}}, {"lat": "23.7127722", "lon": "78.7112340", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Jaisinagar Tahsil, Sagar, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}, {"lat": "22.5200636", "lon": "76.6728669", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Satwas Tahsil, Dewas, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}, {"lat": "25.4987093", "lon": "76.6717878", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Badoda Tahsil, Sheopur, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}, {"lat": "23.4804414", "lon": "75.0348792", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Ratlam Tahsil, Ratlam, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}, {"lat": "23.3405058", "lon": "77.6757341", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Raisen Tahsil, Raisen, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}, {"lat": "25.7000422", "lon": "78.0922638", "name": "Baroda", "addresstype": "city_district", "display_name": "Baroda, Narwar Tahsil, Shivpuri, Madhya Pradesh, India", "address": {"state": "Madhya Pradesh", "country": "India", "country_code": "in"}}]"""
    private val hyd = """[{"lat": "17.3605890", "lon": "78.4740613", "name": "Hyderabad", "addresstype": "city", "display_name": "Hyderabad, Bahadurpura mandal, Hyderabad, Telangana, India", "address": {"city": "Hyderabad", "state": "Telangana", "country": "India", "country_code": "in"}}, {"lat": "17.3887860", "lon": "78.4610647", "name": "Hyderabad", "addresstype": "state_district", "display_name": "Hyderabad, Telangana, India", "address": {"state": "Telangana", "country": "India", "country_code": "in"}}]"""

    @Test fun bijapurTwoStatesOrdered() {
        val c = Geo.order(Geo.parseNominatim(bij, true))
        assertEquals(2, c.size)
        assertEquals("Bijapur", c[0].name);     assertEquals("Chhattisgarh", c[0].state)
        assertEquals("Vijayapura", c[1].name);  assertEquals("Karnataka", c[1].state)   // wire rename shown as-is
        assertTrue(c.all { it.countryCode == "in" })
        assertEquals(18.7935679, c[0].lat, 1e-6)
    }

    @Test fun barodaAltNameAndInternationalOrder() {
        val c = Geo.order(Geo.parseNominatim(bar, true))
        assertEquals("Vadodara", c.first().name)                       // "Baroda" finds Vadodara
        assertEquals("Gujarat", c.first().state)
        assertTrue(c.first().countryCode == "in")
        val firstForeign = c.indexOfFirst { it.countryCode != "in" }
        assertTrue(firstForeign > 0)                                    // every India row precedes foreign
        assertTrue(c.drop(firstForeign).all { it.countryCode != "in" })
        assertEquals("us", c.last().countryCode)                        // Baroda, Michigan after India rows
        val inStates = c.takeWhile { it.countryCode == "in" }.map { it.state.lowercase() }
        assertEquals(inStates.sorted(), inStates)                       // India states ascending
    }

    @Test fun settlementFilterDropsDistricts() {
        assertEquals(1, Geo.parseNominatim(hyd, true).size)     // city kept, state_district dropped
        assertEquals(2, Geo.parseNominatim(hyd, false).size)    // unfiltered retry sees both
    }

    private fun pc(n: String, st: String, co: String, cc: String) =
        Geo.PlaceCand(n, st, co, cc, 0.0, 0.0)

    @Test fun krishnaXVector() {
        val shuffled = listOf(
            pc("X", "", "Germany", "de"), pc("X", "Uttar Pradesh", "India", "in"),
            pc("X", "", "Sri Lanka", "lk"), pc("X", "Gujarat", "India", "in"),
            pc("X", "", "America", "us"))
        val o = Geo.order(shuffled)
        assertEquals(listOf("Gujarat", "Uttar Pradesh"), o.take(2).map { it.state })
        assertEquals(listOf("America", "Germany", "Sri Lanka"), o.drop(2).map { it.country })
    }

    @Test fun krishnaYVector() {
        val o = Geo.order(listOf(
            pc("Y", "Tamil Nadu", "India", "in"), pc("Y", "Rajasthan", "India", "in")))
        assertEquals(listOf("Rajasthan", "Tamil Nadu"), o.map { it.state })
    }

    @Test fun hyderabadIndiaBeforePakistan() {
        val o = Geo.order(listOf(
            pc("Hyderabad", "Sindh", "Pakistan", "pk"),
            pc("Hyderabad", "Telangana", "India", "in")))
        assertEquals("in", o[0].countryCode); assertEquals("pk", o[1].countryCode)
    }

    @Test fun garbageRejects() { assertTrue(Geo.parseNominatim("not json", true).isEmpty()) }
}
