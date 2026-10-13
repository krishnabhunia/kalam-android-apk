package com.krishna.kalam.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateCacheTest {
    @Test fun verifiedDownloadRemovesOldOwnedApksAndPreservesOtherFiles() {
        val directory = kotlin.io.path.createTempDirectory("update-cache").toFile()
        try {
            fun file(name: String) = File(directory, name).apply { writeText(name) }
            val older = file("Kalam_1.24.0.apk")
            val beta = file("Kalam_1.25.0-beta.1.apk")
            val unrelated = file("holiday.apk")
            val invalidVersion = file("Kalam_1.024.0.apk")
            val shortVersion = file("Kalam_1.24.apk")
            val nested = File(directory, "nested").apply { mkdir() }
            val nestedApk = File(nested, "Kalam_1.24.0.apk").apply { writeText("leave this file") }
            val staged = file("download-in-progress.apk")
            val output = UpdateCache.publish(directory, staged, "1.25.0") {
                assertEquals("download-in-progress.apk", it.readText())
            }
            assertEquals("Kalam_1.25.0.apk", output.name)
            assertEquals("download-in-progress.apk", output.readText())
            assertFalse(older.exists()); assertFalse(beta.exists()); assertFalse(staged.exists())
            assertTrue(unrelated.exists()); assertTrue(invalidVersion.exists()); assertTrue(shortVersion.exists())
            assertTrue(nestedApk.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun failedVerificationKeepsPriorVerifiedApkEvenForTheSameVersion() {
        val directory = kotlin.io.path.createTempDirectory("failed-update").toFile()
        try {
            val previous = File(directory, "Kalam_1.25.0.apk").apply { writeText("previous verified download") }
            val older = File(directory, "Kalam_1.24.0.apk").apply { writeText("older verified download") }
            val staged = File(directory, "download-in-progress.apk").apply { writeText("bad new download") }
            assertThrows(IllegalArgumentException::class.java) {
                UpdateCache.publish(directory, staged, "1.25.0") { error ->
                    assertEquals("bad new download", error.readText())
                    throw IllegalArgumentException("Certificate mismatch")
                }
            }
            assertEquals("previous verified download", previous.readText())
            assertEquals("older verified download", older.readText())
            assertFalse(staged.exists())
        } finally { directory.deleteRecursively() }
    }
}
