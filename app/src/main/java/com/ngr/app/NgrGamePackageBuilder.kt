package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Converts ordinary game folders/ZIPs into the internal, hash-verified NGRPACK format.
 * It only copies data. It never executes or loads any imported file.
 */
class NgrGamePackageBuilder(
    private val limits: NgrPackArchiveInspector.Limits = NgrPackArchiveInspector.Limits()
) {
    data class Result(
        val success: Boolean,
        val message: String,
        val archive: File? = null,
        val displayName: String? = null,
        val entryPoint: String? = null,
        val platform: String? = null,
        val architecture: String? = null,
        val errors: List<String> = emptyList()
    )

    fun buildFromFolder(source: File, outputArchive: File, displayName: String? = null): Result =
        withWorkspace(outputArchive) { workspace ->
            val sourcePath = source.toPath().toAbsolutePath().normalize()
            if (Files.isSymbolicLink(sourcePath) || !Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS))
                throw IOException("Selected game source must be a real directory, not a symbolic link")
            val payload = workspace.resolve("payload")
            copyTreeBounded(sourcePath, payload)
            createPackage(payload, outputArchive, displayName ?: source.name)
        }

    fun buildFromZip(source: File, outputArchive: File, displayName: String? = null): Result =
        withWorkspace(outputArchive) { workspace ->
            val inspection = NgrPackArchiveInspector.inspect(source, limits, requireManifest = false)
            if (!inspection.valid) throw IOException(inspection.errors.joinToString("; "))
            if (inspection.manifestText != null)
                throw IOException("This ZIP already contains a root ngr-manifest.json. Import it using the NGRPACK option.")
            val extracted = workspace.resolve("extracted")
            Files.createDirectory(extracted)
            extractZipBounded(source, extracted)
            val payload = unwrapSingleRoot(extracted)
            createPackage(payload, outputArchive, displayName ?: source.nameWithoutExtension)
        }

    private inline fun withWorkspace(output: File, action: (Path) -> Result): Result {
        val parent = output.absoluteFile.parentFile ?: return Result(false, "Invalid output location", errors = listOf("Output archive has no parent directory"))
        if (!parent.exists() && !parent.mkdirs()) return Result(false, "Unable to prepare import workspace")
        val workspace = Files.createTempDirectory(parent.toPath(), "ngr-game-import-")
        return try {
            action(workspace)
        } catch (e: Exception) {
            NgrLogger.error("package.builder", "Game package preparation failed", e)
            Result(false, "Game import preparation failed", errors = listOf(e.message ?: e.javaClass.simpleName))
        } finally {
            deleteTree(workspace)
        }
    }

    private fun copyTreeBounded(source: Path, destination: Path) {
        Files.createDirectory(destination)
        var count = 0
        var total = 0L
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink || Files.isSymbolicLink(dir)) throw IOException("Symbolic links are not allowed in game folders")
                val relative = source.relativize(dir)
                if (relative.toString().isNotEmpty()) {
                    val target = destination.resolve(relative.toString()).normalize()
                    if (!target.startsWith(destination)) throw IOException("Game folder path escapes import root")
                    Files.createDirectory(target)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink || Files.isSymbolicLink(file) || !attrs.isRegularFile)
                    throw IOException("Only regular files are allowed: ${file.fileName}")
                val relative = source.relativize(file).toString().replace(File.separatorChar, '/')
                validateRelativePath(relative)
                if (relative == "ngr-manifest.json") throw IOException("Source folder already contains ngr-manifest.json; use NGRPACK import for prepared packages")
                count++
                if (count > limits.maxEntries - 1) throw IOException("Game contains too many files")
                val size = attrs.size()
                if (size > limits.maxFileBytes) throw IOException("Game file exceeds size limit: $relative")
                if (total > limits.maxTotalBytes - size) throw IOException("Game exceeds total uncompressed size limit")
                total += size
                val target = destination.resolve(relative).normalize()
                if (!target.startsWith(destination)) throw IOException("Game file escapes import root")
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun extractZipBounded(archive: File, destination: Path) {
        var total = 0L
        var count = 0
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                if (count > limits.maxEntries) throw IOException("ZIP entry count limit exceeded")
                val raw = entry.name
                val relative = (if (entry.isDirectory) raw.removeSuffix("/") else raw).replace('\\\\', '/')
                validateRelativePath(relative)
                val target = destination.resolve(relative).normalize()
                if (!target.startsWith(destination) || target == destination) throw IOException("ZIP path escapes import root: $raw")
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    continue
                }
                if (entry.size < 0 || entry.size > limits.maxFileBytes) throw IOException("ZIP file size limit exceeded: $relative")
                Files.createDirectories(target.parent)
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw IOException("Duplicate ZIP output path: $relative")
                var fileBytes = 0L
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            total += read
                            if (fileBytes > limits.maxFileBytes || total > limits.maxTotalBytes)
                                throw IOException("ZIP expanded size limit exceeded: $relative")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (fileBytes != entry.size) throw IOException("ZIP entry size mismatch: $relative")
            }
        }
    }

    private fun unwrapSingleRoot(extracted: Path): Path {
        val children = Files.newDirectoryStream(extracted).use { it.toList() }
        if (children.size == 1 && Files.isDirectory(children.single(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(children.single())) return children.single()
        return extracted
    }

    private fun createPackage(payload: Path, outputArchive: File, requestedName: String): Result {
        val files = mutableListOf<Path>()
        var total = 0L
        Files.walkFileTree(payload, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink || Files.isSymbolicLink(file) || !attrs.isRegularFile)
                    throw IOException("Game contains a non-regular file")
                val relative = payload.relativize(file).toString().replace(File.separatorChar, '/')
                validateRelativePath(relative)
                if (relative == "ngr-manifest.json") throw IOException("Unexpected nested root manifest")
                if (attrs.size() > limits.maxFileBytes) throw IOException("Game file exceeds size limit: $relative")
                if (total > limits.maxTotalBytes - attrs.size()) throw IOException("Game exceeds total size limit")
                total += attrs.size()
                files += file
                if (files.size > minOf(limits.maxEntries - 1, NgrManifestValidator.MAX_FILES))
                    throw IOException("Game contains too many files for an NGR package")
                return FileVisitResult.CONTINUE
            }
        })
        if (files.isEmpty()) throw IOException("No game files were found in the selected source")
        val candidates = files.map { file ->
            val relative = payload.relativize(file).toString().replace(File.separatorChar, '/')
            val kind = executableKind(file)
            Triple(relative, kind.first, kind.second)
        }.filter { it.second != null }
        if (candidates.isEmpty())
            throw IOException("No Windows EXE or Linux ELF executable was found. The folder/archive may contain only data or an installer.")
        val selected = candidates.sortedWith(
            compareBy<Triple<String, String?, String?>> { if (it.second == "windows") 0 else 1 }
                .thenBy { if (it.first.substringAfterLast('/').contains("shipping", true)) 1 else 0 }
                .thenBy { it.first.length }
        ).first()
        val display = requestedName.trim().ifBlank { selected.first.substringAfterLast('/').substringBeforeLast('.') }
            .take(160)
        val safeName = display.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80)
        val packageId = "import.${safeName.ifBlank { "game" }}.${UUIDSuffix.next()}"
        val platform = selected.second!!
        val architecture = selected.third ?: "unknown"

        val fileArray = JSONArray()
        files.sortedBy { payload.relativize(it).toString() }.forEach { file ->
            val relative = payload.relativize(file).toString().replace(File.separatorChar, '/')
            fileArray.put(JSONObject().put("path", relative).put("sha256", sha256(file)))
        }
        val manifest = JSONObject()
            .put("formatVersion", 1)
            .put("packageId", packageId)
            .put("displayName", display)
            .put("version", "imported")
            .put("backend", JSONObject()
                .put("id", "unconfigured")
                .put("version", "0")
                .put("targetAbi", "arm64-v8a")
                .put("sourcePlatform", platform)
                .put("sourceArchitecture", architecture))
            .put("entryPoint", selected.first)
            .put("minimumRuntimeVersion", "0.1.0")
            .put("provenance", "local-import")
            .put("files", fileArray)
        Files.write(payload.resolve("ngr-manifest.json"), manifest.toString(2).toByteArray(Charsets.UTF_8))
        writePackageZip(payload, outputArchive)
        return Result(true, "Game scanned and NGRPACK generated", outputArchive, display, selected.first, platform, architecture)
    }

    private fun writePackageZip(payload: Path, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            Files.walkFileTree(payload, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relative = payload.relativize(file).toString().replace(File.separatorChar, '/')
                    val entry = ZipEntry(relative)
                    zip.putNextEntry(entry)
                    Files.newInputStream(file).use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            })
        }
        if (output.length() > limits.maxArchiveBytes) {
            output.delete()
            throw IOException("Generated NGRPACK exceeds archive size limit")
        }
    }

    private fun executableKind(file: Path): Pair<String?, String?> {
        val name = file.fileName.toString()
        if (name.endsWith(".exe", true)) return "windows" to detectPeArchitecture(file)
        RandomAccessFile(file.toFile(), "r").use { raf ->
            if (raf.length() >= 20) {
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) {
                    raf.seek(18)
                    val machine = (raf.read() and 0xff) or ((raf.read() and 0xff) shl 8)
                    return "linux" to when (machine) {
                        62 -> "x86_64"
                        183 -> "aarch64"
                        3 -> "x86"
                        40 -> "arm"
                        else -> "unknown"
                    }
                }
            }
        }
        return null to null
    }

    private fun detectPeArchitecture(file: Path): String {
        return try {
            RandomAccessFile(file.toFile(), "r").use { raf ->
                if (raf.length() < 64) return "unknown"
                raf.seek(0x3c)
                val offset = readLe32(raf)
                if (offset < 0 || offset + 6 > raf.length()) return "unknown"
                raf.seek(offset.toLong())
                if (raf.read() != 0x50 || raf.read() != 0x45 || raf.read() != 0) return "unknown"
                val machineLow = raf.read()
                val machineHigh = raf.read()
                when {
                    machineLow == 0x64 && machineHigh == 0x86 -> "x86_64"
                    machineLow == 0x4c && machineHigh == 0x01 -> "x86"
                    machineLow == 0x64 && machineHigh == 0xAA -> "arm64"
                    else -> "unknown"
                }
            }
        } catch (_: Exception) { "unknown" }
    }

    private fun readLe32(raf: RandomAccessFile): Int {
        val a = raf.read(); val b = raf.read(); val c = raf.read(); val d = raf.read()
        if (listOf(a, b, c, d).any { it < 0 }) return -1
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }

    private fun validateRelativePath(path: String) {
        if (path.isBlank() || path.length > limits.maxPathChars || path.startsWith("/") ||
            path.startsWith("\\\\") || path.contains(':') || path.contains('\u0000') ||
            path.replace('\\\\', '/').split('/').any { it.isBlank() || it == "." || it == ".." })
            throw IOException("Unsafe game path: $path")
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private object UUIDSuffix {
        fun next(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
