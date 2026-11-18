package com.ngr.app

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NgrComponentCatalogStoreTest {
    private fun catalogJson(id: String = "local-catalog", revision: String = "r1") = """
        {
          "schemaVersion": 1,
          "catalogId": "$id",
          "revision": "$revision",
          "components": [{
            "id": "box64",
            "type": "BOX64",
            "version": "0.3.2",
            "targetAbis": ["ANDROID_ARM64_V8A", "GUEST_X86_64"],
            "archiveFormat": "ZIP",
            "downloadUrl": "https://example.org/box64.zip",
            "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "licenseSpdx": "MIT",
            "sourceRepository": "https://github.com/ptitSeb/box64",
            "dependencies": [],
            "conflicts": [],
            "constraints": {},
            "installSubdirectory": "runtime/box64"
          }]
        }
    """.trimIndent()

    @Test fun savesLoadsReplacesAndDeletesValidatedCatalog() {
        val root = Files.createTempDirectory("ngr-catalog-store-").toFile()
        try {
            val store = NgrComponentCatalogStore(root)
            assertTrue(store.save(catalogJson()).success)
            assertEquals("r1", store.get("local-catalog")?.catalog?.revision)
            assertTrue(store.save(catalogJson(revision = "r2")).success)
            assertEquals("r2", store.get("local-catalog")?.catalog?.revision)
            assertEquals(1, store.loadAll().entries.size)
            assertTrue(store.loadAll().errors.isEmpty())
            assertTrue(store.delete("local-catalog").success)
            assertTrue(store.loadAll().entries.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsInvalidCatalogAndUnknownFields() {
        val root = Files.createTempDirectory("ngr-catalog-store-").toFile()
        try {
            val store = NgrComponentCatalogStore(root)
            assertFalse(store.save("""{"schemaVersion":1,"catalogId":"bad","revision":"r1","components":[],"ignored":true}""").success)
            assertFalse(store.save(catalogJson(id = "../escape")).success)
            assertNull(store.get("../escape"))
            assertFalse(store.delete("../escape").success)
        } finally { root.deleteRecursively() }
    }

    @Test fun reportsCorruptCatalogFilesWithoutLoadingThem() {
        val root = Files.createTempDirectory("ngr-catalog-store-").toFile()
        try {
            val directory = root.resolve("component-catalogs")
            directory.mkdirs()
            directory.resolve("corrupt.json").writeText("{invalid")
            val result = NgrComponentCatalogStore(root).loadAll()
            assertTrue(result.entries.isEmpty())
            assertEquals(1, result.errors.size)
        } finally { root.deleteRecursively() }
    }
}
