package com.ngr.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NgrComponentCatalogTest {
    private fun component(
        id: String = "box64",
        dependencies: Set<String> = emptySet(),
        conflicts: Set<String> = emptySet()
    ) = NgrComponentCatalog.Component(
        id = id,
        type = NgrComponentCatalog.Type.BOX64,
        version = "0.3.2",
        upstreamCommit = "0123456789abcdef",
        targetAbis = setOf(
            NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A,
            NgrComponentCatalog.TargetAbi.GUEST_X86_64
        ),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.TAR_ZSTD,
        downloadUrl = "https://downloads.example.org/box64.tar.zst",
        sha256 = "a".repeat(64),
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/ptitSeb/box64",
        dependencies = dependencies,
        conflicts = conflicts,
        installSubdirectory = "runtime/box64"
    )

    @Test fun acceptsVerifiedComponentMetadataShape() {
        val catalog = NgrComponentCatalog(
            catalogId = "community",
            revision = "2026-11-18",
            components = listOf(component())
        )
        assertTrue(catalog.validate().toString(), catalog.validate().isEmpty())
    }

    @Test fun rejectsHttpUrlsAndMissingSha256() {
        val invalid = component().copy(
            downloadUrl = "http://downloads.example.org/box64.tar.zst",
            sha256 = "not-a-hash"
        )
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(invalid)
        ).validate()
        assertTrue(errors.any { it.contains("HTTPS") })
        assertTrue(errors.any { it.contains("SHA-256") })
    }

    @Test fun rejectsArchiveTraversalAndAbsoluteInstallPaths() {
        val traversal = component().copy(installSubdirectory = "runtime/../../outside")
        val absolute = component(id = "box64-2").copy(installSubdirectory = "/data/local/tmp")
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(traversal, absolute)
        ).validate()
        assertTrue(errors.count { it.contains("unsafe install subdirectory") } == 2)
    }

    @Test fun rejectsUnknownDependenciesAndConflicts() {
        val invalid = component(dependencies = setOf("missing"), conflicts = setOf("also-missing"))
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(invalid)
        ).validate()
        assertTrue(errors.any { it.contains("unknown component") })
    }

    @Test fun rejectsDuplicateIdsAndSelfDependency() {
        val first = component(dependencies = setOf("box64"))
        val duplicate = component().copy(version = "0.3.3")
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(first, duplicate)
        ).validate()
        assertTrue(errors.any { it.contains("Duplicate component ID") })
        assertTrue(errors.any { it.contains("cannot depend on itself") })
    }

    @Test fun rejectsCredentialBearingUrlsAndInvalidLicenseIdentifiers() {
        val invalid = component().copy(
            downloadUrl = "https://user:secret@downloads.example.org/archive",
            licenseSpdx = "MIT license text"
        )
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(invalid)
        ).validate()
        assertTrue(errors.any { it.contains("HTTPS") })
        assertTrue(errors.any { it.contains("SPDX") })
        assertFalse(errors.isEmpty())
    }

    @Test fun rejectsWindowsAndNulPathSeparators() {
        val invalid = component().copy(installSubdirectory = "runtime\\\\..\\\\outside")
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(invalid)
        ).validate()
        assertTrue(errors.any { it.contains("unsafe install subdirectory") })
    }

    @Test fun rejectsDependencyCycles() {
        val first = component(id = "box64", dependencies = setOf("dxvk"))
        val second = component(id = "dxvk", dependencies = setOf("box64")).copy(
            type = NgrComponentCatalog.Type.DXVK
        )
        val errors = NgrComponentCatalog(
            catalogId = "community",
            revision = "1",
            components = listOf(first, second)
        ).validate()
        assertTrue(errors.any { it.contains("dependency graph contains a cycle") })
    }
    @Test fun rejectsCyclesThroughRequiredComponentConstraints() {
        val first = component(id = "box64").copy(
            constraints = NgrComponentCatalog.CompatibilityConstraints(requiredComponents = setOf("dxvk"))
        )
        val second = component(id = "dxvk").copy(
            type = NgrComponentCatalog.Type.DXVK,
            constraints = NgrComponentCatalog.CompatibilityConstraints(requiredComponents = setOf("box64"))
        )
        val errors = NgrComponentCatalog(catalogId = "community", revision = "1", components = listOf(first, second)).validate()
        assertTrue(errors.any { it.contains("dependency graph contains a cycle") })
    }

}
