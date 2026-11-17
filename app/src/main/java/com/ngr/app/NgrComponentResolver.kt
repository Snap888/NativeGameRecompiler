package com.ngr.app

/**
 * Resolves profile-selected components into a dependency-first install plan.
 * It performs no network, filesystem or activation operations.
 */
object NgrComponentResolver {
    data class RuntimeCapabilities(
        val vulkanAvailable: Boolean,
        val waylandAvailable: Boolean
    )

    data class Plan(
        val valid: Boolean,
        val installOrder: List<NgrComponentCatalog.Component> = emptyList(),
        val alreadyInstalled: Set<String> = emptySet(),
        val errors: List<String> = emptyList()
    )

    fun resolve(
        catalog: NgrComponentCatalog,
        profile: NgrEnvironmentProfile,
        requestedIds: Set<String>,
        installedIds: Set<String> = emptySet(),
        capabilities: RuntimeCapabilities
    ): Plan {
        val errors = mutableListOf<String>()
        errors += catalog.validate()
        errors += profile.validateLaunchReady()
        if (requestedIds.isEmpty() && profileSelectedIds(profile).isEmpty()) {
            errors += "No components were selected"
        }
        if (!capabilities.vulkanAvailable) {
            errors += "Selected environment requires Vulkan support"
        }
        if (profile.displayBackend == NgrEnvironmentProfile.DisplayBackend.WAYLAND && !capabilities.waylandAvailable) {
            errors += "Wayland is not available on this runtime"
        }
        if (errors.isNotEmpty()) return Plan(false, errors = errors.distinct())

        val byId = catalog.components.associateBy { it.id }
        val compatibleAbis = when (profile.containerMode) {
            NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64 ->
                setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A, NgrComponentCatalog.TargetAbi.GUEST_X86_64)
            NgrEnvironmentProfile.ContainerMode.ARM64EC ->
                setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A, NgrComponentCatalog.TargetAbi.ARM64EC)
        }
        val selected = linkedSetOf<String>()
        val alreadyInstalled = linkedSetOf<String>()
        val visiting = mutableSetOf<String>()
        val resolved = mutableSetOf<String>()

        fun include(id: String) {
            if (id in installedIds) {
                alreadyInstalled.add(id)
                return
            }
            if (id in resolved || errors.isNotEmpty()) return
            val component = byId[id]
            if (component == null) {
                errors += "Selected component is not present in catalog: $id"
                return
            }
            if (!visiting.add(id)) {
                errors += "Component dependency cycle encountered while resolving: $id"
                return
            }
            if (component.targetAbis.none { it in compatibleAbis }) {
                errors += "Component $id is incompatible with profile architecture ${profile.containerMode}"
            }
            if (component.constraints.containerModes.isNotEmpty() &&
                profile.containerMode !in component.constraints.containerModes) {
                errors += "Component $id does not support ${profile.containerMode}"
            }
            if (component.constraints.requiresWayland &&
                profile.displayBackend != NgrEnvironmentProfile.DisplayBackend.WAYLAND) {
                errors += "Component $id requires Wayland"
            }
            if (component.constraints.requiresVulkan && !capabilities.vulkanAvailable) {
                errors += "Component $id requires Vulkan support"
            }
            component.dependencies.forEach(::include)
            component.constraints.requiredComponents.forEach(::include)
            component.constraints.forbiddenComponents.intersect(selected + installedIds).forEach {
                errors += "Component $id forbids selected component $it"
            }
            visiting.remove(id)
            resolved.add(id)
            selected.add(id)
        }

        (requestedIds + profileSelectedIds(profile)).forEach(::include)
        if (errors.isNotEmpty()) return Plan(false, errors = errors.distinct())

        val allSelected = selected + installedIds
        allSelected.forEach { id ->
            val component = byId[id] ?: return@forEach
            val conflicts = component.conflicts.intersect(allSelected - id)
            conflicts.forEach { errors += "Component $id conflicts with $it" }
            component.constraints.forbiddenComponents.intersect(allSelected - id).forEach {
                errors += "Component $id forbids $it"
            }
        }
        if (errors.isNotEmpty()) return Plan(false, errors = errors.distinct())

        return Plan(
            valid = true,
            installOrder = selected.mapNotNull(byId::get),
            alreadyInstalled = alreadyInstalled
        )
    }

    private fun profileSelectedIds(profile: NgrEnvironmentProfile): Set<String> = buildSet {
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
