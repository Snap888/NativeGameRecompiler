package com.ngr.app

import java.util.Locale

/**
 * Typed, untrusted component catalog metadata.
 *
 * This layer validates provenance and install intent only. It deliberately does not download,
 * extract, install, activate, dlopen or execute component payloads.
 */
data class NgrComponentCatalog(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val catalogId: String,
    val revision: String,
    val components: List<Component>
) {
    data class Component(
        val id: String,
        val type: Type,
        val version: String,
        val upstreamCommit: String? = null,
        val targetAbis: Set<TargetAbi>,
        val archiveFormat: ArchiveFormat,
        val downloadUrl: String,
        val sha256: String,
        val licenseSpdx: String,
        val sourceRepository: String,
        val dependencies: Set<String> = emptySet(),
        val conflicts: Set<String> = emptySet(),
        val constraints: CompatibilityConstraints = CompatibilityConstraints(),
        /** Relative path below the component's private version directory. */
        val installSubdirectory: String,
        /** Relative rollback marker/directory name; never an arbitrary filesystem path. */
        val rollbackSlot: String = "previous"
    )

    enum class Type {
        WINE, PROTON, BOX64, BOX86, FEXCORE, FEX_UNIXLIBS, WOWBOX64,
        DXVK, VKD3D_PROTON, D8VK, D7VK, TURNIP, RESHADE, SHADER_PACK, AUDIO
    }

    enum class TargetAbi { ANDROID_ARM64_V8A, GUEST_X86_64, GUEST_X86, ARM64EC }
    enum class ArchiveFormat { ZIP, TAR_GZ, TAR_XZ, TAR_ZSTD, RAW }

    data class CompatibilityConstraints(
        val minimumNgrVersion: String? = null,
        val containerModes: Set<NgrEnvironmentProfile.ContainerMode> = emptySet(),
        val requiredComponents: Set<String> = emptySet(),
        val forbiddenComponents: Set<String> = emptySet(),
        val requiresWayland: Boolean = false,
        val requiresVulkan: Boolean = false
    )

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (schemaVersion != CURRENT_SCHEMA_VERSION) errors += "Unsupported catalog schema: $schemaVersion"
        if (!catalogId.matches(ID_PATTERN)) errors += "catalogId is invalid"
        if (revision.isBlank() || revision.length > 128) errors += "catalog revision is invalid"
        if (components.size > MAX_COMPONENTS) errors += "Catalog exceeds component limit"

        val byId = components.groupBy { it.id }
        byId.filterValues { it.size > 1 }.keys.forEach { errors += "Duplicate component ID: $it" }
        val ids = byId.keys
        components.forEach { component ->
            val prefix = "component ${component.id}: "
            if (!component.id.matches(ID_PATTERN)) errors += prefix + "invalid ID"
            if (component.version.isBlank() || component.version.length > 128) errors += prefix + "invalid version"
            if (component.upstreamCommit != null && !component.upstreamCommit.matches(COMMIT_PATTERN)) {
                errors += prefix + "invalid upstream commit"
            }
            if (component.targetAbis.isEmpty()) errors += prefix + "at least one target ABI is required"
            if (!isHttpsUrl(component.downloadUrl)) errors += prefix + "download URL must be HTTPS without credentials"
            if (!component.sha256.matches(SHA256_PATTERN)) errors += prefix + "SHA-256 must contain 64 hexadecimal characters"
            if (!component.licenseSpdx.matches(SPDX_PATTERN)) errors += prefix + "license must be an SPDX identifier or LicenseRef"
            if (!isHttpsUrl(component.sourceRepository)) errors += prefix + "source repository must be HTTPS"
            if (!isSafeRelativePath(component.installSubdirectory)) errors += prefix + "unsafe install subdirectory"
            if (!component.rollbackSlot.matches(ID_PATTERN)) errors += prefix + "unsafe rollback slot"
            if (component.dependencies.any { !it.matches(ID_PATTERN) }) errors += prefix + "invalid dependency ID"
            if (component.conflicts.any { !it.matches(ID_PATTERN) }) errors += prefix + "invalid conflict ID"
            if (component.dependencies.contains(component.id)) errors += prefix + "component cannot depend on itself"
            if (component.conflicts.contains(component.id)) errors += prefix + "component cannot conflict with itself"
            if (component.dependencies.any { it !in ids }) errors += prefix + "dependency references an unknown component"
            if (component.conflicts.any { it !in ids }) errors += prefix + "conflict references an unknown component"
            if (component.constraints.requiredComponents.any { it !in ids }) errors += prefix + "constraint references an unknown required component"
            if (component.constraints.forbiddenComponents.any { it !in ids }) errors += prefix + "constraint references an unknown forbidden component"
            if (component.dependencies.any { it in component.conflicts }) errors += prefix + "dependency is also declared as a conflict"
        }
        return errors
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_COMPONENTS = 2048
        private val ID_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$")
        private val COMMIT_PATTERN = Regex("^(?:[a-fA-F0-9]{7,64})$")
        private val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
        private val SPDX_PATTERN = Regex("^(?:[A-Za-z0-9][A-Za-z0-9.+-]{0,63}|LicenseRef-[A-Za-z0-9.-]{1,64})$")
        
        private fun isHttpsUrl(value: String): Boolean = try {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.port in -1..65535 && !value.contains(0.toChar())
        } catch (_: Exception) {
            false
        }

        private fun isSafeRelativePath(value: String): Boolean {
            if (value.isBlank() || value.length > 512 || value.startsWith("/") ||
            if (value.isBlank() || value.length > 512 || value.startsWith("/") ||
                value.startsWith("\\") || value.contains('\\') || value.contains(':') ||
                value.contains(0.toChar())) return false
            return segments.all { it.isNotBlank() && it != "." && it != ".." }
        }
    }
}
