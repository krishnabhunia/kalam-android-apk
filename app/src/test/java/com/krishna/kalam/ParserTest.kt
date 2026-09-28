package com.krishna.kalam
import com.krishna.kalam.api.Prokerala
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    /** Wire-verified real advanced payload supplied by Krishna, 10-Aug-2026 (descriptions trimmed for brevity
     *  on some items but structure byte-faithful). Primary v3 vector. */
    private val real = """
    {"status":"ok","data":{
      "girl_info":{
        "koot":{"varna":"Brahmin","vasya":"Jalachara","tara":"Janma","yoni":"Gau","graha_maitri":"Jupiter","gana":"Manushya","bhakoot":"Meena","nadi":"Madhya"},
        "nakshatra":{"id":25,"name":"Uttara Bhadrapada","lord":{"id":6,"name":"Saturn","vedic_name":"Shani"},"pada":3},
        "rasi":{"id":11,"name":"Meena","lord":{"id":2,"name":"Mercury","vedic_name":"Budha"}}},
      "boy_info":{
        "koot":{"varna":"Brahmin","vasya":"Jalachara","tara":"Janma","yoni":"Gau","graha_maitri":"Jupiter","gana":"Manushya","bhakoot":"Meena","nadi":"Madhya"},
        "nakshatra":{"id":25,"name":"Uttara Bhadrapada","lord":{"id":6,"name":"Saturn","vedic_name":"Shani"},"pada":3},
        "rasi":{"id":11,"name":"Meena","lord":{"id":2,"name":"Mercury","vedic_name":"Budha"}}},
      "message":{"type":"bad","description":"Union is not recommended due to the presence of Nadi Maha Dosha.  Since Gun Milan Nadi Koot is given supreme priority during match making. The Boy and Girl are not affected by Mangal Dosha"},
      "guna_milan":{"total_points":28,"maximum_points":36,"guna":[
        {"id":1,"name":"Varna","girl_koot":"Brahmin","boy_koot":"Brahmin","maximum_points":1,"obtained_points":1,"description":"Varna represents the working attitude and capacity. For this couple Varna Koot is Good."},
        {"id":2,"name":"Vasya","girl_koot":"Jalachara","boy_koot":"Jalachara","maximum_points":2,"obtained_points":2,"description":"For this couple Vasya Koot is Excellent."},
        {"id":3,"name":"Tara","girl_koot":"Janma","boy_koot":"Janma","maximum_points":3,"obtained_points":3,"description":"The boy's nakshatra Uttara Bhadrapada is 0 th position from girl's nakshatra Uttara Bhadrapada and this is Benefic. For this couple Tara Koot is Excellent."},
        {"id":4,"name":"Yoni","girl_koot":"Gau","boy_koot":"Gau","maximum_points":4,"obtained_points":4,"description":"For this couple Yoni Koot is Excellent."},
        {"id":5,"name":"Graha Maitri","girl_koot":"Jupiter","boy_koot":"Jupiter","maximum_points":5,"obtained_points":5,"description":"For this couple Graha Maitri Koot is Excellent."},
        {"id":6,"name":"Gana","girl_koot":"Manushya","boy_koot":"Manushya","maximum_points":6,"obtained_points":6,"description":"For this couple Gana Koot is Excellent."},
        {"id":7,"name":"Bhakoot","girl_koot":"Meena","boy_koot":"Meena","maximum_points":7,"obtained_points":7,"description":"For this couple Bhakoot Koot is Excellent."},
        {"id":8,"name":"Nadi","girl_koot":"Madhya","boy_koot":"Madhya","maximum_points":8,"obtained_points":0,"description":"Nadi Koot is given supreme priority during match making. This is inauspicious combination. For this couple Nadi Koot is not Good."}]},
      "girl_mangal_dosha_details":{"has_dosha":false,"has_exception":false,"dosha_type":null,"description":"The person is Not Manglik"},
      "boy_mangal_dosha_details":{"has_dosha":false,"has_exception":false,"dosha_type":null,"description":"The person is Not Manglik"},
      "exceptions":[]}}
    """

    @Test fun realPayloadParsesFully() {
        val r = Prokerala.parse(real, false)!!
        assertEquals(28.0, r.total, 0.001)
        assertEquals(36.0, r.max, 0.001)
        assertEquals(8, r.kootas.size)
        assertTrue(r.extraNone.isEmpty())
        assertEquals("Graha Maitri", r.kootas[4].name)          // v2 could never parse this row
        assertEquals("Brahmin", r.kootas[0].girl)
        assertEquals(0.0, r.kootas[7].points, 0.001)
        assertEquals(8.0, r.kootas[7].max, 0.001)
        assertTrue(r.kootas[0].desc.contains("Varna Koot is Good"))
        assertEquals(Prokerala.DoshaColor.RED, r.unionColor)    // 28/36 yet type "bad" — type wins
        assertEquals("Meena", r.girl.rasi)
        assertEquals("Mercury", r.girl.rasiLord)                // v2 bug showed Saturn here
        assertEquals("Budha", r.girl.rasiLordVedic)
        assertEquals("Uttara Bhadrapada", r.girl.nakshatra)
        assertEquals("Saturn", r.girl.nakshatraLord)
        assertEquals("Shani", r.girl.nakshatraLordVedic)
        assertEquals("3", r.girl.pada)
        assertEquals(Prokerala.DoshaColor.GREEN, r.girlDosha.color)
        assertEquals("The person is Not Manglik", r.girlDosha.text)   // null dosha_type -> no suffix
    }

    @Test fun minifiedVariantParses() {
        val r = Prokerala.parse(real.replace(": ", ":").replace(", ", ",").replace("\n", "").replace("  ", ""), false)!!
        assertEquals(8, r.kootas.size)
        assertEquals("Graha Maitri", r.kootas[4].name)
        assertEquals("Mercury", r.boy.rasiLord)
    }

    @Test fun legacyFlatShapeStillParses() {   // regression: v1.1 synthetic (girl_varna keys, name "Maitri")
        val flat = """{"guna_milan":{"total_points":27,"maximum_points":36,"guna":[
          {"name":"Varna","girl_varna":"Brahmin","boy_varna":"Brahmin","obtained_points":1,"maximum_points":1},
          {"name":"Maitri","girl_maitri":"Mars","boy_maitri":"Mars","obtained_points":5,"maximum_points":5}]},
          "message":{"type":"good","description":"Union is Very Good."}}"""
        val r = Prokerala.parse(flat, false)!!
        assertEquals(2, r.kootas.size)
        assertEquals("Brahmin", r.kootas[0].girl)
        assertEquals("Maitri", r.kootas[1].name)               // wire name displayed as-is
        assertEquals(Prokerala.DoshaColor.GREEN, r.unionColor)
    }

    @Test fun nestedPointsVariant() {
        val j = """{"guna_milan":{"total_points":10,"maximum_points":36,"guna":[
          {"name":"Varna","girl_koot":"X","boy_koot":"Y","points":{"obtained":1,"maximum":1}}]}}"""
        val r = Prokerala.parse(j, false)!!
        assertEquals(1, r.kootas.size)
        assertEquals(1.0, r.kootas[0].points, 0.001)
    }

    @Test fun doshaExceptionIsYellowAndTypeSuffixed() {   // K36
        val j = """{"guna_milan":{"total_points":20,"maximum_points":36},
          "girl_mangal_dosha_details":{"has_dosha":true,"has_exception":true,"dosha_type":"Anshik","description":"Is Manglik"},
          "boy_mangal_dosha_details":{"has_dosha":true,"has_exception":false,"dosha_type":"Purna","description":"Is Manglik"}}"""
        val r = Prokerala.parse(j, false)!!
        assertEquals(Prokerala.DoshaColor.YELLOW, r.girlDosha.color)
        assertEquals("Is Manglik \u00b7 Anshik", r.girlDosha.text)
        assertEquals(Prokerala.DoshaColor.RED, r.boyDosha.color)
        assertEquals("Is Manglik \u00b7 Purna", r.boyDosha.text)
    }

    @Test fun arrayAbsentFallsBackWithNone() {
        val r = Prokerala.parse("""{"guna_milan":{"total_points":18,"maximum_points":36}}""", false)!!
        assertEquals(0, r.kootas.size)
        assertEquals(8, r.extraNone.size)
        assertEquals("NONE", r.girl.rasi)
        assertEquals(Prokerala.DoshaColor.YELLOW, r.girlDosha.color)
    }

    @Test fun rejectsGarbage() { assertNull(Prokerala.parse("not json at all", false)) }
}
