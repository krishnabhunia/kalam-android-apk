package com.krishna.kalam.update

import org.json.JSONArray
import java.math.BigInteger

/** SemVer precedence, including numeric beta components (beta.10 > beta.2). */
data class ReleaseVersion(val major: BigInteger, val minor: BigInteger, val patch: BigInteger,
                          val pre: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        for ((a, b) in listOf(major to other.major, minor to other.minor, patch to other.patch)) {
            if (a != b) return a.compareTo(b)
        }
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            else -> -1
        }
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            if (a == b) continue
            val an = a.toBigIntegerOrNull(); val bn = b.toBigIntegerOrNull()
            return when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(value: String): ReleaseVersion? {
            val m = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?").matchEntire(value) ?: return null
            val pre = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            val build = m.groupValues[5].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if ((pre + build).any { it.isEmpty() } || pre.any { it.all(Char::isDigit) && it.length > 1 && it[0] == '0' }) return null
            return ReleaseVersion(m.groupValues[1].toBigInteger(), m.groupValues[2].toBigInteger(),
                m.groupValues[3].ifEmpty { "0" }.toBigInteger(), pre)
        }
    }
}

data class GitHubRelease(val version: String, val parsed: ReleaseVersion, val beta: Boolean,
                         val url: String, val zipUrl: String, val digest: String, val size: Long)

object ReleasePolicy {
    const val REPO = "krishnabhunia/kalam-android-apk"
    const val MAX_BYTES = 100L * 1024 * 1024
    fun parseReleases(json: String): List<GitHubRelease> {
        val rows = JSONArray(json)
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            if (row.optBoolean("draft")) return@mapNotNull null
            val tag = row.optString("tag_name")
            val version = tag.removePrefix("v")
            val parsed = ReleaseVersion.parse(tag) ?: return@mapNotNull null
            val expected = "Kalam_${version}.zip"
            val assets = row.optJSONArray("assets") ?: return@mapNotNull null
            val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
                .filter { it.optString("name") == expected && it.optString("state") == "uploaded" }
            if (matches.size != 1) return@mapNotNull null
            val asset = matches.single()
            val url = asset.optString("browser_download_url")
            val prefix = "https://github.com/$REPO/releases/download/"
            if (url != "${prefix}${tag}/${expected}") return@mapNotNull null
            val digest = asset.optString("digest").removePrefix("sha256:")
            if (!Regex("[a-fA-F0-9]{64}").matches(digest)) return@mapNotNull null
            val size = asset.optLong("size")
            if (size !in 1..MAX_BYTES) return@mapNotNull null
            GitHubRelease(version, parsed, row.optBoolean("prerelease") || parsed.pre.isNotEmpty(),
                "https://github.com/$REPO/releases/tag/$tag", url, digest.lowercase(), size)
        }
    }
    fun newer(releases: List<GitHubRelease>, installed: String, includeBeta: Boolean): GitHubRelease? {
        val current = requireNotNull(ReleaseVersion.parse(installed)) { "Installed version is not recognized" }
        return releases.filter { (includeBeta || !it.beta) && it.parsed > current }.maxByOrNull { it.parsed }
    }
    fun validateApk(packageName: String, expectedPackage: String, code: Long, installedCode: Long,
                    version: String?, expectedVersion: String, certificates: Set<String>, installedCertificates: Set<String>) {
        require(packageName == expectedPackage) { "This APK belongs to a different app" }
        require(version == expectedVersion && code > installedCode) { "This APK is not a newer installable version" }
        require(certificates.isNotEmpty() && certificates == installedCertificates) {
            "This update uses a different signing key. Your installed app cannot accept it. Keep your clocks backed up; this updater will not uninstall Kalam."
        }
    }
}
