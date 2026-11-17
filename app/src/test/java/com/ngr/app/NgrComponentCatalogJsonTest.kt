package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NgrComponentCatalogJsonTest {
    private fun validJson() = """
        {
          "schemaVersion": 1,
          "catalogId": "community",
          "revision": "r1",
          "components": [{
            "id": "box64",
            "type": "BOX64",
            "version": "0.3.2",
            "upstreamCommit": "0123456789abcdef",
            "targetAbis": ["ANDROID_ARM64_V8A", "GUEST_X86_64"],
            "archiveFormat": "TAR_ZSTD",
            "downloadUrl": "https://example.org/box64.tar.zst",
            "sha256": "${"a".repeat(64)}",
            "licenseSpdx": "MIT",
            "sourceRepository": "https://github.com/ptitSeb/box64",
            "dependencies": [],
            "conflicts": [],
            "constraints": {
              "containerModes": ["STANDARD_X86_64"],
              "requiresVulkan": true
            },
            "installSubdirectory": "runtime/box64",
            "rollbackSlot": "previous"
          }]
        }
    """.trimIndent()

    @Test fun parsesTypedCatalogAndCompatibilityConstraints() {
        val catalog = NgrComponentCatalogJson.parse(validJson())
        assertEquals("community", catalog.catalogId)
        assertEquals(NgrComponentCatalog.Type.BOX64, catalog.components.single().type)
        assertTrue(catalog.components.single().constraints.requiresVulkan)
        assertEquals(
            setOf(NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64),
            catalog.components.single().constraints.containerModes
        )
    }

    @Test fun rejectsUnknownRootAndComponentFields() {
        val root = validJson().replace("\"revision\": \"r1\",", "\"revision\": \"r1\",\"futureMode\":true,")
        assertParseFails(root, "Unknown catalog JSON fields")
        val component = validJson().replace("\"version\": \"0.3.2\",", "\"version\": \"0.3.2\",\"execute\":true,")
        assertParseFails(component, "Unknown catalog JSON fields")
    }

    @Test fun rejectsUnknownEnumsAndInvalidMetadata() {
        val invalid = validJson().replace("\"BOX64\"", "\"MAGIC_EXECUTOR\"")
        assertParseFails(invalid, "MAGIC_EXECUTOR")
    }

    @Test fun rejectsDuplicateArrayItems() {
        val invalid = validJson().replace("\"dependencies\": [],", "\"dependencies\": [\"dxvk\", \"dxvk\"],")
        assertParseFails(invalid, "Duplicate array item")
    }

    private fun assertParseFails(json: String, message: String) {
        try {
            NgrComponentCatalogJson.parse(json)
            fail("Expected catalog JSON parsing to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message))
        } catch (error: Exception) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message))
        }
    }
}
