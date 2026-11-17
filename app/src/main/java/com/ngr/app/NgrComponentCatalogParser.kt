package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict JSON decoder for remote component catalog metadata.
 * Parsing is bounded and rejects unknown keys so misspelled security fields cannot be ignored.
 */
object NgrComponentCatalogParser {
    const val MAX_CATALOG_BYTES = 4 * 1024 * 1024

    fun parse(json: String): NgrComponentCatalog {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_CATALOG_BYTES) {
            "Component catalog exceeds maximum JSON size"
        }
        val root = try { JSONObject(json) }
        catch (e: Exception) { throw IllegalArgumentException("Invalid component catalog JSON", e) }
        rejectUnknownKeys(root, setOf("schemaVersion", "catalogId", "revision", "components"), "catalog")

        val componentsJson = root.getJSONArray("components")
        require(componentsJson.length() <= NgrComponentCatalog.MAX_COMPONENTS) {
            "Catalog exceeds component limit"
        }
        val components = ArrayList<NgrComponentCatalog.Component>(componentsJson.length())
        for (index in 0 until componentsJson.length()) {
            components += parseComponent(componentsJson.getJSONObject(index), index)
        }
        val schemaValue = root.get("schemaVersion")
        require(schemaValue is Number && schemaValue.toDouble() == schemaValue.toInt().toDouble()) {
            "schemaVersion must be an integer"
        }
        val catalog = NgrComponentCatalog(
            schemaVersion = schemaValue.toInt(),
            catalogId = root.getString("catalogId"),
            revision = root.getString("revision"),
            components = components
        )
        val errors = catalog.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return catalog
    }

    private fun parseComponent(json: JSONObject, index: Int): NgrComponentCatalog.Component {
        val context = "components[$index]"
        rejectUnknownKeys(
            json,
            setOf(
                "id", "type", "version", "upstreamCommit", "targetAbis", "archiveFormat",
                "downloadUrl", "sha256", "licenseSpdx", "sourceRepository", "dependencies",
                "conflicts", "constraints", "installSubdirectory", "rollbackSlot"
            ),
            context
        )
        val constraintsJson = if (json.has("constraints")) json.getJSONObject("constraints") else JSONObject()
        rejectUnknownKeys(
            constraintsJson,
            setOf(
                "minimumNgrVersion", "containerModes", "requiredComponents", "forbiddenComponents",
                "requiresWayland", "requiresVulkan"
            ),
            "$context.constraints"
        )
        val constraints = NgrComponentCatalog.CompatibilityConstraints(
            minimumNgrVersion = optionalString(constraintsJson, "minimumNgrVersion"),
            containerModes = enumSet(
                stringArray(constraintsJson, "containerModes"),
                NgrEnvironmentProfile.ContainerMode::valueOf,
                "$context.constraints.containerModes"
            ),
            requiredComponents = stringArray(constraintsJson, "requiredComponents").toSet(),
            forbiddenComponents = stringArray(constraintsJson, "forbiddenComponents").toSet(),
            requiresWayland = optionalBoolean(constraintsJson, "requiresWayland", false),
            requiresVulkan = optionalBoolean(constraintsJson, "requiresVulkan", false)
        )
        return NgrComponentCatalog.Component(
            id = json.getString("id"),
            type = enumValue(json.getString("type"), NgrComponentCatalog.Type::valueOf, "$context.type"),
            version = json.getString("version"),
            upstreamCommit = optionalString(json, "upstreamCommit"),
            targetAbis = enumSet(
                stringArray(json, "targetAbis"),
                NgrComponentCatalog.TargetAbi::valueOf,
                "$context.targetAbis"
            ),
            archiveFormat = enumValue(
                json.getString("archiveFormat"),
                NgrComponentCatalog.ArchiveFormat::valueOf,
                "$context.archiveFormat"
            ),
            downloadUrl = json.getString("downloadUrl"),
            sha256 = json.getString("sha256"),
            licenseSpdx = json.getString("licenseSpdx"),
            sourceRepository = json.getString("sourceRepository"),
            dependencies = stringArray(json, "dependencies").toSet(),
            conflicts = stringArray(json, "conflicts").toSet(),
            constraints = constraints,
            installSubdirectory = json.getString("installSubdirectory"),
            rollbackSlot = optionalString(json, "rollbackSlot") ?: "previous"
        )
    }

    private fun rejectUnknownKeys(json: JSONObject, allowed: Set<String>, context: String) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key in allowed) { "Unknown key '$key' in $context" }
        }
    }

    private fun stringArray(json: JSONObject, key: String): List<String> {
        if (!json.has(key)) return emptyList()
        val array = json.getJSONArray(key)
        return (0 until array.length()).map { index ->
            val value = array.get(index)
            require(value is String) { "$key[$index] must be a string" }
            value
        }
    }

    private fun optionalString(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.get(key)
        require(value is String) { "$key must be a string or null" }
        return value
    }

    private fun optionalBoolean(json: JSONObject, key: String, default: Boolean): Boolean {
        if (!json.has(key)) return default
        val value = json.get(key)
        require(value is Boolean) { "$key must be a boolean" }
        return value
    }

    private fun <T : Enum<T>> enumSet(values: List<String>, parse: (String) -> T, context: String): Set<T> =
        values.map { enumValue(it, parse, context) }.toSet()

    private fun <T> enumValue(value: String, parse: (String) -> T, context: String): T =
        try { parse(value) } catch (e: Exception) {
            throw IllegalArgumentException("Unsupported enum value '$value' in $context", e)
        }
}
