package com.ngr.app

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Read-only NGRPACK ZIP inspection. No payload is extracted or executed here.
 * Unix file types are read from central-directory external attributes because ZipEntry
 * does not expose symlink metadata on Android/Java.
 */
object NgrPackArchiveInspector {
    data class Limits(
        val maxArchiveBytes: Long = 1024L * 1024 * 1024,
        val maxEntries: Int = 4096,
        val maxPathChars: Int = 1024,
        val maxManifestBytes: Long = 1024L * 1024,
        val maxFileBytes: Long = 512L * 1024 * 1024,
        val maxTotalBytes: Long = 2L * 1024 * 1024 * 1024,
        val maxCompressionRatio: Long = 200
    )

    data class EntryInfo(
        val path: String,
        val directory: Boolean,
        val compressedBytes: Long,
        val uncompressedBytes: Long
    )

    data class Result(
        val valid: Boolean,
        val errors: List<String>,
        val entries: List<EntryInfo> = emptyList(),
        val totalUncompressedBytes: Long = 0,
        val manifestText: String? = null
    )

    private data class CentralEntry(
        val name: String,
        val flags: Int,
        val method: Int,
        val compressed: Long,
        val uncompressed: Long,
        val unixMode: Int
    )

    fun inspect(archive: File, limits: Limits = Limits()): Result {
        val errors = mutableListOf<String>()
        if (!archive.isFile || archive.length() <= 0L) return Result(false, listOf("NGRPACK archive is missing or empty"))
        if (archive.length() > limits.maxArchiveBytes) return Result(false, listOf("NGRPACK archive exceeds size limit"))

        val central = try { readCentralDirectory(archive, limits.maxEntries) }
        catch (e: Exception) { return Result(false, listOf("Invalid ZIP central directory: ${e.message ?: "read error"}")) }

        if (central.size > limits.maxEntries) return Result(false, listOf("Archive exceeds entry count limit"))

        val infos = mutableListOf<EntryInfo>()
        val seen = mutableSetOf<String>()
        var total = 0L
        var manifestText: String? = null

        try {
            ZipFile(archive).use { zip ->
                val zipEntries = zip.entries()
                var index = 0
                while (zipEntries.hasMoreElements()) {
                    if (index >= central.size) {
                        errors += "ZIP central directory entry count mismatch"
                        break
                    }
                    val entry = zipEntries.nextElement()
                    val metadata = central[index++]
                    if (entry.name != metadata.name) {
                        errors += "ZIP entry name mismatch in central directory"
                        continue
                    }
                    if (metadata.flags and 0x0001 != 0) errors += "Encrypted ZIP entries are not supported: ${entry.name}"
                    if (metadata.method != ZipEntry.STORED && metadata.method != ZipEntry.DEFLATED)
                        errors += "Unsupported ZIP compression method: ${entry.name}"
                    if (entry.method != metadata.method || entry.size != metadata.uncompressed || entry.compressedSize != metadata.compressed)
                        errors += "ZIP entry metadata mismatch: ${entry.name}"
                    if (metadata.unixMode != 0) {
                        val type = metadata.unixMode and 0xF000
                        if (type != 0x8000 && type != 0x4000)
                            errors += "Non-regular or symbolic-link ZIP entry is forbidden: ${entry.name}"
                        if (type == 0x4000 && !entry.isDirectory)
                            errors += "Directory metadata mismatch: ${entry.name}"
                        if (type == 0x8000 && entry.isDirectory)
                            errors += "File metadata mismatch: ${entry.name}"
                    }

                    val rawName = entry.name
                    val isDirectory = entry.isDirectory
                    val pathCandidate = if (isDirectory) rawName.removeSuffix("/") else rawName
                    if (!isSafePath(pathCandidate, limits.maxPathChars)) {
                        errors += "Unsafe ZIP entry path: $rawName"
                        continue
                    }
                    val normalized = pathCandidate.replace('\\', '/')
                    val collisionKey = normalized.lowercase(Locale.ROOT)
                    if (!seen.add(collisionKey)) errors += "Duplicate or case-colliding ZIP path: $normalized"

                    val size = metadata.uncompressed
                    val compressed = metadata.compressed
                    if (size < 0 || compressed < 0) errors += "Invalid ZIP entry size: $normalized"
                    if (!isDirectory) {
                        if (size > limits.maxFileBytes) errors += "ZIP entry exceeds per-file size limit: $normalized"
                        if (total > limits.maxTotalBytes - size) errors += "Archive exceeds total uncompressed size limit"
                        else total += size
                        if (size > 0 && compressed == 0L) errors += "Invalid zero-size compressed payload: $normalized"
                        else if (compressed > 0 && size > compressed * limits.maxCompressionRatio)
                            errors += "ZIP compression ratio exceeds limit: $normalized"
                    }
                    infos += EntryInfo(normalized, isDirectory, compressed, size)

                    if (!isDirectory && normalized == "ngr-manifest.json") {
                        if (size > limits.maxManifestBytes) errors += "NGR manifest exceeds size limit"
                        else {
                            val bytes = zip.getInputStream(entry).use { input ->
                                val out = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var readTotal = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    readTotal += count
                                    if (readTotal > limits.maxManifestBytes) throw IllegalArgumentException("manifest size limit exceeded")
                                    out.write(buffer, 0, count)
                                }
                                out.toByteArray()
                            }
                            manifestText = bytes.toString(StandardCharsets.UTF_8)
                            try {
                                val manifest = JSONObject(manifestText!!)
                                if (manifest.opt("formatVersion") != 1) errors += "Unsupported NGR manifest formatVersion"
                                if (manifest.opt("packageId") !is String) errors += "NGR manifest packageId is missing or invalid"
                            } catch (_: JSONException) {
                                errors += "ngr-manifest.json is not valid JSON"
                            }
                        }
                    }
                }
                if (index != central.size) errors += "ZIP central directory entry count mismatch"
            }
        } catch (e: Exception) {
            errors += "Unable to inspect ZIP entry: ${e.message ?: "read error"}"
        }

