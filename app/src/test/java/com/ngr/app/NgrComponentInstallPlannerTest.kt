package com.ngr.app

import org.junit.Assert.*
import org.junit.Test

class NgrComponentInstallPlannerTest {
    private fun component(
        id: String,
        dependencies: Set<String> = emptySet(),
        conflicts: Set<String> = emptySet(),
        forbidden: Set<String> = emptySet()
    ) = NgrComponentCatalog.Component(
        id = id,
        type = NgrComponentCatalog.Type.BOX64,
        version = "1.0",
        targetAbis = setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/$id.zip",
        sha256 = "a".repeat(64),
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/example/$id",
        dependencies = dependencies,
        conflicts = conflicts,
        constraints = NgrComponentCatalog.CompatibilityConstraints(forbiddenComponents = forbidden),
        installSubdirectory = "runtime/$id"
    )

    private fun catalog(vararg components: NgrComponentCatalog.Component) = NgrComponentCatalog(
        catalogId = "test-catalog",
        revision = "r1",
        components = components.toList()
    )

    @Test fun resolvesTransitiveDependenciesInDependencyFirstOrder() {
        val value = catalog(
            component("wine", setOf("box64")),
            component("box64", setOf("libc")),
            component("libc")
        )
        val plan = NgrComponentInstallPlanner.resolve(value, setOf("wine"))
        assertTrue(plan.errors.toString(), plan.valid)
        assertEquals(listOf("libc", "box64", "wine"), plan.installOrder.map { it.id })
    }

    @Test fun skipsAlreadyInstalledDependencies() {
        val value = catalog(component("wine", setOf("box64")), component("box64"))
        val plan = NgrComponentInstallPlanner.resolve(value, setOf("wine"), setOf("box64"))
        assertTrue(plan.errors.toString(), plan.valid)
        assertEquals(listOf("wine"), plan.installOrder.map { it.id })
        assertEquals(setOf("box64"), plan.alreadyInstalled)
    }

    @Test fun rejectsUnknownSelectionsAndConflicts() {
        val unknown = NgrComponentInstallPlanner.resolve(catalog(component("wine")), setOf("missing"))
        assertFalse(unknown.valid)
        assertTrue(unknown.errors.any { it.contains("not present") })

        val conflict = NgrComponentInstallPlanner.resolve(
            catalog(component("wine", conflicts = setOf("box64")), component("box64")),
            setOf("wine", "box64")
        )
        assertFalse(conflict.valid)
        assertTrue(conflict.errors.any { it.contains("conflicts") })
    }

    @Test fun rejectsForbiddenInstalledComponent() {
        val value = catalog(component("wine", forbidden = setOf("box64")), component("box64"))
        val plan = NgrComponentInstallPlanner.resolve(value, setOf("wine"), setOf("box64"))
        assertFalse(plan.valid)
        assertTrue(plan.errors.any { it.contains("forbids") })
    }
}
