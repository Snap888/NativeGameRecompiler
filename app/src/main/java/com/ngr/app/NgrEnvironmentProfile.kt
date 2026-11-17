package com.ngr.app

/**
 * Declarative, versioned configuration for one Windows compatibility environment.
 *
 * This model records intent only. It does not download, install, load or execute components.
 * Component IDs must resolve through a verified catalog before a runtime can consume them.
 */
data class NgrEnvironmentProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profileId: String,
    val displayName: String,
    val containerMode: ContainerMode,
    val wineLayerId: String? = null,
    val box64ComponentId: String? = null,
    val box86ComponentId: String? = null,
    val cpuTranslator: CpuTranslator,
    val box64Preset: Box64Preset = Box64Preset.BALANCED,
    val fexCoreComponentId: String? = null,
    val fexUnixLibsComponentId: String? = null,
    val wowBox64ComponentId: String? = null,
    val dxvkComponentId: String? = null,
    val vkd3dComponentId: String? = null,
    val graphicsProvider: GraphicsProvider = GraphicsProvider.SYSTEM_VULKAN,
    val turnipComponentId: String? = null,
    val displayBackend: DisplayBackend = DisplayBackend.X11,
    val waylandWineSupported: Boolean = false,
    val waylandTurnipSupported: Boolean = false,
    val reshade: ReShadeProfile = ReShadeProfile(),
    val audioBackend: AudioBackend = AudioBackend.AUTO,
    val desktopWidth: Int = 1280,
    val desktopHeight: Int = 720,
    val dpi: Int = 96,
    val environmentVariables: Map<String, String> = emptyMap(),
    val gameOverrides: Map<String, NgrGameEnvironmentOverride> = emptyMap(),
    /** Exact component versions pinned by ID for reproducible environments. */
    val componentVersions: Map<String, String> = emptyMap()
) {
    enum class ContainerMode { STANDARD_X86_64, ARM64EC }
    enum class CpuTranslator { BOX64, FEXCORE }
    enum class Box64Preset { COMPATIBILITY, BALANCED, PERFORMANCE }
    enum class GraphicsProvider { SYSTEM_VULKAN, TURNIP }
    enum class DisplayBackend { X11, WAYLAND }
    enum class AudioBackend { AUTO, DIRECT_AUDIO, OBOE }

    data class ReShadeProfile(
        val enabled: Boolean = false,
        val componentId: String? = null,
        val presetPath: String? = null,
        val shaderPackageIds: List<String> = emptyList()
    )

    data class NgrGameEnvironmentOverride(
        val wineLayerId: String? = null,
        val dxvkComponentId: String? = null,
        val vkd3dComponentId: String? = null,
        val turnipComponentId: String? = null,
        val displayBackend: DisplayBackend? = null,
        val environmentVariables: Map<String, String> = emptyMap()
    )

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (schemaVersion != CURRENT_SCHEMA_VERSION) errors += "Unsupported environment profile schema: $schemaVersion"
        if (!profileId.matches(ID_PATTERN)) errors += "profileId is not a safe identifier"
        if (displayName.isBlank() || displayName.length > 80) errors += "displayName must contain 1..80 characters"

        when (containerMode) {
            ContainerMode.STANDARD_X86_64 -> {
                if (cpuTranslator != CpuTranslator.BOX64) errors += "STANDARD_X86_64 requires Box64"
                if (fexCoreComponentId != null || fexUnixLibsComponentId != null) errors += "FEXCore/FEX UnixLibs are only valid for ARM64EC profiles"
                if (wowBox64ComponentId != null) errors += "WOWBox64 is only valid for ARM64EC profiles"
            }
            ContainerMode.ARM64EC -> {
                if (cpuTranslator != CpuTranslator.FEXCORE) errors += "ARM64EC requires FEXCore"
                if (fexCoreComponentId.isNullOrBlank()) errors += "ARM64EC requires a selected FEXCore component"
            }
        }

        if (wineLayerId != null && !wineLayerId.matches(ID_PATTERN)) errors += "wineLayerId is invalid"
        listOf(
            "box64ComponentId" to box64ComponentId,
            "box86ComponentId" to box86ComponentId,
            "fexCoreComponentId" to fexCoreComponentId,
            "fexUnixLibsComponentId" to fexUnixLibsComponentId,
            "wowBox64ComponentId" to wowBox64ComponentId,
            "dxvkComponentId" to dxvkComponentId,
            "vkd3dComponentId" to vkd3dComponentId,
            "turnipComponentId" to turnipComponentId,
            "ReShade componentId" to reshade.componentId
        ).forEach { (name, id) ->
            if (id != null && !id.matches(ID_PATTERN)) errors += "$name is invalid"
        }

        if (graphicsProvider == GraphicsProvider.TURNIP && turnipComponentId.isNullOrBlank()) {
            errors += "TURNIP provider requires a selected Turnip component"
        }
        if (displayBackend == DisplayBackend.WAYLAND &&
            (!waylandWineSupported || !waylandTurnipSupported || graphicsProvider != GraphicsProvider.TURNIP)) {
            errors += "Wayland requires a compatible Wine driver and Wayland-capable Turnip provider"
        }
        if (reshade.enabled && reshade.componentId.isNullOrBlank()) {
            errors += "ReShade cannot be enabled without a selected component"
        }
        if (reshade.presetPath != null && !reshade.enabled) {
            errors += "A ReShade preset requires ReShade to be enabled"
        }
        if (reshade.presetPath != null && !isSafeRelativePath(reshade.presetPath)) {
            errors += "ReShade preset path must be a safe relative path"
        }
        if (reshade.shaderPackageIds.any { !it.matches(ID_PATTERN) }) {
            errors += "One or more shader package IDs are invalid"
        }
        if (desktopWidth !in 320..7680 || desktopHeight !in 320..7680) {
            errors += "Desktop dimensions must be within 320..7680"
        }
        if (dpi !in 48..480) errors += "DPI must be within 48..480"

        componentVersions.forEach { (componentId, version) ->
            if (!componentId.matches(ID_PATTERN)) errors += "componentVersions contains an invalid component ID: $componentId"
            if (!version.matches(VERSION_PATTERN)) errors += "componentVersions contains an invalid version for $componentId"
        }
        validateEnvironment(environmentVariables, "environmentVariables", errors)
        gameOverrides.forEach { (gameId, override) ->
            if (!gameId.matches(ID_PATTERN)) errors += "Invalid game override key: $gameId"
            if (override.wineLayerId != null && !override.wineLayerId.matches(ID_PATTERN)) {
                errors += "Invalid Wine layer in override for $gameId"
            }
            if (override.displayBackend == DisplayBackend.WAYLAND &&
                (!waylandWineSupported || !waylandTurnipSupported || graphicsProvider != GraphicsProvider.TURNIP)) {
                errors += "Wayland override for $gameId requires a compatible Wine and Turnip profile"
            }
            listOf(override.dxvkComponentId, override.vkd3dComponentId, override.turnipComponentId)
                .filterNotNull().filterNot { it.matches(ID_PATTERN) }
                .forEach { errors += "Invalid component ID in override for $gameId" }
            validateEnvironment(override.environmentVariables, "gameOverrides.$gameId", errors)
        }
        return errors
    }


    /** Returns structural validation plus the component references required for a launchable Wine profile. */
    fun validateLaunchReady(): List<String> {
        val errors = validate().toMutableList()
        if (wineLayerId.isNullOrBlank()) errors += "A Wine/Proton layer must be selected"
        when (containerMode) {
            ContainerMode.STANDARD_X86_64 -> if (box64ComponentId.isNullOrBlank()) errors += "A Box64 component must be selected"
            ContainerMode.ARM64EC -> {
                if (fexCoreComponentId.isNullOrBlank()) errors += "An FEXCore component must be selected"
                if (fexUnixLibsComponentId.isNullOrBlank()) errors += "FEX UnixLibs must be selected for ARM64EC"
            }
        }
        return errors.distinct()
    }
    private fun validateEnvironment(values: Map<String, String>, scope: String, errors: MutableList<String>) {
        if (values.size > 128) errors += "$scope contains more than 128 variables"
        values.forEach { (key, value) ->
            if (!key.matches(ENV_KEY_PATTERN)) errors += "$scope contains an invalid variable name"
            if (key.startsWith("LD_") || key.startsWith("NGR_") ||
                key == "PATH" || key == "HOME" || key == "PREFIX") {
                errors += "$scope attempts to override a runtime-managed variable: $key"
            }
            if (value.length > 4096 || value.contains('\u0000')) errors += "$scope contains an invalid value for $key"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private val ID_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$")
        private val VERSION_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9._+-]{0,127}$")
        private val ENV_KEY_PATTERN = Regex("^[A-Z_][A-Z0-9_]{0,127}$")

        private fun isSafeRelativePath(value: String): Boolean =
            value.isNotBlank() && value.length <= 512 && !value.startsWith("/") &&
                !value.startsWith("\\") && !value.contains('\\') && !value.contains(':') &&
                !value.contains(0.toChar()) &&
                value.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    }
}
