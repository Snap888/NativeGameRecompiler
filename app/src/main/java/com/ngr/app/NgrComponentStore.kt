package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Private version store for verified component payloads.
 *
 * Promotion and activation state are atomic. The store never loads or executes component files.
 */
class NgrComponentStore(private val privateRoot: File) {
    data class Result(
        val success: Boolean,
        val installedDirectory: File? = null,
        val activeVersion: String? = null,
        val error: String? = null
    )

    private data class State(val current: String?, val previous: String?)

    fun stagingDirectory(): File {
        ensurePrivateRoot()
        return File(privateRoot, STAGING_DIRECTORY).also {
            Files.createDirectories(it.toPath())
            require(!Files.isSymbolicLink(it.toPath())) { "Staging directory must not be a symbolic link" }
        }
    }

    fun installAndActivate(
        component: NgrComponentCatalog.Component,
        stagedDirectory: File
    ): Result {
        validateComponent(component)?.let { return Result(false, error = it) }
        return try {
            val stagingRoot = stagingDirectory().canonicalFile
            val staged = stagedDirectory.canonicalFile
            require(staged.parentFile == stagingRoot && staged.isDirectory &&
                !Files.isSymbolicLink(stagedDirectory.toPath())) {
                "Staged component must be a direct child of the private staging directory"
            }
            validateTree(staged.toPath())

            val componentsRoot = File(privateRoot, COMPONENTS_DIRECTORY)
            Files.createDirectories(componentsRoot.toPath())
            require(!Files.isSymbolicLink(componentsRoot.toPath())) { "Components directory must not be a symbolic link" }
            val componentRoot = File(componentsRoot, component.id)
            Files.createDirectories(componentRoot.toPath())
            require(!Files.isSymbolicLink(componentRoot.toPath())) { "Component store path must not be a symbolic link" }
            val versions = File(componentRoot, VERSIONS_DIRECTORY)
            Files.createDirectories(versions.toPath())
            require(!Files.isSymbolicLink(versions.toPath())) { "Version store path must not be a symbolic link" }

            val destination = File(versions, component.version)
            require(!destination.exists()) { "Component version is already installed: ${component.version}" }
            val previousState = readState(componentRoot)
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)

            val nextState = State(component.version, previousState.current)
            try {
                writeState(componentRoot, nextState)
            } catch (e: Exception) {
                // Preserve the old active pointer if state promotion fails.
                try {
                    Files.move(destination.toPath(), staged.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    deleteTree(destination.toPath())
                }
                throw e
            }
            Result(true, destination, component.version)
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Component promotion failed")
        }
    }

    fun rollback(componentId: String): Result {
        if (!isSafeId(componentId)) return Result(false, error = "Component ID is invalid")
        return try {
            val componentRoot = File(File(privateRoot, COMPONENTS_DIRECTORY), componentId)
            val state = readState(componentRoot)
            val previous = state.previous ?: return Result(false, error = "No previous component version is available")
            val versionsRoot = File(componentRoot, VERSIONS_DIRECTORY)
            require(!Files.isSymbolicLink(versionsRoot.toPath()) && versionsRoot.isDirectory) { "Version store directory is unsafe" }
            val previousDirectory = File(versionsRoot, previous)
            require(previousDirectory.isDirectory && !Files.isSymbolicLink(previousDirectory.toPath())) {
                "Previous component version is missing or unsafe"
            }
            writeState(componentRoot, State(previous, state.current))
            Result(true, previousDirectory, previous)
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Component rollback failed")
        }
    }

