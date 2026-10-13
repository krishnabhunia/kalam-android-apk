package com.krishna.kalam

import com.krishna.kalam.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ClockBackupTest {
    private val clocks = listOf(
        ClockPlace("মেদিনীপুর", "West Bengal · India", "Asia/Kolkata", 22.425, 87.319, true, "Home \"clock\"\nবাংলা"),
        ClockPlace("London", "England · United Kingdom", "Europe/London", 51.507, -0.127, false, "Work")
    )
    private val settings = Settings(clockSort = "NAME", clockGroup = "COUNTRY_STATE",
        groupStages = "India\u00022\u0001United Kingdom\u00021", sortDir = "1")
    private fun raw() = ClockBackup.encode(ClockBackup.snapshot(clocks, settings))

    @Test fun completeClockRoundTripPreservesUnicodeAliasesAndListOrder() {
        val restored = ClockBackup.decode(raw())
        assertEquals(clocks, restored.clocks)
        assertEquals("NAME", restored.sort)
        assertEquals("COUNTRY_STATE", restored.group)
        assertEquals(settings.groupStages, restored.stages)
        assertEquals("1", restored.direction)
    }

    @Test fun restoreOnlyChangesClockDisplayPreferences() {
        val existing = Settings(boyName = "Example", apiClientSecret = "example-test-secret", fontData = 25)
        val restored = ClockBackup.decode(raw()).applyTo(existing)
        assertEquals(existing.apiClientSecret, restored.apiClientSecret)
        assertEquals(existing.boyName, restored.boyName)
        assertEquals(existing.fontData, restored.fontData)
        assertEquals(settings.clockSort, restored.clockSort)
        assertFalse(raw().contains("apiClientSecret"))
        assertFalse(raw().contains("boyName"))
    }

    private fun rejected(json: String) {
        try { ClockBackup.decode(json); fail("Invalid backup accepted") } catch (_: Exception) { }
    }

    @Test fun rejectsUnsupportedBackupVersionsAndForeignFiles() {
        rejected(JSONObject(raw()).put("version", 2).toString())
        rejected(JSONObject(raw()).put("format", "other-app").toString())
        rejected("not a backup")
        rejected(raw() + " trailing garbage")
    }

    @Test fun rejectsInvalidClockRatherThanPartiallyRestoring() {
        for ((key, value) in listOf("latitude" to 100.0, "longitude" to -181.0,
            "timezone" to "Invalid/Zone", "expanded" to "true", "name" to "", "alias" to "x\u001fy")) {
            val root = JSONObject(raw())
            root.getJSONArray("clocks").getJSONObject(1).put(key, value)
            rejected(root.toString())
        }
    }

    @Test fun rejectsUnknownGroupingAndMissingFields() {
        val root = JSONObject(raw())
        root.getJSONObject("display").put("group", "UNKNOWN")
        rejected(root.toString())
        val missing = JSONObject(raw())
        missing.getJSONArray("clocks").getJSONObject(0).remove("timezone")
        rejected(missing.toString())
    }

    @Test fun boundedInputRejectsOversizedFileAndBadUtf8() {
        try { ClockBackup.read(ByteArray(ClockBackup.MAX_BYTES + 1).inputStream()); fail("Oversized file accepted") }
        catch (_: IllegalArgumentException) { }
        try { ClockBackup.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream()); fail("Bad UTF-8 accepted") }
        catch (_: java.nio.charset.CharacterCodingException) { }
    }

    @Test fun rejectsDuplicateLocationsThatWouldCrashTheClockList() {
        val root = JSONObject(raw())
        val first = root.getJSONArray("clocks").getJSONObject(0)
        root.getJSONArray("clocks").put(JSONObject(first.toString()).put("alias", "copy"))
        rejected(root.toString())
    }

    @Test fun deeplyNestedFileIsRejectedNotAStackOverflowCrash() {
        // StackOverflowError is not an Exception; the restore screen used to crash on such a file.
        try { ClockBackup.decode("[".repeat(200_000)); fail("Nested file accepted") }
        catch (_: IllegalArgumentException) { }
        // Android's org.json accepts 'single quotes' and comments; brackets hidden in them must not
        // fool the scan (the reviewer's bypass: closing brackets in a quoted pad, then deep nesting).
        try { ClockBackup.decode("{'pad':'" + "]".repeat(20_000) + "','b':" + "[".repeat(20_000) + "}"); fail("Bypass accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(Int.MAX_VALUE, ClockBackup.nesting("{/*[*/}"))
        assertEquals(3, ClockBackup.nesting(raw()))
        assertEquals(1, ClockBackup.nesting("{\"a\":\"[[[{{{\\\"\"}"))
    }

    @Test fun fileRoundTripAndEmptyBackup() {
        assertEquals(clocks, ClockBackup.read(raw().byteInputStream()).clocks)
        val empty = ClockBackup.snapshot(emptyList(), Settings())
        assertEquals(empty, ClockBackup.decode(ClockBackup.encode(empty)))
    }
}
