package com.ngr.app

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.zip.ZipFile

/**
 * App-private NGRPACK package storage.
 *
 * This manager installs data only. It never loads native libraries or executes package code.
 * Archive bytes are first copied into private storage, inspected, extracted into a staging
 * directory, and fully manifest/hash-validated before the destination directory is replaced.
 */
class NgrPackPackageManager(
    private val packagesDirectory: File,
    private val limits: NgrPackArchiveInspector.Limits = NgrPackArchiveInspector.Limits()
) {
    data class InstalledPackage(
        val packageId: String,
        val displayName: String,
        val version: String,
        val backendId: String,
        val backendVersion: String,
        val entryPoint: String,
        val sourcePlatform: String?,
        val sourceArchitecture: String?,
        val directory: File
    )

    data class OperationResult(
        val success: Boolean,
        val message: String,
        val packageInfo: InstalledPackage? = null,
        val errors: List<String> = emptyList()
    )

    data class InventoryResult(
        val packages: List<InstalledPackage>,
        val errors: List<String>,
        val invalidPackageIds: List<String> = emptyList()
    )

    companion object {
        private val packageIdPattern = Regex("^[a-z0-9][a-z0-9._-]{2,127}$")
        private const val MANIFEST_NAME = "ngr-manifest.json"
    }

    @Synchronized
    fun install(archive: File): OperationResult {
        NgrLogger.info("package.manager", "Install started; archive=${archive.name}; bytes=${archive.length()}")
        val recoveryErrors = mutableListOf<String>()
        val root = try { prepareRoot(recoveryErrors) }
        catch (e: Exception) { return OperationResult(false, "Package storage is unavailable", errors = listOf(e.message ?: "storage error")) }
        if (recoveryErrors.isNotEmpty()) return OperationResult(false, "Package storage recovery requires attention", errors = recoveryErrors)

        val token = UUID.randomUUID().toString()
        val incoming = root.resolve(".incoming-$token")
        var staging: Path? = null
        try {
            copyArchiveToPrivateStorage(archive, incoming)
            NgrLogger.info("package.manager", "Archive copied to private incoming path; bytes=${Files.size(incoming)}")
            val inspected = NgrPackArchiveInspector.inspect(incoming.toFile(), limits)
            NgrLogger.info("package.manager", "Archive inspection valid=${inspected.valid}; entries=${inspected.entries.size}; uncompressed=${inspected.totalUncompressedBytes}; errors=${inspected.errors.joinToString(" | ")}")
            if (!inspected.valid) return OperationResult(false, "NGRPACK inspection failed", errors = inspected.errors)
            val manifestText = inspected.manifestText
                ?: return OperationResult(false, "NGRPACK manifest is missing", errors = listOf("Root ngr-manifest.json is required"))
            val manifest = try { JSONObject(manifestText) }
                catch (_: JSONException) { return OperationResult(false, "NGRPACK manifest is invalid", errors = listOf("Manifest is not valid JSON")) }
            val packageId = manifest.opt("packageId") as? String
                ?: return OperationResult(false, "NGRPACK packageId is invalid", errors = listOf("packageId must be a string"))
            if (!packageIdPattern.matches(packageId))
                return OperationResult(false, "NGRPACK packageId is invalid", errors = listOf("packageId is not a safe package identifier"))

            NgrLogger.info("package.manager", "Manifest accepted; packageId=$packageId")
            staging = Files.createTempDirectory(root, ".staging-$token-")
            NgrLogger.info("package.manager", "Extraction started into private staging directory")
            extractBounded(incoming, staging)
            NgrLogger.info("package.manager", "Extraction complete; validating manifest and payload hashes")
            val extractedManifest = staging.resolve(MANIFEST_NAME)
            if (!Files.isRegularFile(extractedManifest, LinkOption.NOFOLLOW_LINKS))
                return OperationResult(false, "Extracted manifest is missing", errors = listOf("Root ngr-manifest.json was not extracted"))
            val extractedText = String(Files.readAllBytes(extractedManifest), Charsets.UTF_8)
            if (extractedText != manifestText)
                return OperationResult(false, "Archive changed during installation", errors = listOf("Extracted manifest differs from inspected manifest"))

            val validation = NgrManifestValidator.validate(extractedText, staging.toFile(), verifyHashes = true)
            if (!validation.valid)
                return OperationResult(false, "Package manifest or payload validation failed", errors = validation.errors)

            val packageInfo = readPackageInfo(staging.toFile(), verifyHashes = true)
                ?: return OperationResult(false, "Package metadata is invalid", errors = listOf("Unable to read validated package metadata"))
            if (packageInfo.packageId != packageId)
                return OperationResult(false, "Package identity mismatch", errors = listOf("Manifest packageId changed during validation"))

            val destination = root.resolve(packageId)
            val backup = root.resolve(".backup-$packageId-$token")
            var oldMoved = false
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(destination) || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("Existing package destination is not a real directory")
                    val existing = readPackageInfo(destination.toFile(), verifyHashes = true)
                        ?: throw IOException("Existing package is invalid; refusing to overwrite it")
                    if (existing.packageId != packageId) throw IOException("Existing package identity does not match destination")
                    atomicMove(destination, backup)
                    oldMoved = true
                }
                atomicMove(staging, destination)
                staging = null
                if (oldMoved) {
                    try { deleteTree(backup) } catch (_: Exception) { /* recovered on next manager operation */ }
                }
            } catch (e: Exception) {
                if (oldMoved && !Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    try { atomicMove(backup, destination) } catch (restore: Exception) {
                        throw IOException("Install failed and previous package rollback failed: ${restore.message}", e)
                    }
                }
                throw e
            }

            val installed = readPackageInfo(destination.toFile(), verifyHashes = false)
                ?: return OperationResult(false, "Installed package could not be re-opened", errors = listOf("Post-install metadata validation failed"))
            NgrLogger.info("package.manager", "Install committed; packageId=${installed.packageId}; version=${installed.version}")
            return OperationResult(true, "Package installed", installed)
        } catch (e: Exception) {
            NgrLogger.error("package.manager", "Package installation failed", e)
            return OperationResult(false, "Package installation failed", errors = listOf(e.message ?: e.javaClass.simpleName))
        } finally {
            try { deleteTree(incoming) } catch (_: Exception) { }
            staging?.let { try { deleteTree(it) } catch (_: Exception) { } }
        }
    }

    /**
     * Creates a private staging directory for a SAF folder import. The payload is copied
     * directly here, avoiding cache copies and an intermediate NGRPACK archive.
     */
    @Synchronized
    fun createFolderImportStaging(): File {
        val errors = mutableListOf<String>()
        val root = prepareRoot(errors)
        if (errors.isNotEmpty()) throw IOException(errors.joinToString("; "))
        val parent = root.resolve(".folder-staging")
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
                throw IOException("Folder import staging root is unsafe")
        } else Files.createDirectory(parent)
        val staging = Files.createDirectory(parent.resolve(UUID.randomUUID().toString()))
        NgrLogger.info("package.manager", "Created direct folder staging directory")
        return staging.toFile()
    }

    /**
     * Validates and atomically promotes a prepared folder-import directory.
     * The caller must have created it through createFolderImportStaging().
     */
    @Synchronized
    fun installPreparedFolder(stagingDirectory: File): OperationResult {
        val errors = mutableListOf<String>()
        val root = try { prepareRoot(errors) }
        catch (e: Exception) {
            return OperationResult(false, "Package storage is unavailable", errors = listOf(e.message ?: "storage error"))
        }
        if (errors.isNotEmpty()) return OperationResult(false, "Package storage recovery requires attention", errors = errors)
        val staging = stagingDirectory.toPath().toAbsolutePath().normalize()
        val stagingParent = root.resolve(".folder-staging").toAbsolutePath().normalize()
        if (staging.parent != stagingParent || Files.isSymbolicLink(stagingParent) ||
            !Files.isDirectory(stagingParent, LinkOption.NOFOLLOW_LINKS) ||
            !staging.fileName.toString().matches(Regex("^[0-9a-fA-F-]{36}$")) ||
            Files.isSymbolicLink(staging) || !Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS))
            return OperationResult(false, "Folder import staging path is invalid", errors = listOf("Staging must be a UUID-named real directory directly inside the private folder-staging directory"))
        if (runCatching { stagingParent.toRealPath() }.getOrNull() != stagingParent ||
            runCatching { staging.toRealPath().parent }.getOrNull() != stagingParent)
            return OperationResult(false, "Folder import staging path is invalid", errors = listOf("Staging path resolves outside its private parent"))

        try {
            val info = readPackageInfo(staging.toFile(), verifyHashes = true)
                ?: throw IOException("Prepared game folder failed manifest and SHA-256 validation")
            if (!packageIdPattern.matches(info.packageId)) throw IOException("Prepared package ID is unsafe")
            val destination = root.resolve(info.packageId)
            val backup = root.resolve(".backup-${info.packageId}-${UUID.randomUUID()}")
            var oldMoved = false
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(destination) || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("Existing package destination is not a real directory")
                    val existing = readPackageInfo(destination.toFile(), verifyHashes = true)
                        ?: throw IOException("Existing package is invalid; refusing to overwrite it")
                    if (existing.packageId != info.packageId) throw IOException("Existing package identity mismatch")
                    atomicMove(destination, backup)
                    oldMoved = true
                }
                atomicMove(staging, destination)
                if (oldMoved) try { deleteTree(backup) } catch (_: Exception) { }
            } catch (e: Exception) {
                if (oldMoved && !Files.exists(destination, LinkOption.NOFOLLOW_LINKS) &&
                    Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    try { atomicMove(backup, destination) }
                    catch (restore: Exception) { throw IOException("Folder import failed and rollback failed: ${restore.message}", e) }
                }
                throw e
            }
            val installed = readPackageInfo(destination.toFile(), verifyHashes = false)
                ?: throw IOException("Installed package could not be reopened after promotion")
            NgrLogger.info("package.manager", "Direct folder import committed; packageId=${installed.packageId}")
            return OperationResult(true, "Game folder installed", installed)
        } catch (e: Exception) {
            NgrLogger.error("package.manager", "Direct folder import failed", e)
            return OperationResult(false, "Game folder installation failed", errors = listOf(e.message ?: e.javaClass.simpleName))
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                try { deleteTree(staging) } catch (_: Exception) { }
            }
        }
    }

    @Synchronized
    fun listInstalled(): InventoryResult {
        NgrLogger.info("package.manager", "Inventory scan started")
        val errors = mutableListOf<String>()
        val root = try { prepareRoot(errors) }
        catch (e: Exception) { return InventoryResult(emptyList(), listOf(e.message ?: "storage error")) }
        if (errors.isNotEmpty()) return InventoryResult(emptyList(), errors)
        val packages = mutableListOf<InstalledPackage>()
        val invalidPackageIds = mutableListOf<String>()
        Files.newDirectoryStream(root).use { stream ->
            for (path in stream) {
                val name = path.fileName.toString()
                if (name.startsWith(".")) continue
                if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue
                val info = readPackageInfo(path.toFile(), verifyHashes = false)
                if (info == null) {
                    errors += "Invalid installed package directory: $name"
                    if (packageIdPattern.matches(name)) invalidPackageIds += name
                } else if (info.packageId != name) {
                    errors += "Installed package directory identity mismatch: $name"
                    if (packageIdPattern.matches(name)) invalidPackageIds += name
                } else packages += info
            }
        }
        return InventoryResult(packages.sortedBy { it.packageId }, errors, invalidPackageIds.sorted())
    }

    @Synchronized
    fun remove(packageId: String): OperationResult {
        NgrLogger.info("package.manager", "Removal started; packageId=$packageId")
        if (!packageIdPattern.matches(packageId)) return OperationResult(false, "Invalid package ID", errors = listOf("Unsafe package identifier"))
        val errors = mutableListOf<String>()
        val root = try { prepareRoot(errors) }
        catch (e: Exception) { return OperationResult(false, "Package storage is unavailable", errors = listOf(e.message ?: "storage error")) }
        if (errors.isNotEmpty()) return OperationResult(false, "Package storage recovery requires attention", errors = errors)
        val target = root.resolve(packageId)
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return OperationResult(false, "Package not found")
        if (Files.isSymbolicLink(target) || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
            return OperationResult(false, "Refusing to remove a non-directory package path")
        // Removal is intentionally allowed for corrupt package contents. The target is
        // constrained to this exact safe child path and deleteTree never follows symlinks.
        val info = readPackageInfo(target.toFile(), verifyHashes = false)
        val tombstone = root.resolve(".trash-${UUID.randomUUID()}")
        return try {
            atomicMove(target, tombstone)
            try { deleteTree(tombstone) } catch (e: Exception) {
                return OperationResult(true, "Package removed; cleanup will be retried", info)
            }
            NgrLogger.info("package.manager", "Removal committed; packageId=$packageId")
            OperationResult(true, "Package removed", info)
        } catch (e: Exception) {
            NgrLogger.error("package.manager", "Package removal failed; packageId=$packageId", e)
            OperationResult(false, "Package removal failed", errors = listOf(e.message ?: "remove error"))
        }
    }

    private fun prepareRoot(errors: MutableList<String>): Path {
        val root = packagesDirectory.toPath().toAbsolutePath().normalize()
        if (Files.isSymbolicLink(root)) throw IOException("Package storage root must not be a symbolic link")
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root)
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw IOException("Package storage root is not a directory")
        recoverTransactions(root, errors)
        return root
    }

    private fun recoverTransactions(root: Path, errors: MutableList<String>) {
        Files.newDirectoryStream(root).use { stream ->
            for (path in stream) {
                val name = path.fileName.toString()
                if (name.startsWith(".staging-") || name.startsWith(".incoming-") || name.startsWith(".trash-")) {
                    try { deleteTree(path) } catch (e: Exception) { errors += "Unable to clean interrupted transaction $name: ${e.message}" }
                } else if (name.startsWith(".backup-")) {
                    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        errors += "Invalid package backup entry requires manual review: $name"
                        continue
                    }
                    val info = readPackageInfo(path.toFile(), verifyHashes = false)
                    if (info == null || !packageIdPattern.matches(info.packageId)) {
                        errors += "Unrecognized package backup requires manual review: $name"
                        continue
                    }
                    val destination = root.resolve(info.packageId)
                    try {
                        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) atomicMove(path, destination)
                        else deleteTree(path)
                    } catch (e: Exception) { errors += "Unable to recover package backup $name: ${e.message}" }
                }
            }
        }
    }

    private fun copyArchiveToPrivateStorage(source: File, destination: Path) {
        val sourcePath = source.toPath()
        if (Files.isSymbolicLink(sourcePath) || !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS))
            throw IOException("NGRPACK source must be a regular non-symlink file")
        if (Files.size(sourcePath) > limits.maxArchiveBytes) throw IOException("NGRPACK archive exceeds size limit")
        var copied = 0L
        Files.newInputStream(sourcePath, LinkOption.NOFOLLOW_LINKS).use { input ->
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > limits.maxArchiveBytes) throw IOException("NGRPACK archive exceeds size limit while copying")
                    output.write(buffer, 0, count)
                }
            }
        }
        if (copied == 0L) throw IOException("NGRPACK archive is empty")
    }

    private fun extractBounded(archive: Path, staging: Path) {
        val root = staging.toAbsolutePath().normalize()
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw IOException("Staging directory is not a real directory")
        var totalBytes = 0L
        var entries = 0
        ZipFile(archive.toFile()).use { zip ->
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                entries++
                if (entries > limits.maxEntries) throw IOException("Archive entry count limit exceeded during extraction")
                val rawName = entry.name
                val directory = entry.isDirectory
                val relative = if (directory) rawName.removeSuffix("/") else rawName
                if (!isSafeRelativePath(relative)) throw IOException("Unsafe ZIP path during extraction: $rawName")
                val normalized = relative.replace('\\', '/')
                val target = root.resolve(normalized).normalize()
                if (!target.startsWith(root) || target == root) throw IOException("ZIP path escapes staging directory")
                if (directory) {
                    ensureDirectory(root, target)
                    continue
                }
                if (entry.size < 0 || entry.size > limits.maxFileBytes) throw IOException("ZIP file size limit exceeded: $normalized")
                val parent = target.parent ?: throw IOException("ZIP file has no parent directory")
                ensureDirectory(root, parent)
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target))
                    throw IOException("Duplicate or pre-existing extraction target: $normalized")
                var fileBytes = 0L
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            fileBytes += count
                            totalBytes += count
                            if (fileBytes > limits.maxFileBytes) throw IOException("ZIP file exceeded streamed size limit: $normalized")
                            if (totalBytes > limits.maxTotalBytes) throw IOException("Package exceeded streamed total size limit")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (fileBytes != entry.size) throw IOException("ZIP entry size mismatch after extraction: $normalized")
            }
        }
    }

    private fun ensureDirectory(root: Path, directory: Path) {
        if (!directory.normalize().startsWith(root)) throw IOException("Directory path escapes staging root")
        var current = root
        for (segment in root.relativize(directory)) {
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
                    throw IOException("Extraction parent is not a real directory")
            } else {
                Files.createDirectory(current)
            }
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > limits.maxPathChars || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\u0000') || path.contains(':')) return false
        return path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun readPackageInfo(directory: File, verifyHashes: Boolean): InstalledPackage? {
        val manifestFile = File(directory, MANIFEST_NAME)
        if (Files.isSymbolicLink(directory.toPath()) ||
            !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS) ||
            !Files.isRegularFile(manifestFile.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.size(manifestFile.toPath()) > NgrManifestValidator.MAX_MANIFEST_BYTES) return null
        val text = try { manifestFile.readText(Charsets.UTF_8) } catch (_: Exception) { return null }
        val validation = NgrManifestValidator.validate(text, directory, verifyHashes)
        if (!validation.valid) return null
        return try {
            val manifest = JSONObject(text)
            val backend = manifest.getJSONObject("backend")
            InstalledPackage(
                packageId = manifest.getString("packageId"),
                displayName = manifest.getString("displayName"),
                version = manifest.optString("version", ""),
                backendId = backend.getString("id"),
                backendVersion = backend.getString("version"),
                entryPoint = manifest.getString("entryPoint"),
                sourcePlatform = backend.optString("sourcePlatform").takeIf { it.isNotBlank() },
                sourceArchitecture = backend.optString("sourceArchitecture").takeIf { it.isNotBlank() },
                directory = directory
            )
        } catch (_: Exception) { null }
    }

    private fun atomicMove(source: Path, destination: Path) {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            throw IOException("Atomic directory rename is not supported by package storage", e)
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
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
}
