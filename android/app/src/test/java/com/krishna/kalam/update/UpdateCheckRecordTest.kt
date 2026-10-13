package com.krishna.kalam.update

import org.junit.Assert.*
import org.junit.Test

class UpdateCheckRecordTest {
    @Test fun resultSurvivesProcessRestartWithBetaChoice() {
        val record = UpdateCheckRecord(1_700_000_000_000, "Version 1.25.0-beta.1 is available", true, "1.24.0")
        assertEquals(record, UpdateCheckRecord.decode(record.encode(), "1.24.0"))
        val stable = record.copy(includeBeta = false, message = "No newer stable version is available")
        assertEquals(stable, UpdateCheckRecord.decode(stable.encode(), "1.24.0"))
    }

    @Test fun previousInstallOrDamagedHistoryDoesNotClaimAnUpdate() {
        val record = UpdateCheckRecord(100, "Version 1.25.0 is available", false, "1.24.0")
        assertNull(UpdateCheckRecord.decode(record.encode(), "1.25.0"))
        assertNull(UpdateCheckRecord.decode("not json", "1.24.0"))
        assertNull(UpdateCheckRecord.decode(null, "1.24.0"))
        assertNull(UpdateCheckRecord.decode(record.copy(at = 0).encode(), "1.24.0"))
        assertNull(UpdateCheckRecord.decode(record.copy(message = " ").encode(), "1.24.0"))
    }
}
