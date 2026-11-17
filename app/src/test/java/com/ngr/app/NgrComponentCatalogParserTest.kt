package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentCatalogParserTest {
    private val validJson = """
        {
          "schemaVersion": 1,
          "catalogId": "community",
          "revision": "2026-11-18",
          "components": [{
            "id": "box64",
            "type": "BOX64",
            "version": "0.3.2",
            "upstreamCommit": "0123456789abcdef",
            "targetAbis": ["ANDROID_ARM64_V8A", "GUEST_X86_64"],
            "archiveFormat": "ZIP",
            "downloadUrl": "https://downloads.example.org/box64.zip",
            "sha256": "${"a".repeat(64)}",
            "licenseSpdx": "MIT",
            "sourceRepository": "https://github.com/ptitSeb/box64",
            "dependencies": [],
            "conflicts": [],
            "constraints": {
              "containerModes": ["STANDARD_X86_64"],
              "requiresVulkan": true
            },
            "installSubdirectory": "runtime/box64"
          }]
        }
    """.trimIndent()

    @Test fun parsesValidCatalogAndDefaultsOptionalFields() {
        val catalog = NgrComponentCatalogParser.parse(validJson)
        assertEquals("community", catalog.catalogId)
        assertEquals(NgrComponentCatalog.Type.BOX64, catalog.components.single().type)
        assertEquals("previous", catalog.components.single().rollbackSlot)
        assertEquals(setOf(NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64),
            catalog.components.single().constraints.containerModes)
    }

    @Test fun rejectsUnknownCatalogAndComponentKeys() {
        val unknownRoot = validJson.replace("\"revision\":", "\"ignored\": 1, \"revision\":")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(unknownRoot) }.exceptionOrNull()
            ?.message.orEmpty().contains("Unknown key"))

        val unknownComponent = validJson.replace("\"id\": \"box64\",", "\"id\": \"box64\", \"unexpected\": true,")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(unknownComponent) }.exceptionOrNull()
            ?.message.orEmpty().contains("Unknown key"))
    }

    @Test fun rejectsUnknownEnumValuesAndWrongBooleanTypes() {
        val invalidEnum = validJson.replace("\"BOX64\"", "\"BOX_64\"")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(invalidEnum) }.exceptionOrNull()
            ?.message.orEmpty().contains("Unsupported enum"))

        val invalidBoolean = validJson.replace("\"requiresVulkan\": true", "\"requiresVulkan\": \"yes\"")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(invalidBoolean) }.exceptionOrNull()
            ?.message.orEmpty().contains("must be a boolean"))
    }

    @Test fun rejectsCatalogLargerThanConfiguredLimit() {
        val oversized = " ".repeat(NgrComponentCatalogParser.MAX_CATALOG_BYTES + 1)
        assertTrue(runCatching { NgrComponentCatalogParser.parse(oversized) }.exceptionOrNull()
            ?.message.orEmpty().contains("maximum JSON size"))
    }
    @Test fun rejectsStringOrFractionalSchemaVersion() {
        val stringVersion = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(stringVersion) }.exceptionOrNull()
            ?.message.orEmpty().contains("schemaVersion must be an integer"))
        val fractionalVersion = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5")
        assertTrue(runCatching { NgrComponentCatalogParser.parse(fractionalVersion) }.exceptionOrNull()
            ?.message.orEmpty().contains("schemaVersion must be an integer"))
    }
}
