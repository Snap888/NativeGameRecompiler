package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A persisted per-game launch shortcut. Paths are guest paths, never host filesystem paths.
 * This model does not itself launch a process or grant package payloads executable trust.
 */
data class NgrGameShortcut(
    val shortcutId: String,
    val title: String,
    val packageId: String,
    val executablePath: String,
    val workingDirectory: String? = null,
    val arguments: List<String> = emptyList(),
    val environmentProfileId: String,
    val launchMode: LaunchMode = LaunchMode.WINE_COMPATIBILITY,
    val environmentOverrides: Map<String, String> = emptyMap(),
    val iconAsset: String? = null,
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val lastLaunchedAtEpochMillis: Long? = null
) {
    enum class LaunchMode { WINE_COMPATIBILITY, STATIC_RECOMPILED, NATIVE_ARM64 }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!SAFE_ID.matches(shortcutId)) errors += "shortcutId is invalid"
        if (!SAFE_ID.matches(packageId)) errors += "packageId is invalid"
        if (title.isBlank() || title.length > 100) errors += "title must contain 1..100 characters"
        if (!isSafeGuestPath(executablePath)) errors += "executablePath must be a safe guest path"
        if (workingDirectory != null && !isSafeGuestPath(workingDirectory)) errors += "workingDirectory must be a safe guest path"
        if (arguments.size > 128 || arguments.any { it.length > 4096 || it.contains('\u0000') }) {
            errors += "arguments exceed the allowed bounds"
        }
        if (!SAFE_ID.matches(environmentProfileId)) errors += "environmentProfileId is invalid"
        if (environmentOverrides.size > 128) errors += "too many per-game environment overrides"
        environmentOverrides.forEach { (key, value) ->
            if (!ENV_KEY.matches(key) || NgrEnvironmentVariablePolicy.isRuntimeManaged(key)) {
                errors += "environment override contains a protected or invalid key: $key"
            }
            if (value.length > 4096 || value.contains('\u0000')) errors += "environment override value is invalid: $key"
        }
        if (iconAsset != null && !isSafeRelativeAsset(iconAsset)) errors += "iconAsset must be a safe app-managed relative path"
        if (createdAtEpochMillis < 0 || (lastLaunchedAtEpochMillis != null && lastLaunchedAtEpochMillis < 0)) {
            errors += "shortcut timestamps cannot be negative"
        }
        return errors
    }

    companion object {
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val ENV_KEY = Regex("^[A-Z_][A-Z0-9_]{0,127}$")

        private fun isSafeGuestPath(value: String): Boolean {
            if (value.isBlank() || value.length > 1024 || value.contains('\u0000')) return false
            val normalized = value.replace('\\', '/')
            val drivePath = Regex("^[A-Za-z]:/.*$").matches(normalized)
            if (!drivePath && (normalized.startsWith("/") || normalized.startsWith("//"))) return false
            val pathPart = if (drivePath) normalized.substring(3) else normalized
            return pathPart.split('/').none { it == ".." || it == "." }
        }

        private fun isSafeRelativeAsset(value: String): Boolean =
            value.isNotBlank() && value.length <= 256 && !value.startsWith("/") &&
                !value.contains('\\') && !value.contains(':') &&
                value.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }
}

/**
 * Atomic, bounded app-private persistence for game shortcuts.
 * This is the library metadata layer; Android pinned-shortcut publication and process launch
 * are deliberately separate integrations.
 */
class NgrGameShortcutStore(private val privateRoot: File) {
    data class Result(val success: Boolean, val error: String? = null)
    data class LoadResult(val shortcuts: List<NgrGameShortcut>, val errors: List<String>)

    fun save(shortcut: NgrGameShortcut): Result {
        val errors = shortcut.validate()
        if (errors.isNotEmpty()) return Result(false, errors.joinToString("; "))
        return try {
            val directory = shortcutDirectory()
            val json = encode(shortcut)
            val bytes = json.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_SHORTCUT_BYTES) { "Shortcut metadata exceeds size limit" }
            val target = File(directory, "${shortcut.shortcutId}.json")
            if (target.exists()) {
                require(!Files.isSymbolicLink(target.toPath()) && target.isFile) { "Shortcut target is not a regular file" }
            } else {
                val count = directory.listFiles().orEmpty().count { it.name.endsWith(".json", true) }
                require(count < MAX_SHORTCUTS) { "Shortcut count exceeds $MAX_SHORTCUTS" }
            }
            val temporary = File.createTempFile("shortcut-", ".tmp", directory)
            try {
                FileOutputStream(temporary, false).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            Result(true)
        } catch (e: Exception) {
            Result(false, e.message ?: "Unable to save game shortcut")
        }
    }

