package com.ngr.app

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption

/** Validates an already extracted NGR package directory. ZIP inspection/extraction is separate. */
object NgrManifestValidator {
    data class Result(val valid: Boolean, val errors: List<String>)

    private val packageIdPattern = Regex("^[a-z0-9][a-z0-9._-]{2,127}$")
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")
    private val rootKeys = setOf(
        "formatVersion", "packageId", "displayName", "version", "backend",
        "entryPoint", "minimumRuntimeVersion", "graphics", "files", "provenance"
    )
    private val backendKeys = setOf("id", "version", "targetAbi", "sourcePlatform", "sourceArchitecture")
    private val graphicsKeys = setOf("api", "requiredExtensions")
    private val fileKeys = setOf("path", "sha256")

    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_FILES = 4096
    const val MAX_PATH_CHARS = 1024
    const val MAX_FILE_BYTES = 512L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

    fun validate(manifestText: String, packageRoot: File, verifyHashes: Boolean = true): Result {
        val errors = mutableListOf<String>()
        if (manifestText.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES)
            return Result(false, listOf("Manifest exceeds size limit"))

        val manifest = try { JSONObject(manifestText) }
        catch (_: JSONException) { return Result(false, listOf("Manifest is not valid JSON")) }

        rejectUnknownKeys(manifest, rootKeys, "manifest", errors)
        if (manifest.opt("formatVersion") != 1) errors += "Unsupported formatVersion"
        val packageId = manifest.opt("packageId")
        if (packageId !is String || !packageIdPattern.matches(packageId))
            errors += "Invalid packageId"
        val displayName = manifest.opt("displayName")
        if (displayName !is String || displayName.isBlank() || displayName.length > 160)
            errors += "displayName must contain 1..160 characters"

        for (optionalString in listOf("version", "minimumRuntimeVersion", "provenance")) {
            if (manifest.has(optionalString) && manifest.opt(optionalString) !is String)
                errors += "$optionalString must be a string"
        }

        val backend = manifest.optJSONObject("backend")
        if (backend == null) errors += "backend object is required"
        else {
            rejectUnknownKeys(backend, backendKeys, "backend", errors)
            if (backend.opt("id") !is String || backend.optString("id").isBlank())
                errors += "backend.id is required"
            if (backend.opt("version") !is String || backend.optString("version").isBlank())
                errors += "backend.version is required"
            if (backend.opt("targetAbi") != "arm64-v8a") errors += "Unsupported targetAbi"
            for (key in listOf("sourcePlatform", "sourceArchitecture")) {
                if (backend.has(key) && backend.opt(key) !is String)
                    errors += "backend.$key must be a string"
            }
        }

        val graphics = manifest.opt("graphics")
        if (manifest.has("graphics") && graphics !is JSONObject) errors += "graphics must be an object"
        if (graphics is JSONObject) {
            rejectUnknownKeys(graphics, graphicsKeys, "graphics", errors)
            if (graphics.has("api") && graphics.opt("api") !is String)
                errors += "graphics.api must be a string"
            if (graphics.has("requiredExtensions")) {
                val extensions = graphics.opt("requiredExtensions")
                if (extensions !is org.json.JSONArray) errors += "graphics.requiredExtensions must be an array"
                else for (i in 0 until extensions.length()) {
                    if (extensions.opt(i) !is String) errors += "graphics.requiredExtensions[$i] must be a string"
                }
            }
        }

        val entryPointRaw = manifest.opt("entryPoint")
        val entryPoint = if (entryPointRaw is String) normalizePath(entryPointRaw) else ""
        if (!isSafeRelativePath(entryPointRaw as? String ?: "")) errors += "Unsafe or missing entryPoint"

        val rootPath = packageRoot.toPath()
        if (Files.isSymbolicLink(rootPath) || !Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS))
            errors += "Package root must be a real directory"
        val root = packageRoot.canonicalFile
        val hashedPaths = mutableSetOf<String>()
        var totalBytes = 0L
        val files = manifest.opt("files")
        if (files !is org.json.JSONArray || files.length() == 0) errors += "files must contain at least one entry"
        else if (files.length() > MAX_FILES) errors += "files exceeds maximum entry count"
        else {
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i)
                if (item == null) { errors += "files[$i] must be an object"; continue }
                rejectUnknownKeys(item, fileKeys, "files[$i]", errors)
                val rawPath = item.opt("path")
                val rawHash = item.opt("sha256")
                if (rawPath !is String || !isSafeRelativePath(rawPath)) {
                    errors += "files[$i] has an unsafe path"
                    continue
                }
                if (rawHash !is String || !sha256Pattern.matches(rawHash)) {
                    errors += "Invalid SHA-256 for ${rawPath}"
                    continue
                }
                val path = normalizePath(rawPath)
                if (!hashedPaths.add(path)) errors += "Duplicate file path: $path"
                if (path.length > MAX_PATH_CHARS) { errors += "Package path exceeds length limit"; continue }

                var unresolved = root.toPath()
                var hasSymlink = false
                for (segment in path.split('/')) {
                    unresolved = unresolved.resolve(segment)
                    if (Files.isSymbolicLink(unresolved)) { hasSymlink = true; break }
                }
                if (hasSymlink) { errors += "Symbolic links are not allowed: $path"; continue }
                val file = File(root, path)
                val filePath = file.toPath()
                if (!filePath.normalize().startsWith(root.toPath())) {
                    errors += "Path escapes package root: $path"
                    continue
                }
                if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
                    errors += "Package file is missing or is not a regular file: $path"
                    continue
                }
                val size = Files.size(filePath)
                if (size > MAX_FILE_BYTES) { errors += "Package file exceeds size limit: $path"; continue }
                if (totalBytes > MAX_TOTAL_BYTES - size) {
                    errors += "Package payload exceeds total size limit"
                    continue
                }
                totalBytes += size
                if (verifyHashes && sha256(file) != rawHash.lowercase())
                    errors += "SHA-256 mismatch: $path"
            }
        }
        if (entryPoint.isNotEmpty() && entryPoint !in hashedPaths)
            errors += "entryPoint must be included in the hashed files list"
        return Result(errors.isEmpty(), errors)
    }

    private fun rejectUnknownKeys(obj: JSONObject, allowed: Set<String>, prefix: String, errors: MutableList<String>) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowed) errors += "Unknown $prefix property: $key"
        }
    }

    private fun normalizePath(path: String) = path.replace('\\\\', '/')

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.length > MAX_PATH_CHARS || path.startsWith("/") || path.startsWith("\\\\")) return false
        if (path.contains('\\u0000') || path.contains(':')) return false
        return normalizePath(path).split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
