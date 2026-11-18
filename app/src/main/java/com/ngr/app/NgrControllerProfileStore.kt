package com.ngr.app

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Bounded atomic app-private persistence for controller mapping profiles. */
class NgrControllerProfileStore(private val privateRoot: File) {
    data class Result(val success: Boolean, val error: String? = null)
    data class LoadResult(val profiles: List<NgrControllerProfile>, val errors: List<String>)

    fun save(profile: NgrControllerProfile): Result {
        val errors = profile.validate()
        if (errors.isNotEmpty()) return Result(false, errors.joinToString("; "))
        return try {
            val directory = profileDirectory()
            val bytes = encode(profile).toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_PROFILE_BYTES) { "Controller profile exceeds size limit" }
            val target = File(directory, "${profile.profileId}.json")
            if (target.exists()) {
                require(!Files.isSymbolicLink(target.toPath()) && target.isFile) { "Controller profile target is unsafe" }
            } else {
                val count = directory.listFiles().orEmpty().count { it.name.endsWith(".json", true) }
                require(count < MAX_PROFILES) { "Controller profile count exceeds $MAX_PROFILES" }
            }
            val temporary = File.createTempFile("controller-", ".tmp", directory)
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
            Result(false, e.message ?: "Unable to save controller profile")
        }
    }

    fun loadAll(): LoadResult {
        val directory = try { profileDirectory() }
        catch (e: Exception) { return LoadResult(emptyList(), listOf(e.message ?: "Controller profile directory is unsafe")) }
        val files = directory.listFiles().orEmpty().filter { it.name.endsWith(".json", true) }.sortedBy { it.name }
        if (files.size > MAX_PROFILES) return LoadResult(emptyList(), listOf("Controller profile count exceeds $MAX_PROFILES"))
        val profiles = mutableListOf<NgrControllerProfile>()
        val errors = mutableListOf<String>()
        files.forEach { file ->
            try {
                require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Controller profile file is unsafe" }
                require(file.length() in 1..MAX_PROFILE_BYTES.toLong()) { "Controller profile file is empty or too large" }
                val profile = decode(file.readText(Charsets.UTF_8))
                require(file.name == "${profile.profileId}.json") { "Controller profile filename does not match profileId" }
                profiles += profile
            } catch (e: Exception) {
                errors += "${file.name}: ${e.message ?: "invalid controller profile"}"
            }
        }
        return LoadResult(profiles, errors)
    }

    fun get(profileId: String): NgrControllerProfile? {
        if (!SAFE_ID.matches(profileId)) return null
        return loadAll().profiles.firstOrNull { it.profileId == profileId }
    }

    fun delete(profileId: String): Result {
        if (!SAFE_ID.matches(profileId)) return Result(false, "Controller profile ID is invalid")
        return try {
            val file = File(profileDirectory(), "$profileId.json")
            if (!file.exists()) return Result(true)
            require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Controller profile path is unsafe" }
            require(file.delete()) { "Unable to delete controller profile" }
            Result(true)
        } catch (e: Exception) {
            Result(false, e.message ?: "Unable to delete controller profile")
        }
    }

    private fun profileDirectory(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Controller storage root is unsafe" }
        val directory = File(privateRoot, DIRECTORY)
        Files.createDirectories(directory.toPath())
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory) { "Controller profile directory is unsafe" }
        return directory
    }

    private fun encode(profile: NgrControllerProfile): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("profileId", profile.profileId)
        put("displayName", profile.displayName)
        put("deviceDescriptor", profile.deviceDescriptor)
        put("deviceName", profile.deviceName)
        put("gamePackageId", profile.gamePackageId ?: JSONObject.NULL)
        put("bindings", JSONObject().also { json ->
            profile.bindings.toSortedMap(compareBy { it.name }).forEach { (action, binding) ->
                json.put(action.name, JSONObject()
                    .put("kind", binding.kind.name)
                    .put("code", binding.code)
                    .put("direction", binding.direction))
            }
        })
        put("leftStickDeadZone", profile.leftStickDeadZone)
        put("rightStickDeadZone", profile.rightStickDeadZone)
        put("leftStickSensitivity", profile.leftStickSensitivity)
        put("rightStickSensitivity", profile.rightStickSensitivity)
        put("triggerThreshold", profile.triggerThreshold)
        put("invertLeftX", profile.invertLeftX)
        put("invertLeftY", profile.invertLeftY)
        put("invertRightX", profile.invertRightX)
        put("invertRightY", profile.invertRightY)
        put("stickCurve", profile.stickCurve.name)
        put("vibrationEnabled", profile.vibrationEnabled)
        put("touchLayout", profile.touchLayout.name)
        put("touchOpacity", profile.touchOpacity)
        put("touchScale", profile.touchScale)
    }.toString()

    private fun decode(raw: String): NgrControllerProfile {
        val root = JSONObject(raw)
        rejectUnknown(root, ROOT_KEYS)
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "Unsupported controller profile schema" }
        val bindingsJson = root.getJSONObject("bindings")
        val bindings = linkedMapOf<NgrControllerProfile.Action, NgrControllerProfile.Binding>()
        val keys = bindingsJson.keys()
        while (keys.hasNext()) {
            val action = NgrControllerProfile.Action.valueOf(keys.next())
            val item = bindingsJson.getJSONObject(action.name)
            rejectUnknown(item, BINDING_KEYS)
            bindings[action] = NgrControllerProfile.Binding(
                kind = NgrControllerProfile.InputKind.valueOf(item.getString("kind")),
                code = item.getInt("code"),
                direction = item.getInt("direction")
            )
        }
        val profile = NgrControllerProfile(
            profileId = root.getString("profileId"),
            displayName = root.getString("displayName"),
            deviceDescriptor = root.getString("deviceDescriptor"),
            deviceName = root.getString("deviceName"),
            gamePackageId = if (root.isNull("gamePackageId")) null else root.getString("gamePackageId"),
            bindings = bindings,
            leftStickDeadZone = root.getDouble("leftStickDeadZone"),
            rightStickDeadZone = root.getDouble("rightStickDeadZone"),
            leftStickSensitivity = root.getDouble("leftStickSensitivity"),
            rightStickSensitivity = root.getDouble("rightStickSensitivity"),
            triggerThreshold = root.getDouble("triggerThreshold"),
            invertLeftX = root.getBoolean("invertLeftX"),
            invertLeftY = root.getBoolean("invertLeftY"),
            invertRightX = root.getBoolean("invertRightX"),
            invertRightY = root.getBoolean("invertRightY"),
            stickCurve = NgrControllerProfile.StickCurve.valueOf(root.getString("stickCurve")),
            vibrationEnabled = root.getBoolean("vibrationEnabled"),
            touchLayout = NgrControllerProfile.TouchLayout.valueOf(root.getString("touchLayout")),
            touchOpacity = root.getDouble("touchOpacity"),
            touchScale = root.getDouble("touchScale")
        )
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return profile
    }

    private fun rejectUnknown(json: JSONObject, allowed: Set<String>) {
        val keys = json.keys()
        while (keys.hasNext()) require(keys.next() in allowed) { "Unknown controller profile field" }
    }

    companion object {
        private const val DIRECTORY = "controller-profiles"
        private const val SCHEMA_VERSION = 1
        private const val MAX_PROFILES = 128
        private const val MAX_PROFILE_BYTES = 128 * 1024
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val ROOT_KEYS = setOf(
            "schemaVersion", "profileId", "displayName", "deviceDescriptor", "deviceName", "gamePackageId",
            "bindings", "leftStickDeadZone", "rightStickDeadZone", "leftStickSensitivity", "rightStickSensitivity",
            "triggerThreshold", "invertLeftX", "invertLeftY", "invertRightX", "invertRightY", "stickCurve",
            "vibrationEnabled", "touchLayout", "touchOpacity", "touchScale"
        )
        private val BINDING_KEYS = setOf("kind", "code", "direction")
    }
}
