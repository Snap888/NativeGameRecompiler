package com.ngr.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NgrEnvironmentReadinessTest {
    private fun component(id: String) = NgrComponentCatalog.Component(
        id = id,
        type = if (id == "wine") NgrComponentCatalog.Type.WINE else NgrComponentCatalog.Type.BOX64,
        version = "1.0.0",
        targetAbis = setOf(
            NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A,
            NgrComponentCatalog.TargetAbi.GUEST_X86_64
        ),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/$id.zip",
        sha256 = "a".repeat(64),
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/example/$id",
        installSubdirectory = "runtime/$id"
    )

    private fun profile(prefixId: String) = NgrEnvironmentProfile(
        profileId = "test-profile",
        displayName = "Test profile",
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        wineLayerId = "wine",
        box64ComponentId = "box64",
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64,
        winePrefixId = prefixId
    )

    private fun catalog() = NgrComponentCatalog(
        catalogId = "test",
        revision = "1",
        components = listOf(component("wine"), component("box64"))
    )

    private val capabilities = NgrComponentResolver.RuntimeCapabilities(
        vulkanAvailable = true,
        waylandAvailable = false
    )

    @Test fun reportsMissingPrefixAndUninstalledComponents() {
        val root = Files.createTempDirectory("ngr-readiness-").toFile()
        try {
            val readiness = NgrEnvironmentReadiness(
                NgrWinePrefixManager(root),
                NgrComponentStore(root)
            )
            val report = readiness.evaluate(profile("prefix-missing"), catalog(), capabilities)
            assertFalse(report.ready)
            assertNull(report.prefixState)
            assertTrue(report.missingComponents.containsAll(setOf("wine", "box64")))
            assertTrue(report.pendingInstallComponents.containsAll(setOf("wine", "box64")))
            assertTrue(report.errors.any { it.contains("prefix-missing") })
            assertTrue(report.errors.any { it.contains("still need installation") })
        } finally { root.deleteRecursively() }
    }

    @Test fun reportsPreparedPrefixAsNotLaunchReady() {
        val root = Files.createTempDirectory("ngr-readiness-").toFile()
        try {
            val prefixes = NgrWinePrefixManager(root)
            assertTrue(prefixes.create("prefix-prepared", "Prepared").success)
            val readiness = NgrEnvironmentReadiness(prefixes, NgrComponentStore(root))
            val report = readiness.evaluate(profile("prefix-prepared"), catalog(), capabilities)
            assertFalse(report.ready)
            assertEquals(NgrWinePrefixManager.State.PREPARED, report.prefixState)
            assertTrue(report.errors.any { it.contains("PREPARED") })
        } finally { root.deleteRecursively() }
    }
}
