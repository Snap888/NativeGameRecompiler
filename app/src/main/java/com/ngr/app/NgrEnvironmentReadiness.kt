package com.ngr.app

/**
 * Combines declarative profile validation with the actual private prefix and component stores.
 * This is a readiness report only: it does not load libraries or launch Wine.
 */
class NgrEnvironmentReadiness(
    private val prefixes: NgrWinePrefixManager,
    private val components: NgrComponentStore
) {
    data class Report(
        val ready: Boolean,
        val prefixState: NgrWinePrefixManager.State?,
        val installedComponents: Set<String>,
        val missingComponents: Set<String>,
        val errors: List<String>
    )

    fun evaluate(
        profile: NgrEnvironmentProfile,
        catalog: NgrComponentCatalog,
        capabilities: NgrComponentResolver.RuntimeCapabilities
    ): Report {
        val errors = profile.validateLaunchReady().toMutableList()
        var prefixState: NgrWinePrefixManager.State? = null
        val prefixId = profile.winePrefixId
        if (prefixId.isNullOrBlank()) {
            errors += "No Wine prefix is assigned to this environment"
        } else {
            val prefix = prefixes.inspect(prefixId)
            if (prefix == null) errors += "Assigned Wine prefix '${prefixId}' is missing or unsafe"
            else {
                prefixState = prefix.state
                if (prefix.state != NgrWinePrefixManager.State.READY) {
                    errors += "Wine prefix '${prefixId}' is ${prefix.state}; initialize it with a compatible Wine runtime first"
                }
            }
        }

        val selectedIds = selectedComponentIds(profile)
        val installed = linkedSetOf<String>()
        val missing = linkedSetOf<String>()
        selectedIds.forEach { id ->
            val directory = components.activeVersionDirectory(id)
            if (directory == null) missing += id else installed += id
        }
        if (missing.isNotEmpty()) errors += "Required runtime components are not installed: ${missing.sorted().joinToString()}"
        val versions = installed.associateWith { id -> components.activeVersionDirectory(id)?.name.orEmpty() }
        val resolution = NgrComponentResolver.resolve(
            catalog = catalog,
            profile = profile,
            requestedIds = emptySet(),
            installedIds = installed,
            capabilities = capabilities,
            installedVersions = versions
        )
        errors += resolution.errors
        return Report(
            ready = errors.isEmpty() && resolution.valid && missing.isEmpty() && prefixState == NgrWinePrefixManager.State.READY,
            prefixState = prefixState,
            installedComponents = installed,
            missingComponents = missing,
            errors = errors.distinct()
        )
    }

    private fun selectedComponentIds(profile: NgrEnvironmentProfile): Set<String> = buildSet {
        profile.wineLayerId?.let(::add)
        profile.box64ComponentId?.let(::add)
        profile.box86ComponentId?.let(::add)
        profile.fexCoreComponentId?.let(::add)
        profile.fexUnixLibsComponentId?.let(::add)
        profile.wowBox64ComponentId?.let(::add)
        profile.dxvkComponentId?.let(::add)
        profile.vkd3dComponentId?.let(::add)
        profile.turnipComponentId?.let(::add)
        profile.reshade.componentId?.let(::add)
        addAll(profile.reshade.shaderPackageIds)
    }
}