        if (infos.none { !it.directory && it.path == "ngr-manifest.json" })
            errors += "Archive must contain root ngr-manifest.json"
        return Result(errors.isEmpty(), errors, infos, total, manifestText)
    }

    private fun isSafePath(path: String, maxLength: Int): Boolean {
        if (path.isBlank() || path.length > maxLength || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\u0000') || path.contains(':')) return false
        val normalized = path.replace('\\', '/')
        return normalized.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun readCentralDirectory(file: File, maxEntries: Int): List<CentralEntry> {
        RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            val tailLength = minOf(fileLength, 65557L).toInt()
            val tail = ByteArray(tailLength)
            raf.seek(fileLength - tailLength)
            raf.readFully(tail)
            var eocd = -1
            for (i in tail.size - 22 downTo 0) {
                if (u32(tail, i) == 0x06054b50L) {
                    val commentLength = u16(tail, i + 20)
                    if (i + 22 + commentLength == tail.size) { eocd = i; break }
                }
            }
            if (eocd < 0) throw IllegalArgumentException("end-of-central-directory record not found")
            val disk = u16(tail, eocd + 4)
            val centralDisk = u16(tail, eocd + 6)
            val diskEntries = u16(tail, eocd + 8)
            val totalEntries = u16(tail, eocd + 10)
            val centralSize = u32(tail, eocd + 12)
            val centralOffset = u32(tail, eocd + 16)
            val eocdAbsolute = fileLength - tailLength + eocd
            if (disk != 0 || centralDisk != 0 || diskEntries != totalEntries)
                throw IllegalArgumentException("multi-disk ZIP archives are not supported")
            if (totalEntries == 0xFFFF || centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL)
                throw IllegalArgumentException("ZIP64 archives are not supported yet")
            if (totalEntries > maxEntries) throw IllegalArgumentException("entry count limit exceeded")
            if (centralOffset + centralSize > eocdAbsolute)
                throw IllegalArgumentException("central directory is outside archive bounds")

            raf.seek(centralOffset)
            val result = ArrayList<CentralEntry>(totalEntries)
            repeat(totalEntries) {
                val header = ByteArray(46)
                raf.readFully(header)
                if (u32(header, 0) != 0x02014b50L) throw IllegalArgumentException("invalid central directory header")
                val madeBy = u16(header, 4)
                val flags = u16(header, 8)
                val method = u16(header, 10)
                val compressed = u32(header, 20)
                val uncompressed = u32(header, 24)
                val nameLength = u16(header, 28)
                val extraLength = u16(header, 30)
                val commentLength = u16(header, 32)
                val startDisk = u16(header, 34)
                val externalAttributes = u32(header, 38)
                val localOffset = u32(header, 42)
                if (compressed == 0xFFFFFFFFL || uncompressed == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL || startDisk != 0)
                    throw IllegalArgumentException("ZIP64 or split entry is not supported")
                val nameBytes = ByteArray(nameLength)
                raf.readFully(nameBytes)
                raf.seek(raf.filePointer + extraLength + commentLength)
                val charset = if (flags and 0x0800 != 0) StandardCharsets.UTF_8 else Charset.forName("CP437")
                val name = String(nameBytes, charset)
                val host = madeBy ushr 8
                val unixMode = if (host == 3) (externalAttributes ushr 16).toInt() else 0
                result += CentralEntry(name, flags, method, compressed, uncompressed, unixMode)
            }
            if (raf.filePointer != centralOffset + centralSize)
                throw IllegalArgumentException("central directory size mismatch")
            return result
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (u16(bytes, offset).toLong() and 0xFFFF) or ((u16(bytes, offset + 2).toLong() and 0xFFFF) shl 16)
}
