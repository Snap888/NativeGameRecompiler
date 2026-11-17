package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentResolverTest {
    private val profile = NgrEnvironmentProfile(
        profileId = "test",
        displayName = "Test profile",
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        wineLayerId = "wine",
        box64ComponentId = "box64",
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64
    )
    private val capabilities = NgrComponentResolver.RuntimeCapabilities(
        vulkanAvailable = true,
        waylandAvailable = false
    )

    private fun component(
        id: String,
        dependencies: Set<String> = emptySet(),
        conflicts: Set<String> = emptySet(),
        targetAbis: Set<NgrComponentCatalog.TargetAbi> = setOf(
            NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A,
            NgrComponentCatalog.TargetAbi.GUEST_X86_64
        ),
        constraints: NgrComponentCatalog.CompatibilityConstraints =
            NgrComponentCatalog.CompatibilityConstraints()
    ) = NgrComponentCatalog.Component(
        id = id,
        type = NgrComponentCatalog.Type.WINE,
        version = "1.0.0",
        targetAbis = targetAbis,
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/$id.zip",
        sha256 = "a".repeat(64),
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/example/$id",
        dependencies = dependencies,
        conflicts = conflicts,
        constraints = constraints,
        installSubdirectory = "runtime/$id"
    )

    private fun catalog(vararg components: NgrComponentCatalog.Component) =
        NgrComponentCatalog(catalogId = "test", revision = "1", components = components.toList())

    @Test fun resolvesDependenciesBeforeRequestedComponent() {
        val plan = NgrComponentResolver.resolve(
            catalog(component("wine", dependencies = setOf("box64")), component("box64")),
            profile,
            setOf("wine"),
            capabilities = capabilities
        )
        assertTrue(plan.errors.joinToString(), plan.valid)
        assertEquals(listOf("box64", "wine"), plan.installOrder.map { it.id })
    }

    @Test fun skipsAlreadyInstalledDependencies() {
        val plan = NgrComponentResolver.resolve(
            catalog(component("wine", dependencies = setOf("box64")), component("box64")),
            profile,
            setOf("wine"),
            installedIds = setOf("box64"),
            capabilities = capabilities
        )
        assertTrue(plan.errors.joinToString(), plan.valid)
        assertEquals(listOf("wine"), plan.installOrder.map { it.id })
        assertEquals(setOf("box64"), plan.alreadyInstalled)
    }

    @Test fun rejectsConflictsAcrossRequestedComponents() {
        val plan = NgrComponentResolver.resolve(
            catalog(component("wine", conflicts = setOf("dxvk")), component("dxvk"), component("box64")),
            profile,
            setOf("wine", "dxvk"),
            capabilities = capabilities
        )
        assertFalse(plan.valid)
        assertTrue(plan.errors.any { it.contains("conflicts") })
    }

    @Test fun rejectsIncompatibleArchitectureAndMissingVulkan() {
        val incompatible = component(
            "arm64ec-only",
            targetAbis = setOf(NgrComponentCatalog.TargetAbi.ARM64EC)
        )
        val plan = NgrComponentResolver.resolve(
            catalog(component("wine"), component("box64"), incompatible),
            profile,
            setOf("arm64ec-only"),
            capabilities = capabilities
        )
        assertFalse(plan.valid)
        assertTrue(plan.errors.any { it.contains("incompatible with profile architecture") })

        val noVulkan = NgrComponentResolver.resolve(
            catalog(component("wine"), component("box64")),
            profile,
            setOf("wine"),
            capabilities = capabilities.copy(vulkanAvailable = false)
        )
        assertFalse(noVulkan.valid)
        assertTrue(noVulkan.errors.any { it.contains("Vulkan") })
    }

    @Test fun enforcesWaylandAndContainerModeConstraints() {
        val waylandOnly = component(
            "wayland-layer",
            constraints = NgrComponentCatalog.CompatibilityConstraints(requiresWayland = true)
        )
        val plan = NgrComponentResolver.resolve(
            catalog(component("wine"), component("box64"), waylandOnly),
            profile,
            setOf("wayland-layer"),
            capabilities = capabilities
        )
        assertFalse(plan.valid)
        assertTrue(plan.errors.any { it.contains("requires Wayland") })
    }
    @Test fun enforcesPinnedVersionsForCatalogAndInstalledComponents() {
        val pinnedProfile = profile.copy(componentVersions = mapOf("wine" to "1.0.0"))
        val catalogMismatch = NgrComponentResolver.resolve(
            catalog(component("wine").copy(version = "2.0.0"), component("box64")),
            pinnedProfile,
            setOf("wine"),
            capabilities = capabilities
        )
        assertFalse(catalogMismatch.valid)
        assertTrue(catalogMismatch.errors.any { it.contains("does not match pinned version") })

        val installedMismatch = NgrComponentResolver.resolve(
            catalog(component("wine"), component("box64")),
            pinnedProfile,
            setOf("wine"),
            installedIds = setOf("wine"),
            capabilities = capabilities,
            installedVersions = mapOf("wine" to "0.9.0")
        )
        assertFalse(installedMismatch.valid)
        assertTrue(installedMismatch.errors.any { it.contains("does not match pinned version") })

        val installedWithoutMetadata = NgrComponentResolver.resolve(
            catalog(component("wine"), component("box64")),
            pinnedProfile,
            setOf("wine"),
            installedIds = setOf("wine"),
            capabilities = capabilities
        )
        assertFalse(installedWithoutMetadata.valid)
        assertTrue(installedWithoutMetadata.errors.any { it.contains("verified installed version metadata") })
    }
}