    fun loadAll(): LoadResult {
        val directory = try { shortcutDirectory() }
        catch (e: Exception) { return LoadResult(emptyList(), listOf(e.message ?: "Shortcut directory is unsafe")) }
        val files = directory.listFiles().orEmpty().filter { it.name.endsWith(".json", true) }.sortedBy { it.name }
        if (files.size > MAX_SHORTCUTS) return LoadResult(emptyList(), listOf("Shortcut count exceeds $MAX_SHORTCUTS"))
        val shortcuts = mutableListOf<NgrGameShortcut>()
        val errors = mutableListOf<String>()
        files.forEach { file ->
            try {
                require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Shortcut file is not regular" }
                require(file.length() in 1..MAX_SHORTCUT_BYTES.toLong()) { "Shortcut file is empty or too large" }
                val shortcut = decode(file.readText(Charsets.UTF_8))
                require(file.name == "${shortcut.shortcutId}.json") { "Shortcut filename does not match shortcutId" }
                shortcuts += shortcut
            } catch (e: Exception) {
                errors += "${file.name}: ${e.message ?: "invalid shortcut"}"
            }
        }
        return LoadResult(shortcuts, errors)
    }

    fun get(shortcutId: String): NgrGameShortcut? {
        if (!SAFE_ID.matches(shortcutId)) return null
        return loadAll().shortcuts.firstOrNull { it.shortcutId == shortcutId }
    }

    fun delete(shortcutId: String): Result {
        if (!SAFE_ID.matches(shortcutId)) return Result(false, "shortcutId is invalid")
        return try {
            val file = File(shortcutDirectory(), "$shortcutId.json")
            if (!file.exists()) return Result(true)
            require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Shortcut path is not a regular file" }
            require(file.delete()) { "Unable to delete shortcut" }
            Result(true)
        } catch (e: Exception) {
            Result(false, e.message ?: "Unable to delete shortcut")
        }
    }

    private fun shortcutDirectory(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Shortcut storage root is a symbolic link" }
        val directory = File(privateRoot, "game-shortcuts")
        Files.createDirectories(directory.toPath())
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory) { "Shortcut directory is unsafe" }
        return directory
    }

    private fun encode(value: NgrGameShortcut): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("shortcutId", value.shortcutId)
        put("title", value.title)
        put("packageId", value.packageId)
        put("executablePath", value.executablePath)
        put("workingDirectory", value.workingDirectory ?: JSONObject.NULL)
        put("arguments", JSONArray(value.arguments))
        put("environmentProfileId", value.environmentProfileId)
        put("launchMode", value.launchMode.name)
        put("environmentOverrides", JSONObject().also { map -> value.environmentOverrides.toSortedMap().forEach { (k, v) -> map.put(k, v) } })
        put("iconAsset", value.iconAsset ?: JSONObject.NULL)
        put("enabled", value.enabled)
        put("createdAtEpochMillis", value.createdAtEpochMillis)
        put("lastLaunchedAtEpochMillis", value.lastLaunchedAtEpochMillis ?: JSONObject.NULL)
    }.toString()

    private fun decode(raw: String): NgrGameShortcut {
        val root = JSONObject(raw)
        val allowed = setOf("schemaVersion", "shortcutId", "title", "packageId", "executablePath", "workingDirectory",
            "arguments", "environmentProfileId", "launchMode", "environmentOverrides", "iconAsset", "enabled",
            "createdAtEpochMillis", "lastLaunchedAtEpochMillis")
        val keys = root.keys()
        while (keys.hasNext()) require(keys.next() in allowed) { "Unknown shortcut metadata field" }
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported shortcut schema version" }
        val argsJson = root.getJSONArray("arguments")
        require(argsJson.length() <= 128) { "Too many launch arguments" }
        val args = (0 until argsJson.length()).map { argsJson.getString(it) }
        val envJson = root.getJSONObject("environmentOverrides")
        require(envJson.length() <= 128) { "Too many environment overrides" }
        val env = linkedMapOf<String, String>()
        val envKeys = envJson.keys()
        while (envKeys.hasNext()) {
            val key = envKeys.next()
            env[key] = envJson.getString(key)
        }
        fun optionalString(key: String): String? = if (root.isNull(key)) null else root.getString(key)
        val result = NgrGameShortcut(
            shortcutId = root.getString("shortcutId"),
            title = root.getString("title"),
            packageId = root.getString("packageId"),
            executablePath = root.getString("executablePath"),
            workingDirectory = optionalString("workingDirectory"),
            arguments = args,
            environmentProfileId = root.getString("environmentProfileId"),
            launchMode = NgrGameShortcut.LaunchMode.valueOf(root.getString("launchMode")),
            environmentOverrides = env,
            iconAsset = optionalString("iconAsset"),
            enabled = root.getBoolean("enabled"),
            createdAtEpochMillis = root.getLong("createdAtEpochMillis"),
            lastLaunchedAtEpochMillis = if (root.isNull("lastLaunchedAtEpochMillis")) null else root.getLong("lastLaunchedAtEpochMillis")
        )
        val errors = result.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return result
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MAX_SHORTCUTS = 512
        private const val MAX_SHORTCUT_BYTES = 64 * 1024
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    }
}
