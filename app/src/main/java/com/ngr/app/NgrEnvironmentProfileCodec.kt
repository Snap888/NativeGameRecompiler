package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict, deterministic JSON codec for persistent environment profiles.
 */
object NgrEnvironmentProfileCodec {
    const val MAX_PROFILE_BYTES = 1024 * 1024

    fun encode(profile: NgrEnvironmentProfile): String {
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val root = JSONObject()
            .put("schemaVersion", profile.schemaVersion)
            .put("profileId", profile.profileId)
            .put("displayName", profile.displayName)
            .put("containerMode", profile.containerMode.name)
            .put("cpuTranslator", profile.cpuTranslator.name)
            .put("box64Preset", profile.box64Preset.name)
            .put("graphicsProvider", profile.graphicsProvider.name)
            .put("displayBackend", profile.displayBackend.name)
            .put("waylandWineSupported", profile.waylandWineSupported)
            .put("waylandTurnipSupported", profile.waylandTurnipSupported)
            .put("audioBackend", profile.audioBackend.name)
            .put("desktopWidth", profile.desktopWidth)
            .put("desktopHeight", profile.desktopHeight)
            .put("dpi", profile.dpi)
            .put("environmentVariables", stringMap(profile.environmentVariables))
            .put("reshade", JSONObject()
                .put("enabled", profile.reshade.enabled)
                .put("componentId", nullable(profile.reshade.componentId))
                .put("presetPath", nullable(profile.reshade.presetPath))
                .put("shaderPackageIds", JSONArray(profile.reshade.shaderPackageIds)))
            .put("gameOverrides", JSONObject())

        putNullable(root, "wineLayerId", profile.wineLayerId)
        putNullable(root, "fexCoreComponentId", profile.fexCoreComponentId)
        putNullable(root, "wowBox64ComponentId", profile.wowBox64ComponentId)
        putNullable(root, "dxvkComponentId", profile.dxvkComponentId)
        putNullable(root, "vkd3dComponentId", profile.vkd3dComponentId)
        putNullable(root, "turnipComponentId", profile.turnipComponentId)

        val overrides = root.getJSONObject("gameOverrides")
        profile.gameOverrides.toSortedMap().forEach { (gameId, value) ->
            val item = JSONObject()
                .put("environmentVariables", stringMap(value.environmentVariables))
            putNullable(item, "wineLayerId", value.wineLayerId)
            putNullable(item, "dxvkComponentId", value.dxvkComponentId)
            putNullable(item, "vkd3dComponentId", value.vkd3dComponentId)
            putNullable(item, "turnipComponentId", value.turnipComponentId)
            putNullable(item, "displayBackend", value.displayBackend?.name)
            overrides.put(gameId, item)
        }
        return root.toString(2)
    }

