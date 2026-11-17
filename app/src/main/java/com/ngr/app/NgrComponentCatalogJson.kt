package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict JSON decoder for NGR component catalogs. Unknown fields are rejected so a newer
 * schema cannot silently be interpreted with older install semantics.
 */
object NgrComponentCatalogJson {
    fun parse(json: String): NgrComponentCatalog {
        val root = JSONObject(json)
        requireKeys(root, setOf("schemaVersion", "catalogId", "revision", "components"))
        val catalog = NgrComponentCatalog(
            schemaVersion = root.getInt("schemaVersion"),
            catalogId = root.getString("catalogId"),
            revision = root.getString("revision"),
            components = root.getJSONArray("components").toObjects(::parseComponent)
        )
        val errors = catalog.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        return catalog
    }

    private fun parseComponent(value: JSONObject): NgrComponentCatalog.Component {
        requireKeys(value, setOf(
            "id", "type", "version", "upstreamCommit", "targetAbis", "archiveFormat",
            "downloadUrl", "sha256", "licenseSpdx", "sourceRepository", "dependencies",
            "conflicts", "constraints", "installSubdirectory", "rollbackSlot"
        ), optional = setOf("upstreamCommit", "dependencies", "conflicts", "constraints", "rollbackSlot"))
        val constraintsJson = value.optJSONObject("constraints") ?: JSONObject()
        requireKeys(constraintsJson, setOf(
            "minimumNgrVersion", "containerModes", "requiredComponents", "forbiddenComponents",
            "requiresWayland", "requiresVulkan"
        ), optional = setOf(
            "minimumNgrVersion", "containerModes", "requiredComponents", "forbiddenComponents",
            "requiresWayland", "requiresVulkan"
        ))
        val constraints = NgrComponentCatalog.CompatibilityConstraints(
            minimumNgrVersion = constraintsJson.optStringOrNull("minimumNgrVersion"),
            containerModes = constraintsJson.optStringSet("containerModes")
                .map { NgrEnvironmentProfile.ContainerMode.valueOf(it) }.toSet(),
            requiredComponents = constraintsJson.optStringSet("requiredComponents"),
            forbiddenComponents = constraintsJson.optStringSet("forbiddenComponents"),
            requiresWayland = constraintsJson.optBoolean("requiresWayland", false),
            requiresVulkan = constraintsJson.optBoolean("requiresVulkan", false)
        )
        return NgrComponentCatalog.Component(
            id = value.getString("id"),
            type = NgrComponentCatalog.Type.valueOf(value.getString("type")),
            version = value.getString("version"),
            upstreamCommit = value.optStringOrNull("upstreamCommit"),
            targetAbis = value.getJSONArray("targetAbis").toStringSet()
                .map { NgrComponentCatalog.TargetAbi.valueOf(it) }.toSet(),
            archiveFormat = NgrComponentCatalog.ArchiveFormat.valueOf(value.getString("archiveFormat")),
            downloadUrl = value.getString("downloadUrl"),
            sha256 = value.getString("sha256"),
            licenseSpdx = value.getString("licenseSpdx"),
            sourceRepository = value.getString("sourceRepository"),
            dependencies = value.optStringSet("dependencies"),
            conflicts = value.optStringSet("conflicts"),
            constraints = constraints,
            installSubdirectory = value.getString("installSubdirectory"),
            rollbackSlot = value.optString("rollbackSlot", "previous")
        )
    }

    private fun requireKeys(
        value: JSONObject,
        allowed: Set<String>,
        optional: Set<String> = emptySet()
    ) {
        val keys = value.keys().asSequence().toSet()
        val unknown = keys - allowed
        val missing = allowed - optional - keys
        require(unknown.isEmpty()) { "Unknown catalog JSON fields: ${unknown.sorted().joinToString()}" }
        require(missing.isEmpty()) { "Missing catalog JSON fields: ${missing.sorted().joinToString()}" }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = get(key)
        require(value is String) { "$key must be a string or null" }
        return value
    }

    private fun JSONObject.optStringSet(key: String): Set<String> {
        if (!has(key)) return emptySet()
        return getJSONArray(key).toStringSet()
    }

    private fun JSONArray.toStringSet(): Set<String> {
        val result = linkedSetOf<String>()
        for (index in 0 until length()) {
            val item = get(index)
            require(item is String) { "Array item $index must be a string" }
            require(result.add(item)) { "Duplicate array item: $item" }
        }
        return result
    }

    private fun JSONArray.toObjects(mapper: (JSONObject) -> NgrComponentCatalog.Component): List<NgrComponentCatalog.Component> {
        val result = ArrayList<NgrComponentCatalog.Component>(length())
        for (index in 0 until length()) {
            result += mapper(getJSONObject(index))
        }
        return result
    }
}
