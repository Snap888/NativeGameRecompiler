package com.ngr.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class NgrRecompilerProjectStoreTest {
    @Test fun persistsAndUpdatesDetectedProject() {
        val root = Files.createTempDirectory("ngr-projects").toFile()
        try {
            val store = NgrRecompilerProjectStore(root)
            val first = NgrRecompilerProject(
                projectId = "xbox360-123", displayName = "Dragon Age source.iso", systemId = "XBOX_360",
                sourceUri = "content://provider/document/1", sourceExtension = "iso",
                status = NgrRecompilerProject.Status.METADATA_REQUIRED, detail = "Profile validation pending"
            )
            assertTrue(store.upsert(first).isSuccess)
            assertEquals(1, store.loadAll().projects.size)
            val updated = first.copy(status = NgrRecompilerProject.Status.BACKEND_UNAVAILABLE, detail = "Runtime integration pending")
            assertTrue(store.upsert(updated).isSuccess)
            val loaded = store.loadAll()
            assertTrue(loaded.errors.isEmpty())
            assertEquals(1, loaded.projects.size)
            assertEquals(NgrRecompilerProject.Status.BACKEND_UNAVAILABLE, loaded.projects.single().status)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsUnsafeOrUnsupportedRecords() {
        val root = Files.createTempDirectory("ngr-projects-invalid").toFile()
        try {
            val invalid = NgrRecompilerProject(
                projectId = "../bad", displayName = "bad", systemId = "PS5", sourceUri = "content://x",
                sourceExtension = "iso", status = NgrRecompilerProject.Status.DETECTED, detail = ""
            )
            assertFalse(NgrRecompilerProjectStore(root).upsert(invalid).isSuccess)
        } finally { root.deleteRecursively() }
    }
}
