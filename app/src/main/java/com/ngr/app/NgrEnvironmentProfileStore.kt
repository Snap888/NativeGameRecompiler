package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Atomic app-private persistence for validated environment profiles.
 */
class NgrEnvironmentProfileStore(private val privateRoot: File) {
    data class Result(val success: Boolean, val error: String? = null)
    data class LoadResult(val profiles: List<NgrEnvironmentProfile>, val errors: List<String>)

    fun save(profile: NgrEnvironmentProfile): Result {
        val errors = profile.validate()
        if (errors.isNotEmpty()) return Result(false, errors.joinToString("; "))
        return try {
            val directory = profileDirectory()
            val json = NgrEnvironmentProfileCodec.encode(profile)
            require(json.toByteArray(Charsets.UTF_8).size <= NgrEnvironmentProfileCodec.MAX_PROFILE_BYTES) {
                "Environment profile exceeds maximum JSON size"
            }
            val target = File(directory, "${profile.profileId}.json")
            val temporary = File.createTempFile("profile-", ".tmp", directory)
            try {
                FileOutputStream(temporary, false).use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } finally {
                temporary.delete()
            }
            Result(true)
        } catch (e: Exception) {
            Result(false, e.message ?: "Unable to save environment profile")
        }
    }

    fun loadAll(): LoadResult {
        val directory = try { profileDirectory() }
        catch (e: Exception) { return LoadResult(emptyList(), listOf(e.message ?: "Profile directory is unsafe")) }
        val files = directory.listFiles().orEmpty()
            .filter { it.name.endsWith(".json", ignoreCase = true) }
            .sortedBy { it.name }
        if (files.size > MAX_PROFILES) {
            return LoadResult(emptyList(), listOf("Environment profile count exceeds $MAX_PROFILES"))
        }

        val profiles = mutableListOf<NgrEnvironmentProfile>()
        val errors = mutableListOf<String>()
        files.forEach { file ->
            try {
                require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Profile file is not a regular file" }
                require(file.length() in 1..NgrEnvironmentProfileCodec.MAX_PROFILE_BYTES.toLong()) {
                    "Profile file is empty or exceeds size limit"
                }
                val profile = NgrEnvironmentProfileCodec.decode(file.readText(Charsets.UTF_8))
                require(file.name == "${profile.profileId}.json") { "Profile filename does not match profileId" }
                profiles += profile
            } catch (e: Exception) {
                errors += "${file.name}: ${e.message ?: "invalid profile"}"
            }
        }
        return LoadResult(profiles, errors)
    }

    fun get(profileId: String): NgrEnvironmentProfile? {
        if (!isSafeId(profileId)) return null
        return loadAll().profiles.firstOrNull { it.profileId == profileId }
    }

    fun delete(profileId: String): Result {
        if (!isSafeId(profileId)) return Result(false, "Profile ID is invalid")
        return try {
            val file = File(profileDirectory(), "$profileId.json")
            if (!file.exists()) return Result(true)
            require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Profile path is not a regular file" }
            require(file.delete()) { "Unable to delete environment profile" }
            Result(true)
        } catch (e: Exception) {
            Result(false, e.message ?: "Unable to delete environment profile")
        }
    }

    private fun profileDirectory(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Profile storage root must not be a symbolic link" }
        val directory = File(privateRoot, PROFILE_DIRECTORY)
        Files.createDirectories(directory.toPath())
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory) {
            "Profile directory must be a regular directory"
        }
        return directory
    }

    private fun isSafeId(value: String): Boolean =
        value.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$"))

    companion object {
        private const val PROFILE_DIRECTORY = "environment-profiles"
        private const val MAX_PROFILES = 256
    }
}
