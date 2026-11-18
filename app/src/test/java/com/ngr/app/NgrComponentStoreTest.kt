package com.ngr.app

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentStoreTest {
    private fun component(version: String) = NgrComponentCatalog.Component(
        id = "box64",
        type = NgrComponentCatalog.Type.BOX64,
        version = version,
        targetAbis = setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/box64.zip",
        sha256 = "a".repeat(64),
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/ptitSeb/box64",
        installSubdirectory = "runtime/box64"
    )

    private fun staged(store: NgrComponentStore, marker: String): java.io.File {
        val dir = Files.createTempDirectory(store.stagingDirectory().toPath(), "component-").toFile()
        dir.resolve("runtime/box64").mkdirs()
        dir.resolve("runtime/box64/payload.txt").writeText(marker)
        return dir
    }

    @Test fun installsVersionsAndRollsBackAtomically() {
        val root = Files.createTempDirectory("ngr-component-store-").toFile()
        val store = NgrComponentStore(root)
        try {
            val first = store.installAndActivate(component("1.0.0"), staged(store, "one"))
            assertTrue(first.error ?: "first install failed", first.success)
            assertEquals("1.0.0", store.activeVersionDirectory("box64")?.name)

            val second = store.installAndActivate(component("2.0.0"), staged(store, "two"))
            assertTrue(second.error ?: "second install failed", second.success)
            assertEquals("2.0.0", store.activeVersionDirectory("box64")?.name)

            val rollback = store.rollback("box64")
            assertTrue(rollback.error ?: "rollback failed", rollback.success)
            assertEquals("1.0.0", store.activeVersionDirectory("box64")?.name)

            val secondRollback = store.rollback("box64")
            assertTrue(secondRollback.error ?: "second rollback failed", secondRollback.success)
            assertEquals("2.0.0", store.activeVersionDirectory("box64")?.name)
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun reactivatesPreviouslyStoredVersionWithoutReinstalling() {
        val root = Files.createTempDirectory("ngr-component-store-").toFile()
        val store = NgrComponentStore(root)
        try {
            assertTrue(store.installAndActivate(component("1.0.0"), staged(store, "one")).success)
            assertTrue(store.installAndActivate(component("2.0.0"), staged(store, "two")).success)
            assertTrue(store.rollback("box64").success)
            assertEquals("1.0.0", store.activeVersionDirectory("box64")?.name)

            val reactivated = store.activateStoredVersion(component("2.0.0"))
            assertTrue(reactivated.error ?: "stored version activation failed", reactivated.success)
            assertEquals("2.0.0", store.activeVersionDirectory("box64")?.name)
            assertEquals("two", store.activeComponentPath(component("2.0.0"))?.resolve("payload.txt")?.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesToRemoveCurrentOrRollbackVersions() {
        val root = Files.createTempDirectory("ngr-component-store-").toFile()
        val store = NgrComponentStore(root)
        try {
            assertTrue(store.installAndActivate(component("1.0.0"), staged(store, "one")).success)
            assertTrue(store.installAndActivate(component("2.0.0"), staged(store, "two")).success)
            assertFalse(store.removeVersion("box64", "2.0.0").success)
            assertFalse(store.removeVersion("box64", "1.0.0").success)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsStagingDirectoryOutsideManagedStagingRoot() {
        val root = Files.createTempDirectory("ngr-component-store-").toFile()
        val store = NgrComponentStore(root)
        val outside = Files.createTempDirectory("ngr-outside-stage-").toFile()
        outside.resolve("payload.txt").writeText("outside")
        try {
            val result = store.installAndActivate(component("1.0.0"), outside)
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("direct child"))
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun uninstallsComponentAndClearsActiveVersion() {
        val root = Files.createTempDirectory("ngr-component-store-").toFile()
        val store = NgrComponentStore(root)
        try {
            assertTrue(store.installAndActivate(component("1.0.0"), staged(store, "one")).success)
            assertTrue(store.uninstall("box64").success)
            assertEquals(null, store.activeVersionDirectory("box64"))
        } finally {
            root.deleteRecursively()
        }
    }
}