    fun decode(json: String): NgrEnvironmentProfile {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_PROFILE_BYTES) {
            "Environment profile exceeds maximum JSON size"
        }
        val root = try { JSONObject(json) }
        catch (e: Exception) { throw IllegalArgumentException("Invalid environment profile JSON", e) }
        rejectUnknown(root, ROOT_KEYS, "profile")
        val reshadeJson = root.getJSONObject("reshade")
        rejectUnknown(reshadeJson, RESHADE_KEYS, "profile.reshade")
        val overridesJson = root.getJSONObject("gameOverrides")
        val overrides = linkedMapOf<String, NgrEnvironmentProfile.NgrGameEnvironmentOverride>()
        val overrideKeys = overridesJson.keys()
        while (overrideKeys.hasNext()) {
            val gameId = overrideKeys.next()
            val item = overridesJson.getJSONObject(gameId)
            rejectUnknown(item, OVERRIDE_KEYS, "profile.gameOverrides.$gameId")
            overrides[gameId] = NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                wineLayerId = optionalString(item, "wineLayerId"),
                dxvkComponentId = optionalString(item, "dxvkComponentId"),
                vkd3dComponentId = optionalString(item, "vkd3dComponentId"),
                turnipComponentId = optionalString(item, "turnipComponentId"),
                displayBackend = optionalEnum(item, "displayBackend", NgrEnvironmentProfile.DisplayBackend::valueOf),
                environmentVariables = readStringMap(item.getJSONObject("environmentVariables"))
            )
        }
        val profile = NgrEnvironmentProfile(
            schemaVersion = integer(root, "schemaVersion"),
            profileId = requiredString(root, "profileId"),
            displayName = requiredString(root, "displayName"),
            containerMode = enumValue(requiredString(root, "containerMode"), NgrEnvironmentProfile.ContainerMode::valueOf, "containerMode"),
            wineLayerId = optionalString(root, "wineLayerId"),
            cpuTranslator = enumValue(requiredString(root, "cpuTranslator"), NgrEnvironmentProfile.CpuTranslator::valueOf, "cpuTranslator"),
            box64Preset = enumValue(requiredString(root, "box64Preset"), NgrEnvironmentProfile.Box64Preset::valueOf, "box64Preset"),
            fexCoreComponentId = optionalString(root, "fexCoreComponentId"),
            wowBox64ComponentId = optionalString(root, "wowBox64ComponentId"),
            dxvkComponentId = optionalString(root, "dxvkComponentId"),
            vkd3dComponentId = optionalString(root, "vkd3dComponentId"),
            graphicsProvider = enumValue(requiredString(root, "graphicsProvider"), NgrEnvironmentProfile.GraphicsProvider::valueOf, "graphicsProvider"),
            turnipComponentId = optionalString(root, "turnipComponentId"),
            displayBackend = enumValue(requiredString(root, "displayBackend"), NgrEnvironmentProfile.DisplayBackend::valueOf, "displayBackend"),
            waylandWineSupported = boolean(root, "waylandWineSupported"),
            waylandTurnipSupported = boolean(root, "waylandTurnipSupported"),
            reshade = NgrEnvironmentProfile.ReShadeProfile(
                enabled = boolean(reshadeJson, "enabled"),
                componentId = optionalString(reshadeJson, "componentId"),
                presetPath = optionalString(reshadeJson, "presetPath"),
                shaderPackageIds = stringArray(reshadeJson, "shaderPackageIds")
            ),
            audioBackend = enumValue(requiredString(root, "audioBackend"), NgrEnvironmentProfile.AudioBackend::valueOf, "audioBackend"),
            desktopWidth = integer(root, "desktopWidth"),
            desktopHeight = integer(root, "desktopHeight"),
            dpi = integer(root, "dpi"),
            environmentVariables = readStringMap(root.getJSONObject("environmentVariables")),
            gameOverrides = overrides
        )
        val errors = profile.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return profile
    }

    private fun stringMap(values: Map<String, String>): JSONObject =
        JSONObject().also { json -> values.toSortedMap().forEach { (key, value) -> json.put(key, value) } }

    private fun readStringMap(json: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            require(value is String) { "Environment variable '$key' must have a string value" }
            result[key] = value
        }
        return result
    }

    private fun stringArray(json: JSONObject, key: String): List<String> {
        val array = json.getJSONArray(key)
        return (0 until array.length()).map { index ->
            val value = array.get(index)
            require(value is String) { "$key[$index] must be a string" }
            value
        }
    }

    private fun requiredString(json: JSONObject, key: String): String {
        val value = json.get(key)
        require(value is String) { "$key must be a string" }
        return value
    }

    private fun optionalString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.get(key)
        require(value is String) { "$key must be a string or null" }
        return value
    }

    private fun integer(json: JSONObject, key: String): Int {
        val value = json.get(key)
        require(value is Number && value.toDouble() == value.toInt().toDouble()) { "$key must be an integer" }
        return value.toInt()
    }

    private fun boolean(json: JSONObject, key: String): Boolean {
        val value = json.get(key)
        require(value is Boolean) { "$key must be a boolean" }
        return value
    }

    private fun <T : Enum<T>> optionalEnum(json: JSONObject, key: String, parse: (String) -> T): T? =
        optionalString(json, key)?.let { enumValue(it, parse, key) }

    private fun <T> enumValue(value: String, parse: (String) -> T, key: String): T =
        try { parse(value) } catch (e: Exception) {
            throw IllegalArgumentException("Unsupported value '$value' for $key", e)
        }

    private fun rejectUnknown(json: JSONObject, allowed: Set<String>, context: String) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key in allowed) { "Unknown key '$key' in $context" }
        }
    }

    private fun nullable(value: String?): Any = value ?: JSONObject.NULL

    private fun putNullable(json: JSONObject, key: String, value: String?) {
        json.put(key, nullable(value))
    }

    private val ROOT_KEYS = setOf(
        "schemaVersion", "profileId", "displayName", "containerMode", "wineLayerId",
        "cpuTranslator", "box64Preset", "fexCoreComponentId", "wowBox64ComponentId",
        "dxvkComponentId", "vkd3dComponentId", "graphicsProvider", "turnipComponentId",
        "displayBackend", "waylandWineSupported", "waylandTurnipSupported", "reshade",
        "audioBackend", "desktopWidth", "desktopHeight", "dpi", "environmentVariables", "gameOverrides"
    )
    private val RESHADE_KEYS = setOf("enabled", "componentId", "presetPath", "shaderPackageIds")
    private val OVERRIDE_KEYS = setOf(
        "wineLayerId", "dxvkComponentId", "vkd3dComponentId", "turnipComponentId",
        "displayBackend", "environmentVariables"
    )
}
