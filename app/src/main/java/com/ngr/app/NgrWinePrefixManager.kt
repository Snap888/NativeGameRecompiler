package com.ngr.app

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * Safe app-private Wine prefix storage. Creating a prefix only prepares an empty
 * directory; READY is reported exclusively when Wine registry/drive markers exist.
 * This class never invokes Wine or executes files from a prefix.
 */
class NgrWinePrefixManager(private val privateRoot: File) {
    enum class State { PREPARED, READY, INVALID }
    data class Prefix(
        val id: String,
        val displayName: String,
        val state: State,
        val directory: File,
        val fileCount: Int,
        val totalBytes: Long,
        val modifiedAt: Long
    )
    data class Result(val success: Boolean, val prefix: Prefix? = null, val error: String? = null)

    fun list(): List<Prefix> {
        val root = prefixesRoot()
        return root.listFiles().orEmpty().filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { inspect(it.name) }.sortedBy { it.displayName.lowercase() }
    }

    fun inspect(id: String): Prefix? {
        if (!safeId(id)) return null
        return try {
            val root = prefixesRoot().canonicalFile
            val directory = File(root, id)
            if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory ||
                directory.canonicalFile.parentFile != root) return null
            val metadata = readMetadata(directory)
            val scan = scanTree(directory.toPath(), includeMetadata = false)
            val ready = hasWineMarkers(directory)
            Prefix(
                id, metadata.optString("displayName", id),
                if (ready) State.READY else if (metadata.optBoolean("prepared", false)) State.PREPARED else State.INVALID,
                directory, scan.first, scan.second, directory.lastModified()
            )
        } catch (_: Exception) { null }
    }

    fun create(id: String, displayName: String): Result {
        if (!safeId(id)) return Result(false, error = "Prefix ID is invalid")
        if (displayName.isBlank() || displayName.length > 80) return Result(false, error = "Prefix name must contain 1..80 characters")
        return try {
            val root = prefixesRoot()
            val target = File(root, id)
            require(!target.exists()) { "Prefix already exists: $id" }
            val stage = Files.createTempDirectory(root.toPath(), ".prefix-stage-").toFile()
            try {
                writeMetadata(stage, id, displayName, prepared = true)
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { if (stage.exists()) deleteTree(stage.toPath()) }
            Result(true, inspect(id))
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to create prefix") }
    }

    fun clone(sourceId: String, targetId: String, displayName: String): Result {
        if (!safeId(sourceId) || !safeId(targetId)) return Result(false, error = "Prefix ID is invalid")
        if (sourceId == targetId) return Result(false, error = "Source and destination prefix IDs must differ")
        if (displayName.isBlank() || displayName.length > 80) return Result(false, error = "Prefix name must contain 1..80 characters")
        return try {
            val source = inspect(sourceId) ?: error("Source prefix does not exist or is unsafe")
            require(source.state == State.READY) { "Only a validated READY prefix can be cloned" }
            val root = prefixesRoot()
            val target = File(root, targetId)
            require(!target.exists()) { "Destination prefix already exists: $targetId" }
            val stage = Files.createTempDirectory(root.toPath(), ".prefix-clone-").toFile()
            try {
                copyTree(source.directory.toPath(), stage.toPath(), excludeMetadata = true)
                writeMetadata(stage, targetId, displayName, prepared = true)
                require(hasWineMarkers(stage)) { "Cloned prefix failed Wine marker validation" }
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { if (stage.exists()) deleteTree(stage.toPath()) }
            Result(true, inspect(targetId))
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to clone prefix") }
    }

    /** Creates a private directory snapshot; it is not a ZIP and is not exportable yet. */
    fun backup(prefixId: String, backupId: String): Result {
        if (!safeId(prefixId) || !safeId(backupId)) return Result(false, error = "Prefix or backup ID is invalid")
        return try {
            val source = inspect(prefixId) ?: error("Prefix does not exist or is unsafe")
            require(source.state == State.READY) { "Only a validated READY prefix can be backed up" }
            val root = backupsRoot()
            val target = File(root, backupId)
            require(!target.exists()) { "Backup already exists: $backupId" }
            val stage = Files.createTempDirectory(root.toPath(), ".backup-stage-").toFile()
            try {
                copyTree(source.directory.toPath(), stage.toPath(), excludeMetadata = true)
                val digest = treeDigest(stage.toPath())
                JSONObject().put("schemaVersion", 1).put("backupId", backupId)
                    .put("sourcePrefixId", prefixId).put("sourceName", source.displayName)
                    .put("createdAt", System.currentTimeMillis()).put("treeSha256", digest)
                    .writeTo(File(stage, BACKUP_MARKER))
                require(hasWineMarkers(stage) && treeDigest(stage.toPath(), BACKUP_MARKER) == digest) {
                    "Backup validation failed"
                }
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { if (stage.exists()) deleteTree(stage.toPath()) }
            Result(true, prefix = Prefix(backupId, source.displayName, State.READY, target, source.fileCount, source.totalBytes, target.lastModified()))
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to create backup") }
    }

    fun restore(backupId: String, targetId: String, displayName: String): Result {
        if (!safeId(backupId) || !safeId(targetId)) return Result(false, error = "Backup or prefix ID is invalid")
        if (displayName.isBlank() || displayName.length > 80) return Result(false, error = "Prefix name must contain 1..80 characters")
        return try {
            val root = backupsRoot().canonicalFile
            val backup = File(root, backupId)
            require(!Files.isSymbolicLink(backup.toPath()) && backup.isDirectory && backup.canonicalFile.parentFile == root) {
                "Backup does not exist or is unsafe"
            }
            val marker = File(backup, BACKUP_MARKER)
            require(!Files.isSymbolicLink(marker.toPath()) && marker.isFile) { "Backup metadata is missing" }
            val meta = JSONObject(marker.readText(Charsets.UTF_8))
            require(meta.optInt("schemaVersion") == 1 && meta.optString("backupId") == backupId) { "Unsupported or mismatched backup metadata" }
            val expected = meta.getString("treeSha256")
            require(hasWineMarkers(backup) && treeDigest(backup.toPath(), BACKUP_MARKER) == expected) { "Backup integrity check failed" }
            val targetRoot = prefixesRoot()
            val target = File(targetRoot, targetId)
            require(!target.exists()) { "Destination prefix already exists: $targetId" }
            val stage = Files.createTempDirectory(targetRoot.toPath(), ".prefix-restore-").toFile()
            try {
                copyTree(backup.toPath(), stage.toPath(), excludeMetadata = true, excludedName = BACKUP_MARKER)
                writeMetadata(stage, targetId, displayName, prepared = true)
                require(hasWineMarkers(stage)) { "Restored prefix failed Wine marker validation" }
                Files.move(stage.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { if (stage.exists()) deleteTree(stage.toPath()) }
            Result(true, inspect(targetId))
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to restore backup") }
    }

    fun reset(id: String): Result {
        if (!safeId(id)) return Result(false, error = "Prefix ID is invalid")
        return try {
            val prefix = inspect(id) ?: error("Prefix does not exist or is unsafe")
            val root = prefixesRoot().canonicalFile
            require(prefix.directory.canonicalFile.parentFile == root) { "Prefix is outside managed storage" }
            prefix.directory.listFiles().orEmpty().forEach { child ->
                require(!Files.isSymbolicLink(child.toPath())) { "Refusing to follow symbolic link during reset" }
                deleteTree(child.toPath())
            }
            writeMetadata(prefix.directory, id, prefix.displayName, prepared = true)
            Result(true, inspect(id))
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to reset prefix") }
    }

    fun remove(id: String): Result {
        if (!safeId(id)) return Result(false, error = "Prefix ID is invalid")
        return try {
            val root = prefixesRoot().canonicalFile
            val target = File(root, id)
            if (!target.exists()) return Result(true)
            require(!Files.isSymbolicLink(target.toPath()) && target.isDirectory && target.canonicalFile.parentFile == root) {
                "Refusing to remove a path outside managed prefix storage"
            }
            scanTree(target.toPath())
            deleteTree(target.toPath())
            Result(true)
        } catch (e: Exception) { Result(false, error = e.message ?: "Unable to remove prefix") }
    }

    private fun prefixesRoot(): File = managedRoot(PREFIXES)
    private fun backupsRoot(): File = managedRoot(BACKUPS)
    private fun managedRoot(name: String): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Private root must not be a symbolic link" }
        val root = File(privateRoot, name)
        Files.createDirectories(root.toPath())
        require(!Files.isSymbolicLink(root.toPath()) && root.isDirectory) { "Managed directory is unsafe" }
        return root.canonicalFile
    }

    private fun writeMetadata(directory: File, id: String, name: String, prepared: Boolean) {
        val file = File(directory, PREFIX_MARKER)
        require(!file.exists()) { "Prefix metadata already exists" }
        val bytes = JSONObject().put("schemaVersion", 1).put("prefixId", id)
            .put("displayName", name).put("prepared", prepared).put("createdAt", System.currentTimeMillis())
            .toString(2).toByteArray(Charsets.UTF_8)
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }

    private fun readMetadata(directory: File): JSONObject {
        val file = File(directory, PREFIX_MARKER)
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile && file.length() in 1..MAX_METADATA_BYTES.toLong()) {
            "Prefix metadata is missing or unsafe"
        }
        val json = JSONObject(file.readText(Charsets.UTF_8))
        require(json.optInt("schemaVersion") == 1 && json.optString("prefixId") == directory.name) {
            "Prefix metadata is invalid"
        }
        return json
    }

    private fun hasWineMarkers(directory: File): Boolean {
        fun regular(name: String) = File(directory, name).let { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        fun dir(name: String) = File(directory, name).let { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
        return regular("system.reg") && regular("user.reg") && regular("userdef.reg") &&
            dir("drive_c") && dir("dosdevices")
    }

    private fun scanTree(root: Path, includeMetadata: Boolean = true): Pair<Int, Long> {
        var files = 0
        var bytes = 0L
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!attrs.isSymbolicLink && !Files.isSymbolicLink(dir)) { "Symbolic link found in prefix" }
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(attrs.isRegularFile && !attrs.isSymbolicLink && !Files.isSymbolicLink(file)) {
                    "Non-regular file found in prefix"
                }
                if (!includeMetadata && file.fileName.toString() == PREFIX_MARKER) return FileVisitResult.CONTINUE
                files++
                bytes = Math.addExact(bytes, attrs.size())
                require(files <= MAX_FILES && bytes <= MAX_BYTES) { "Prefix exceeds storage safety limits" }
                return FileVisitResult.CONTINUE
            }
        })
        return files to bytes
    }

    private fun copyTree(source: Path, destination: Path, excludeMetadata: Boolean, excludedName: String? = null) {
        scanTree(source)
        var copied = 0L
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!attrs.isSymbolicLink && !Files.isSymbolicLink(dir)) { "Symbolic link found in source tree" }
                val relative = source.relativize(dir)
                if (relative.toString().isNotEmpty()) Files.createDirectories(destination.resolve(relative))
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(attrs.isRegularFile && !attrs.isSymbolicLink && !Files.isSymbolicLink(file)) { "Non-regular source file" }
                val name = file.fileName.toString()
                if ((excludeMetadata && name == PREFIX_MARKER) || name == excludedName) return FileVisitResult.CONTINUE
                val target = destination.resolve(source.relativize(file))
                Files.createDirectories(target.parent)
                Files.copy(file, target)
                copied = Math.addExact(copied, attrs.size())
                require(copied <= MAX_BYTES) { "Prefix copy exceeds byte limit" }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun treeDigest(root: Path, excludedName: String? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val files = mutableListOf<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!attrs.isSymbolicLink && !Files.isSymbolicLink(dir)) { "Symbolic link in backup" }
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(attrs.isRegularFile && !attrs.isSymbolicLink && !Files.isSymbolicLink(file)) { "Invalid backup file" }
                if (file.fileName.toString() != excludedName) files += file
                return FileVisitResult.CONTINUE
            }
        })
        files.sortedBy { root.relativize(it).toString() }.forEach { file ->
            digest.update(root.relativize(file).toString().replace(File.separatorChar, '/').toByteArray(Charsets.UTF_8))
            digest.update(0)
            FileInputStream(file.toFile()).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteTree(root: Path) {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(dir)) { "Refusing to traverse symbolic link" }
                return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!Files.isSymbolicLink(file)) { "Refusing to delete symbolic link" }
                Files.delete(file); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir); return FileVisitResult.CONTINUE
            }
        })
    }

    private fun safeId(value: String) = value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$"))
    private fun JSONObject.writeTo(file: File) { FileOutputStream(file).use { it.write(toString(2).toByteArray(Charsets.UTF_8)); it.fd.sync() } }

    companion object {
        private const val PREFIXES = "wine-prefixes"
        private const val BACKUPS = "wine-prefix-backups"
        private const val PREFIX_MARKER = ".ngr-prefix.json"
        private const val BACKUP_MARKER = ".ngr-backup.json"
        private const val MAX_METADATA_BYTES = 16 * 1024
        private const val MAX_FILES = 100_000
        private const val MAX_BYTES = 16L * 1024 * 1024 * 1024
    }
}
