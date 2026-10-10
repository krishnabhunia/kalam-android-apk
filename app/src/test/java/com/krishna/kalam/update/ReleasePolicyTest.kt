package com.krishna.kalam.update

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReleasePolicyTest {
    private fun version(v: String) = requireNotNull(ReleaseVersion.parse(v))
    private fun release(v: String, beta: Boolean = false) = GitHubRelease(v, version(v), beta, "", "", "", 1)
    @Test fun semanticOrderHandlesNumericBetasAndStablePromotion() {
        assertTrue(version("1.24.0-beta.3.10.1") > version("1.24.0-beta.3.9.1"))
        assertTrue(version("1.24.0") > version("1.24.0-beta.99"))
        assertTrue(version("1.10.0") > version("1.9.9"))
        assertEquals(version("v1.21"), version("1.21.0"))
        assertEquals(0, version("1.24.0+build.1").compareTo(version("1.24.0+build.2")))
        assertNull(ReleaseVersion.parse("1.024.0"))
        assertNull(ReleaseVersion.parse("1.24.0-beta.01"))
        assertNull(ReleaseVersion.parse("1.24.0-beta..1"))
    }
    @Test fun stableDefaultAndOptionalBetaNeverDowngrade() {
        val releases = listOf(release("1.25.0-beta.3", true), release("1.24.0"), release("1.23.0"))
        assertEquals("1.24.0", ReleasePolicy.newer(releases, "1.23.0", false)?.version)
        assertEquals("1.25.0-beta.3", ReleasePolicy.newer(releases, "1.23.0", true)?.version)
        assertNull(ReleasePolicy.newer(releases, "1.25.0-beta.4", false))
        assertNull(ReleasePolicy.newer(releases, "1.25.0", true))
        assertEquals("1.24.0", ReleasePolicy.newer(releases, "1.24.0-beta.3", false)?.version)
    }
    @Test fun liveGitHubFixtureHasCorrectArchiveAndChecksum() {
        val json = javaClass.getResource("/github-releases.json")!!.readText()
        val releases = ReleasePolicy.parseReleases(json)
        assertEquals("1.23.0", releases.first().version)
        assertEquals("a134a8a2de2929a0b4a62003d7ea6983f79cbda1f1a5fde17d98773e6dd57f9c", releases.first().digest)
        assertEquals("1.23.0", ReleasePolicy.newer(releases, "1.22.0", false)?.version)
        assertTrue(ReleasePolicy.parseReleases(json.replace("sha256:", "invalid:")).isEmpty())
        assertTrue(ReleasePolicy.parseReleases(json.replace("https://github.com/", "https://evil.example/")).isEmpty())
        val drafts = org.json.JSONArray(json)
        for (i in 0 until drafts.length()) drafts.getJSONObject(i).put("draft", true)
        assertTrue(ReleasePolicy.parseReleases(drafts.toString()).isEmpty())
    }
    @Test fun rejectsTheActualCertificateConflictAndBadPackages() {
        val old = setOf("415527987baa3502a6be573392e6303ac07f843a3cfd6075d94618867c171bf9")
        val new = setOf("04a523b9caabeee3b6dca1cc351833096d9fedd4c1df5db91e5d7770ee5e95d5")
        fun verify(pkg: String = "com.krishna.kalam", code: Long = 33, name: String = "1.23.0", cert: Set<String> = old) {
            ReleasePolicy.validateApk(pkg, "com.krishna.kalam", code, 29, name, "1.23.0", cert, old)
        }
        verify()
        assertThrows(IllegalArgumentException::class.java) { verify(cert = new) }
        assertThrows(IllegalArgumentException::class.java) { verify(cert = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { verify(pkg = "com.other.app") }
        assertThrows(IllegalArgumentException::class.java) { verify(code = 29) }
        assertThrows(IllegalArgumentException::class.java) { verify(name = "1.22.0") }
    }
    @Test fun archiveExtractorRejectsForeignPayloads() {
        val dir = kotlin.io.path.createTempDirectory("update-test").toFile()
        try {
            val zip = File(dir, "update.zip"); val out = File(dir, "update.apk")
            fun archive(vararg names: String) {
                ZipOutputStream(zip.outputStream()).use { stream -> names.forEach {
                    stream.putNextEntry(ZipEntry(it)); stream.write(byteArrayOf(1, 2, 3)); stream.closeEntry()
                } }
            }
            archive("Android/Kalam_1.23.0.apk")
            GitHubUpdates.extract(zip, out, "Android/Kalam_1.23.0.apk")
            assertArrayEquals(byteArrayOf(1, 2, 3), out.readBytes())
            archive("../Kalam.apk")
            assertThrows(IllegalArgumentException::class.java) { GitHubUpdates.extract(zip, out, "Android/Kalam_1.23.0.apk") }
            archive("Android/Kalam_1.22.0.apk")
            assertThrows(IllegalArgumentException::class.java) { GitHubUpdates.extract(zip, out, "Android/Kalam_1.23.0.apk") }
        } finally { dir.deleteRecursively() }
    }
}
