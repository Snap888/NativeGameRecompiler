package com.ngr.app

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files

/** Validates an already extracted NGR package directory. ZIP extraction is a separate step. */
object NgrManifestValidator {
    data class Result(val valid: Boolean, val errors: List<String>)
    private val packageIdPattern = Regex("^[a-z0-9][a-z0-9._-]{2,127}$")
    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    fun validate(manifestText: String, packageRoot: File, verifyHashes: Boolean = true): Result {
        val errors = mutableListOf<String>()
        val manifest = try { JSONObject(manifestText) }
        catch (_: JSONException) { return Result(false, listOf("Manifest is not valid JSON")) }

        if (manifest.optInt("formatVersion", -1) != 1) errors += "Unsupported formatVersion"
        if (!packageIdPattern.matches(manifest.optString("packageId", ""))) errors += "Invalid packageId"
        if (manifest.optString("displayName").isBlank()) errors += "displayName is required"

        val backend = manifest.optJSONObject("backend")
        if (backend == null) errors += "backend object is required"
        else {
            if (backend.optString("id").isBlank()) errors += "backend.id is required"
            if (backend.optString("version").isBlank()) errors += "backend.version is required"
            if (backend.optString("targetAbi") != "arm64-v8a") errors += "Unsupported targetAbi"
        }

        val entryPoint = manifest.optString("entryPoint", "")
        if (!isSafeRelativePath(entryPoint)) errors += "Unsafe or missing entryPoint"
        else if (!File(packageRoot, entryPoint).isFile) errors += "entryPoint file does not exist"

        val hashedPaths = mutableSetOf<String>()
        val files = manifest.optJSONArray("files")
        if (files == null || files.length() == 0) errors += "files must contain at least one entry"
        else {
            val seen = mutableSetOf<String>()
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i)
                if (item == null) { errors += "files[$i] must be an object"; continue }
                val path = item.optString("path", "")
                val expected = item.optString("sha256", "")
                if (!isSafeRelativePath(path)) { errors += "files[$i] has an unsafe path"; continue }
                if (!seen.add(path)) errors += "Duplicate file path: $path"
                hashedPaths += path
                if (!sha256Pattern.matches(expected)) { errors += "Invalid SHA-256 for $path"; continue }
                val root = packageRoot.canonicalFile
                var unresolved = root.toPath()
                var hasSymlink = false
                for (segment in path.replace('\\\\', '/').split('/')) {
                    unresolved = unresolved.resolve(segment)
                    if (Files.isSymbolicLink(unresolved)) { hasSymlink = true; break }
                }
                if (hasSymlink) { errors += "Symbolic links are not allowed: $path"; continue }
                val file = File(packageRoot, path).canonicalFile
                if (!file.toPath().startsWith(root.toPath())) { errors += "Path escapes package root: $path"; continue }
                if (!file.isFile) errors += "Package file does not exist: $path"
                else if (verifyHashes && sha256(file) != expected.lowercase()) errors += "SHA-256 mismatch: $path"
            }
        }
        if (isSafeRelativePath(entryPoint) && entryPoint !in hashedPaths)
            errors += "entryPoint must be included in the hashed files list"
        return Result(errors.isEmpty(), errors)
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\u0000') || path.contains(':')) return false
        return path.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." }
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
