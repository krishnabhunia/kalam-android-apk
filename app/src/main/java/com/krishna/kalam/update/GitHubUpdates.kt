package com.krishna.kalam.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.krishna.kalam.AppVersion
import com.krishna.kalam.core.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class UpdateStatus(val busy: Boolean = false, val message: String = "Not checked yet",
                        val release: GitHubRelease? = null, val apk: File? = null)

object GitHubUpdates {
    private val mutable = MutableStateFlow(UpdateStatus())
    val status = mutable.asStateFlow()
    private val lock = Mutex()

    suspend fun check(includeBeta: Boolean) = lock.withLock {
        mutable.value = UpdateStatus(busy = true, message = "Checking GitHub…")
        try {
            val releases = withContext(Dispatchers.IO) {
                val found = mutableListOf<GitHubRelease>()
                // Pagination avoids relying on release creation order (backports can be newer rows).
                for (page in 1..10) {
                    val json = request("https://api.github.com/repos/${ReleasePolicy.REPO}/releases?per_page=100&page=$page") {
                        readBounded(it, 8L * 1024 * 1024).toString(Charsets.UTF_8)
                    }
                    val rows = org.json.JSONArray(json)
                    found += ReleasePolicy.parseReleases(json)
                    if (rows.length() < 100) break
                    check(page < 10) { "Too many releases to check safely" }
                }
                found
            }
            val release = ReleasePolicy.newer(releases, AppVersion.NAME, includeBeta)
            mutable.value = UpdateStatus(message = if (release != null) "Version ${release.version} is available"
                else "No newer ${if (includeBeta) "stable or beta" else "stable"} version is available", release = release)
            Logger.e("Updates", "GitHub check completed; update=${release?.version ?: "none"}")
        } catch (e: Exception) {
            if (e is CancellationException) { mutable.value = UpdateStatus(); throw e }
            Logger.e("Updates", "GitHub check failed", e)
            mutable.value = UpdateStatus(message = "Could not check GitHub. ${e.message ?: "Try again when connected."}")
        }
    }

    suspend fun download(ctx: Context, release: GitHubRelease) = lock.withLock {
        mutable.value = UpdateStatus(busy = true, message = "Downloading and verifying ${release.version}…", release = release)
        try {
            val apk = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                val zip = File(dir, "download.zip")
                val output = File(dir, "Kalam_${release.version}.apk")
                output.delete()
                try {
                    request(release.zipUrl) { input ->
                        zip.outputStream().use { out ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(32768); var total = 0L
                            while (true) {
                                val n = input.read(buffer); if (n == -1) break
                                total += n
                                require(total <= ReleasePolicy.MAX_BYTES && total <= release.size) { "Update download is too large" }
                                digest.update(buffer, 0, n); out.write(buffer, 0, n)
                            }
                            require(total == release.size && hex(digest.digest()) == release.digest) { "Update checksum did not match; try again" }
                        }
                    }
                    extract(zip, output, "Android/Kalam_${release.version}.apk")
                    verify(ctx, output, release.version)
                    output
                } catch (e: Exception) { output.delete(); throw e }
                finally { zip.delete() }
            }
            mutable.value = UpdateStatus(message = "Verified ${release.version}. Ready to install.", release = release, apk = apk)
            Logger.e("Updates", "download and installed-signature verification passed ${release.version}")
        } catch (e: Exception) {
            if (e is CancellationException) { mutable.value = UpdateStatus(release = release); throw e }
            Logger.e("Updates", "download/verification failed", e)
            mutable.value = UpdateStatus(message = e.message ?: "Update could not be downloaded", release = release)
        }
    }

    fun verify(ctx: Context, apk: File, version: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val installed = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
        @Suppress("DEPRECATION")
        val archive = ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Downloaded APK could not be read")
        fun code(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else {
            @Suppress("DEPRECATION")
            p.versionCode.toLong()
        }
        fun certs(p: PackageInfo): Set<String> {
            @Suppress("DEPRECATION")
            val signatures = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
            return signatures?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet() ?: emptySet()
        }
        ReleasePolicy.validateApk(archive.packageName, ctx.packageName, code(archive), code(installed),
            archive.versionName, version, certs(archive), certs(installed))
    }

    internal fun extract(zip: File, output: File, expected: String) {
        var found = false
        ZipInputStream(zip.inputStream()).use { input ->
            var entries = 0
            while (true) {
                val entry = input.nextEntry ?: break
                require(++entries <= 100) { "Unexpected update archive" }
                if (entry.name == expected && !entry.isDirectory) {
                    require(!found) { "Duplicate APK in update archive" }
                    found = true
                    output.outputStream().use { out ->
                        val buffer = ByteArray(32768); var total = 0L
                        while (true) {
                            val n = input.read(buffer); if (n == -1) break
                            total += n; require(total <= ReleasePolicy.MAX_BYTES) { "Update APK is too large" }
                            out.write(buffer, 0, n)
                        }
                        require(total > 0) { "Update APK is empty" }
                    }
                } else {
                    // Reject foreign payloads; the release contract is one Android folder/APK.
                    require(entry.isDirectory && entry.name == "Android/") { "Unexpected file in update archive" }
                }
                input.closeEntry()
            }
        }
        require(found) { "Update archive does not contain the expected Android APK" }
    }

    private fun <T> request(url: String, read: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", AppVersion.USER_AGENT)
        connection.setRequestProperty("Accept", if (url.startsWith("https://api.github.com/")) "application/vnd.github+json" else "application/octet-stream")
        try {
            val code = connection.responseCode
            require(code == 200) { if (code == 403 || code == 429) "GitHub rate limit reached; try later." else "GitHub returned HTTP $code" }
            require(connection.url.protocol == "https") { "Insecure update connection" }
            return connection.inputStream.use(read)
        } finally { connection.disconnect() }
    }
    private fun readBounded(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
        while (true) {
            val n = input.read(buffer); if (n == -1) break
            require(out.size().toLong() + n <= max) { "GitHub response too large" }; out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
