package com.krishna.kalam
import com.krishna.kalam.ui.Geo
import org.junit.Assert.*
import org.junit.Test

class GeoTest {
    @Test fun fullLabel() = assertEquals("Bijapur \u00b7 Karnataka \u00b7 IN", Geo.label("Bijapur", "Karnataka", "IN"))
    @Test fun missingAdmin() = assertEquals("Bajkul \u00b7 IN", Geo.label("Bajkul", null, "IN"))
    @Test fun missingCountry() = assertEquals("Bijapur \u00b7 Chhattisgarh", Geo.label("Bijapur", "Chhattisgarh", ""))
    @Test fun allMissing() = assertEquals("Unknown place", Geo.label(" ", null, null))
    @Test fun dedupeKeyCollapses() {
        assertEquals(Geo.key(16.83021, 75.71004), Geo.key(16.83019, 75.70996))
        assertNotEquals(Geo.key(16.8302, 75.7100), Geo.key(18.7900, 80.8200))
    }
}
