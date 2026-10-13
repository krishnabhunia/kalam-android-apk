package com.krishna.kalam.update

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Publish only verified downloads, retaining prior APKs if verification fails. */
internal object UpdateCache {
    private val ownedName = Regex("Kalam_([0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)\\.apk")

    fun publish(directory: File, staged: File, version: String, verify: (File) -> Unit): File {
        require(ownedName.matches("Kalam_$version.apk") && ReleaseVersion.parse(version) != null) {
            "Invalid update version"
        }
        val root = directory.canonicalFile
        require(staged.canonicalFile.parentFile == root && staged.isFile) { "Invalid staged update" }
        val output = File(root, "Kalam_$version.apk")
        require(output.canonicalFile.parentFile == root) { "Invalid update cache path" }
        try {
            verify(staged)
            Files.move(staged.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
            clearSuperseded(root, output)
            return output
        } finally {
            staged.delete()
        }
    }

    private fun clearSuperseded(directory: File, current: File) {
        directory.listFiles()?.forEach { candidate ->
            val version = ownedName.matchEntire(candidate.name)?.groupValues?.get(1) ?: return@forEach
            if (ReleaseVersion.parse(version) == null || !candidate.isFile) return@forEach
            val resolved = candidate.canonicalFile
            // Never follow a file out of the dedicated cache or remove the newly verified APK.
            if (resolved.parentFile == directory && resolved != current.canonicalFile) candidate.delete()
        }
    }
}
