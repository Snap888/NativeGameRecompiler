package com.ngr.app

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Extracts a catalog component ZIP into a fresh private staging directory.
 *
 * It never activates, loads or executes the extracted payload. The caller is responsible for
 * atomically promoting a successfully validated staging directory into the version store.
 */
object NgrComponentArchiveInstaller {
    data class Limits(
        val maxArchiveBytes: Long = 512L * 1024 * 1024,
        val maxEntries: Int = 2048,
        val maxPathChars: Int = 1024,
        val maxFileBytes: Long = 256L * 1024 * 1024,
        val maxTotalBytes: Long = 1024L * 1024 * 1024,
        val maxCompressionRatio: Long = 100
    )

    data class Result(
        val success: Boolean,
        val stagingDirectory: File? = null,
        val error: String? = null,
        val extractedFiles: Int = 0,
        val extractedBytes: Long = 0
    )

    fun extractZip(
        archive: File,
        component: NgrComponentCatalog.Component,
        stagingParent: File,
        limits: Limits = Limits()
    ): Result {
        if (component.archiveFormat != NgrComponentCatalog.ArchiveFormat.ZIP) {
            return Result(false, error = "Only ZIP component archives are supported by this installer")
        }
        if (!component.id.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$")) ||
            !isSafeRelativePath(component.installSubdirectory)) {
            return Result(false, error = "Component ID or install subdirectory is unsafe")
        }
        if (component.sha256.length != 64 || !component.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            return Result(false, error = "Component SHA-256 metadata is invalid")
        }
        if (!archive.isFile || archive.length() <= 0L) return Result(false, error = "Component archive is missing or empty")
        if (archive.length() > limits.maxArchiveBytes) return Result(false, error = "Component archive exceeds size limit")
        if (!stagingParent.isDirectory || !stagingParent.canWrite()) {
            return Result(false, error = "Staging parent is not a writable directory")
        }

        val expectedHash = component.sha256.lowercase()
        val actualHash = try { sha256(archive, limits.maxArchiveBytes) }
        catch (e: Exception) { return Result(false, error = "Unable to hash component archive: ${e.message ?: "I/O error"}") }
        if (actualHash != expectedHash) return Result(false, error = "Component archive SHA-256 mismatch")

        val inspectionLimits = NgrPackArchiveInspector.Limits(
            maxArchiveBytes = limits.maxArchiveBytes,
            maxEntries = limits.maxEntries,
            maxPathChars = limits.maxPathChars,
            maxManifestBytes = 1024L * 1024,
            maxFileBytes = limits.maxFileBytes,
            maxTotalBytes = limits.maxTotalBytes,
            maxCompressionRatio = limits.maxCompressionRatio
        )
        val inspection = NgrPackArchiveInspector.inspect(archive, inspectionLimits, requireManifest = false)
        if (!inspection.valid) return Result(false, error = inspection.errors.joinToString("; "))

        val root = try {
            File.createTempFile("ngr-${component.id}-", ".stage", stagingParent).also {
                if (!it.delete() || !it.mkdir()) throw IllegalStateException("Unable to create staging directory")
            }
        } catch (e: Exception) {
            return Result(false, error = "Unable to create component staging directory: ${e.message ?: "I/O error"}")
        }
        val rootCanonical = root.canonicalFile
        var files = 0
        var totalBytes = 0L

        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val relative = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
                    val target = File(root, relative)
                    val canonical = target.canonicalFile
                    val rootPrefix = rootCanonical.path + File.separator
                    if (!canonical.path.startsWith(rootPrefix)) {
                        throw SecurityException("Archive entry escapes staging directory: $relative")
                    }

                    if (entry.isDirectory) {
                        if (canonical.exists() && !canonical.isDirectory) {
                            throw SecurityException("Directory collides with file: $relative")
                        }
                        if (!canonical.exists() && !canonical.mkdirs()) {
                            throw IllegalStateException("Unable to create directory: $relative")
                        }
                        if (canonical.canonicalFile.path != canonical.path) {
                            throw SecurityException("Directory path resolves through a symbolic link: $relative")
                        }
                        continue
                    }

                    if (entry.size < 0L || entry.size > limits.maxFileBytes) {
                        throw SecurityException("Invalid or oversized component file: $relative")
                    }
                    val parent = canonical.parentFile ?: throw SecurityException("Missing parent directory: $relative")
                    if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Unable to create parent for: $relative")
                    if (!parent.isDirectory || parent.canonicalFile.path != parent.path) {
                        throw SecurityException("Parent path is not a safe directory: $relative")
                    }
                    if (canonical.exists()) throw SecurityException("Duplicate extraction target: $relative")
                    if (!canonical.createNewFile()) throw IllegalStateException("Unable to create extracted file: $relative")

                    var fileBytes = 0L
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(canonical, false).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                if (fileBytes > limits.maxFileBytes || fileBytes > entry.size) {
                                    throw SecurityException("Extracted file exceeds declared or configured size: $relative")
                                }
                                if (totalBytes > limits.maxTotalBytes) {
                                    throw SecurityException("Component exceeds total extracted size limit")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    if (fileBytes != entry.size) throw SecurityException("Extracted size does not match ZIP metadata: $relative")
                    files++
                }
            }
            return Result(true, root, extractedFiles = files, extractedBytes = totalBytes)
        } catch (e: Exception) {
            deleteTree(root)
            return Result(false, error = "Component extraction failed: ${e.message ?: "I/O error"}")
        }
    }

    private fun isSafeRelativePath(value: String): Boolean {
        if (value.isBlank() || value.length > 512 || value.startsWith("/") ||
            value.startsWith("\\") || value.contains('\\') || value.contains(':') ||
            value.contains(0.toChar())) return false
        return value.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }
    private fun sha256(file: File, maxBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw SecurityException("Archive exceeds hashing limit")
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteTree)
        file.delete()
    }
}
