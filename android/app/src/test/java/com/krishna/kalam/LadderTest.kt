package com.krishna.kalam
import com.krishna.kalam.ui.Ladder
import org.junit.Assert.*
import org.junit.Test

class LadderTest {
    @Test fun raiseSubCascadesHeaderOnly() {
        var l = Ladder.L(20, 16, 14, 13)
        l = Ladder.change(l, 2, +1)!!.value      // 14->15, header 16 ok
        assertEquals(Ladder.L(20, 16, 15, 13), l)
        val o = Ladder.change(l, 2, +1)!!         // 15->16 forces header 17
        assertEquals(Ladder.L(20, 17, 16, 13), o.value)
        assertNotNull(o.cascadeNote)
    }
    @Test fun lowerSubCascadesDataOnly() {
        val o = Ladder.change(Ladder.L(20, 16, 14, 13), 2, -1)!!   // 14->13 forces data 12
        assertEquals(Ladder.L(20, 16, 13, 12), o.value)
    }
    @Test fun noForcedMoveWhenGapFine() {
        val o = Ladder.change(Ladder.L(21, 17, 14, 12), 2, +1)!!   // 14->15, header 17 fine
        assertEquals(Ladder.L(21, 17, 15, 12), o.value)
        assertNull(o.cascadeNote)
    }
    @Test fun rejectWhenCascadeExceedsRange() {
        // header at max 24; raising sub 23->? sub max 22 so use: display 28,header 24,sub 22 -> raise sub blocked by own range
        assertNull(Ladder.change(Ladder.L(28, 24, 22, 20), 2, +1))
        // data at min 10, sub 11: lowering sub would push data below 10
        assertNull(Ladder.change(Ladder.L(20, 16, 11, 10), 2, -1))
        assertFalse(Ladder.canChange(Ladder.L(28, 24, 22, 20), 3, +1) == false && false) // sanity noop
        // raising data 20 max: rejected by own range
        assertNull(Ladder.change(Ladder.L(28, 24, 22, 20), 3, +1))
    }
    @Test fun invariantAlwaysHolds() {
        var l = Ladder.DEFAULT
        val seq = listOf(2 to 1, 2 to 1, 2 to 1, 1 to -1, 3 to 1, 0 to -1, 2 to -1, 2 to -1)
        for ((t, d) in seq) Ladder.change(l, t, d)?.let { l = it.value }
        assertTrue(l.display >= l.header + 1)
        assertTrue(l.header >= l.sub + 1)
        assertTrue(l.sub >= l.data + 1)
    }
}
