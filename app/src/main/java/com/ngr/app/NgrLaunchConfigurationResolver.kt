package com.ngr.app

/**
 * Resolves a saved game shortcut and environment profile into a deterministic launch
 * configuration. This stage does not start processes, resolve host paths or activate binaries.
 */
object NgrLaunchConfigurationResolver {
    data class Configuration(
        val shortcutId: String,
        val packageId: String,
        val profileId: String,
        val launchMode: NgrGameShortcut.LaunchMode,
        val executablePath: String,
        val workingDirectory: String?,
        val arguments: List<String>,
        val wineLayerId: String?,
        val dxvkComponentId: String?,
        val vkd3dComponentId: String?,
        val turnipComponentId: String?,
        val graphicsProvider: NgrEnvironmentProfile.GraphicsProvider,
        val displayBackend: NgrEnvironmentProfile.DisplayBackend,
        val environment: Map<String, String>,
        val requiresWineCompatibilityRuntime: Boolean
    )

    data class Result(
        val configuration: Configuration? = null,
        val errors: List<String> = emptyList()
    ) {
        val valid: Boolean get() = configuration != null && errors.isEmpty()
    }

    fun resolve(
        shortcut: NgrGameShortcut,
        profile: NgrEnvironmentProfile
    ): Result {
        val errors = mutableListOf<String>()
        errors += shortcut.validate()
        errors += profile.validate()
        if (shortcut.environmentProfileId != profile.profileId) {
            errors += "Shortcut references a different environment profile"
        }

        val requiresWine = shortcut.launchMode == NgrGameShortcut.LaunchMode.WINE_COMPATIBILITY
        if (requiresWine) errors += profile.validateLaunchReady()

        val gameOverride = profile.gameOverrides[shortcut.packageId]
        val effectiveWine = gameOverride?.wineLayerId ?: profile.wineLayerId
        val effectiveDxvk = gameOverride?.dxvkComponentId ?: profile.dxvkComponentId
        val effectiveVkd3d = gameOverride?.vkd3dComponentId ?: profile.vkd3dComponentId
        val effectiveTurnip = gameOverride?.turnipComponentId ?: profile.turnipComponentId
        val effectiveDisplay = gameOverride?.displayBackend ?: profile.displayBackend
        val effectiveProvider = if (effectiveTurnip != null) {
            NgrEnvironmentProfile.GraphicsProvider.TURNIP
        } else {
            profile.graphicsProvider
        }

        if (effectiveDisplay == NgrEnvironmentProfile.DisplayBackend.WAYLAND &&
            (!profile.waylandWineSupported || !profile.waylandTurnipSupported ||
                effectiveProvider != NgrEnvironmentProfile.GraphicsProvider.TURNIP ||
                effectiveTurnip.isNullOrBlank())) {
            errors += "Wayland override requires verified Wine Wayland support and a Wayland-capable Turnip component"
        }
        if (requiresWine && effectiveWine.isNullOrBlank()) {
            errors += "No Wine/Proton layer is selected for this game"
        }

        val mergedEnvironment = sortedMapOf<String, String>()
        mergedEnvironment.putAll(profile.environmentVariables)
        gameOverride?.environmentVariables?.let(mergedEnvironment::putAll)
        mergedEnvironment.putAll(shortcut.environmentOverrides)
        mergedEnvironment.forEach { (key, value) ->
            if (NgrEnvironmentVariablePolicy.isRuntimeManaged(key)) {
                errors += "Resolved environment attempts to override runtime-managed variable: $key"
            }
            if (key.length > 128 || value.length > 4096 || value.contains('\u0000')) {
                errors += "Resolved environment contains an invalid entry: $key"
            }
        }

        if (errors.isNotEmpty()) return Result(errors = errors.distinct())

        val workingDirectory = shortcut.workingDirectory ?: guestParent(shortcut.executablePath)
        return Result(
            configuration = Configuration(
                shortcutId = shortcut.shortcutId,
                packageId = shortcut.packageId,
                profileId = profile.profileId,
                launchMode = shortcut.launchMode,
                executablePath = shortcut.executablePath,
                workingDirectory = workingDirectory,
                arguments = shortcut.arguments.toList(),
                wineLayerId = effectiveWine,
                dxvkComponentId = effectiveDxvk,
                vkd3dComponentId = effectiveVkd3d,
                turnipComponentId = effectiveTurnip,
                graphicsProvider = effectiveProvider,
                displayBackend = effectiveDisplay,
                environment = mergedEnvironment.toMap(),
                requiresWineCompatibilityRuntime = requiresWine
            )
        )
    }

    private fun guestParent(path: String): String? {
        val normalized = path.replace('\\', '/')
        val separator = normalized.lastIndexOf('/')
        if (separator < 0) return null
        if (separator == 2 && normalized.length >= 3 && normalized[1] == ':') return normalized.substring(0, 3)
        return normalized.substring(0, separator).ifEmpty { null }
    }
}
