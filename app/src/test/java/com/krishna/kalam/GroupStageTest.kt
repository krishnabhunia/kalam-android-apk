package com.krishna.kalam

import com.krishna.kalam.data.ClockPlace
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.ui.GroupStage
import org.junit.Assert.*
import org.junit.Test

/** K71 stage cycle + codec · K72 alias codec/shown. */
class GroupStageTest {
    @Test fun tapWalkIsTwoStageBothWays() {
        // A →tap→ B →tap→ C →tap→ B →tap→ A  (exactly Krishna's rule)
        val walk = generateSequence(GroupStage.A) { GroupStage.advance(it) }.take(5).map { GroupStage.visual(it) }.toList()
        assertEquals(listOf(0, 1, 2, 1, 0), walk)
    }

    @Test fun glyphs() {
        assertEquals("▾▾", GroupStage.glyph(0)); assertEquals("▾", GroupStage.glyph(1))
        assertEquals("▸", GroupStage.glyph(2)); assertEquals("▾", GroupStage.glyph(3))
    }

    @Test fun stageCodecRoundtripHostileNames() {
        val m = mapOf("India" to 2, "Korea, Republic of" to 1, "A:B|C" to 3)
        assertEquals(m, GroupStage.decode(GroupStage.encode(m)))
        assertTrue(GroupStage.decode("").isEmpty())
        assertTrue(GroupStage.decode("garbage-no-separator").isEmpty())
    }

    @Test fun stageCodecRejectsBadPos() {
        val raw = "India\u00027\u0001Asia\u00021"
        assertEquals(mapOf("Asia" to 1), GroupStage.decode(raw))
    }

    @Test fun aliasCodecV7RoundtripAndOldRows() {
        val c = ClockPlace("Kolkata", "West Bengal · India", "Asia/Kolkata", 22.57, 88.36, true, "Home")
        val enc = listOf(c.name, c.region, c.tz, c.lat.toString(), c.lon.toString(),
            c.expanded.toString(), c.alias).joinToString("\u001f")
        assertEquals(listOf(c), Prefs.decodeClocks(enc))
        val old6 = "Zurich\u001fZH · Switzerland\u001fEurope/Zurich\u001f47.37\u001f8.54\u001ffalse"
        assertEquals("", Prefs.decodeClocks(old6)[0].alias)   // tolerant upgrade
    }

    @Test fun shownPrefersAlias() {
        assertEquals("Home", ClockPlace("Kolkata", "", "Asia/Kolkata", 0.0, 0.0, false, "Home").shown())
        assertEquals("Kolkata", ClockPlace("Kolkata", "", "Asia/Kolkata", 0.0, 0.0, false, "").shown())
    }
}