    fun activeVersionDirectory(componentId: String): File? {
        if (!isSafeId(componentId)) return null
        return try {
            val componentRoot = File(File(privateRoot, COMPONENTS_DIRECTORY), componentId)
            val version = readState(componentRoot).current ?: return null
            val versionsRoot = File(componentRoot, VERSIONS_DIRECTORY)
            if (Files.isSymbolicLink(versionsRoot.toPath()) || !versionsRoot.isDirectory) return null
            File(versionsRoot, version).takeIf {
                it.isDirectory && !Files.isSymbolicLink(it.toPath())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun removeVersion(componentId: String, version: String): Result {
        if (!isSafeId(componentId) || !isSafeVersion(version)) return Result(false, error = "Component ID or version is invalid")
        return try {
            val componentRoot = File(File(privateRoot, COMPONENTS_DIRECTORY), componentId)
            val state = readState(componentRoot)
            require(version != state.current && version != state.previous) {
                "Cannot remove active or rollback component version"
            }
            val versionsDirectory = File(componentRoot, VERSIONS_DIRECTORY)
            require(!Files.isSymbolicLink(versionsDirectory.toPath()) && versionsDirectory.isDirectory) { "Version store directory is unsafe" }
            val versionsRoot = versionsDirectory.canonicalFile
            val target = File(versionsRoot, version)
            require(target.canonicalFile.parentFile == versionsRoot && target.isDirectory) {
                "Component version directory is missing or unsafe"
            }
            deleteTree(target.toPath())
            Result(true)
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Component version removal failed")
        }
    }

    fun uninstall(componentId: String): Result {
        if (!isSafeId(componentId)) return Result(false, error = "Component ID is invalid")
        return try {
            val componentRoot = File(File(privateRoot, COMPONENTS_DIRECTORY), componentId)
            if (!componentRoot.exists()) return Result(true)
            require(!Files.isSymbolicLink(componentRoot.toPath())) { "Component root is a symbolic link" }
            val componentsRoot = componentRoot.parentFile
            require(componentsRoot == null || !Files.isSymbolicLink(componentsRoot.toPath())) {
                "Components directory is a symbolic link"
            }
            writeState(componentRoot, State(null, null))
            deleteTree(componentRoot.toPath())
            Result(true)
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Component uninstall failed")
        }
    }

    private fun ensurePrivateRoot() {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Component store root must not be a symbolic link" }
    }

    private fun validateComponent(component: NgrComponentCatalog.Component): String? = when {
        !isSafeId(component.id) -> "Component ID is invalid"
        !isSafeVersion(component.version) -> "Component version is invalid"
        !isSafeRelativePath(component.installSubdirectory) -> "Component install subdirectory is unsafe"
        else -> null
    }

    private fun readState(componentRoot: File): State {
        val componentsRoot = componentRoot.parentFile
        if (componentsRoot != null && componentsRoot.exists()) {
            require(!Files.isSymbolicLink(componentsRoot.toPath()) && componentsRoot.isDirectory) {
                "Components directory is invalid"
            }
        }
        if (componentRoot.exists()) {
            require(!Files.isSymbolicLink(componentRoot.toPath()) && componentRoot.isDirectory) {
                "Component directory is invalid"
            }
        }
        val stateFile = File(componentRoot, STATE_FILE)
        if (!stateFile.exists()) return State(null, null)
        require(!Files.isSymbolicLink(stateFile.toPath()) && stateFile.isFile && stateFile.length() <= 512L) {
            "Component state file is invalid"
        }
        val lines = stateFile.readText(Charsets.UTF_8).split('\n')
        require(lines.size == 2) { "Component state file is malformed" }
        val current = lines[0].ifEmpty { null }
        val previous = lines[1].ifEmpty { null }
        require((current == null || isSafeVersion(current)) && (previous == null || isSafeVersion(previous))) {
            "Component state contains an invalid version"
        }
        return State(current, previous)
    }

    private fun writeState(componentRoot: File, state: State) {
        val temporary = File.createTempFile("state-", ".tmp", componentRoot)
        try {
            val text = "${state.current.orEmpty()}\n${state.previous.orEmpty()}"
            FileOutputStream(temporary, false).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                File(componentRoot, STATE_FILE).toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }

    private fun validateTree(root: Path) {
        var count = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                count++
                require(count <= MAX_STAGED_FILES) { "Staged component exceeds file count limit" }
                require(!attrs.isSymbolicLink && attrs.isRegularFile) { "Staged component contains a non-regular file" }
                return FileVisitResult.CONTINUE
            }

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                require(!attrs.isSymbolicLink && attrs.isDirectory) { "Staged component contains an unsafe directory" }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteTree(root: Path) {
        if (!Files.exists(root)) return
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun isSafeRelativePath(value: String): Boolean =
        value.isNotBlank() && value.length <= 512 && !value.startsWith("/") &&
            !value.startsWith("\\") && !value.contains('\\') && !value.contains(':') &&
            !value.contains(0.toChar()) &&
            value.split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun isSafeId(value: String): Boolean =
        value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$"))

    private fun isSafeVersion(value: String): Boolean =
        value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._+-]{0,127}$"))

    companion object {
        private const val STAGING_DIRECTORY = "staging"
        private const val COMPONENTS_DIRECTORY = "components"
        private const val VERSIONS_DIRECTORY = "versions"
        private const val STATE_FILE = "active-state"
        private const val MAX_STAGED_FILES = 8192
    }
}
