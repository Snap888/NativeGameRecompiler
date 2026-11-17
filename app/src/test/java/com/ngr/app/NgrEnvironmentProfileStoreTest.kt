package com.ngr.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrEnvironmentProfileStoreTest {
    private fun profile(id: String = "desktop", displayName: String = "Desktop") = NgrEnvironmentProfile(
        profileId = id,
        displayName = displayName,
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64
    )

    @Test fun savesLoadsAndAtomicallyReplacesProfiles() {
        val root = Files.createTempDirectory("ngr-profile-store-").toFile()
        val store = NgrEnvironmentProfileStore(root)
        try {
            assertTrue(store.save(profile()).success)
            assertEquals(profile(), store.get("desktop"))
            assertTrue(store.save(profile(displayName = "Updated desktop")).success)
            assertEquals("Updated desktop", store.get("desktop")?.displayName)
            assertEquals(1, store.loadAll().profiles.size)
            assertTrue(store.loadAll().errors.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsInvalidProfilesAndUnsafeIds() {
        val root = Files.createTempDirectory("ngr-profile-store-").toFile()
        val store = NgrEnvironmentProfileStore(root)
        try {
            val invalid = profile().copy(environmentVariables = mapOf("LD_PRELOAD" to "evil.so"))
            assertFalse(store.save(invalid).success)
            assertFalse(store.delete("../desktop").success)
            assertTrue(store.get("../desktop") == null)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun reportsCorruptFilesWithoutLoadingThem() {
        val root = Files.createTempDirectory("ngr-profile-store-").toFile()
        val store = NgrEnvironmentProfileStore(root)
        try {
            val directory = File(root, "environment-profiles")
            directory.mkdirs()
            File(directory, "broken.json").writeText("{ not json")
            val result = store.loadAll()
            assertTrue(result.profiles.isEmpty())
            assertEquals(1, result.errors.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun deletesProfileAndReturnsEmptyStore() {
        val root = Files.createTempDirectory("ngr-profile-store-").toFile()
        val store = NgrEnvironmentProfileStore(root)
        try {
            assertTrue(store.save(profile()).success)
            assertTrue(store.delete("desktop").success)
            assertTrue(store.loadAll().profiles.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
