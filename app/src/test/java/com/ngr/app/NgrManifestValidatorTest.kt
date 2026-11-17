package com.ngr.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrManifestValidatorTest {
    private val validHash = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    private fun manifest(entry: String = "bin/game.so", filePath: String = "bin/game.so") = """
        {
          "formatVersion":1,
          "packageId":"test.game",
          "displayName":"Test Game",
          "backend":{"id":"test.backend","version":"1.0","targetAbi":"arm64-v8a"},
          "entryPoint":"$entry",
          "files":[{"path":"$filePath","sha256":"$validHash"}]
        }
    """.trimIndent()

    @Test fun acceptsValidExtractedPackage() {
        val root = createTempDir(prefix = "ngr-valid-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val result = NgrManifestValidator.validate(manifest(), root)
            assertTrue(result.errors.joinToString(), result.valid)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsPathTraversal() {
        val root = createTempDir(prefix = "ngr-traversal-")
        try {
            val result = NgrManifestValidator.validate(manifest("../outside.so", "../outside.so"), root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("unsafe") })
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsHashMismatch() {
        val root = createTempDir(prefix = "ngr-hash-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("tampered")
            val result = NgrManifestValidator.validate(manifest(), root)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("SHA-256 mismatch") })
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsEntryPointMissingFromHashedFileList() {
        val root = createTempDir(prefix = "ngr-entry-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val result = NgrManifestValidator.validate(manifest("bin/game.so", "bin/other.so"), root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("entryPoint must be included") })
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsSymlinkPayload() {
        val root = createTempDir(prefix = "ngr-symlink-")
        val outside = createTempFile(prefix = "ngr-outside-")
        try {
            val link = File(root, "bin/game.so")
            link.parentFile!!.mkdirs()
            java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath())
            val result = NgrManifestValidator.validate(manifest(), root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Symbolic links") })
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }


    @Test fun rejectsUnknownManifestPropertiesLikeSchema() {
        val root = createTempDir(prefix = "ngr-schema-extra-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val invalid = manifest().replace("\"formatVersion\":1,", "\"formatVersion\":1,\"unexpected\":true,")
            val result = NgrManifestValidator.validate(invalid, root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Unknown manifest property") })
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsDisplayNameLongerThanSchemaLimit() {
        val root = createTempDir(prefix = "ngr-schema-name-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val longName = "x".repeat(161)
            val invalid = manifest().replace("\"displayName\":\"Test Game\"", "\"displayName\":\"$longName\"")
            val result = NgrManifestValidator.validate(invalid, root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("displayName") })
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsDuplicatePathsAfterSeparatorNormalization() {
        val root = createTempDir(prefix = "ngr-duplicate-path-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val duplicateFiles = "\"files\":[{\"path\":\"bin/game.so\",\"sha256\":\"$validHash\"},{\"path\":\"bin\\\\game.so\",\"sha256\":\"$validHash\"}]"
            val invalid = manifest().replace("\"files\":[{\"path\":\"bin/game.so\",\"sha256\":\"$validHash\"}]", duplicateFiles)
            val result = NgrManifestValidator.validate(invalid, root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Duplicate file path") })
        } finally { root.deleteRecursively() }
    }


    @Test fun rejectsUnlistedPayloadFiles() {
        val root = createTempDir(prefix = "ngr-unlisted-")
        try {
            val payload = File(root, "bin/game.so")
            payload.parentFile!!.mkdirs()
            payload.writeText("hello")
            val extra = File(root, "extras/unhashed.dat")
            extra.parentFile!!.mkdirs()
            extra.writeText("not covered by manifest")
            val result = NgrManifestValidator.validate(manifest(), root, false)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Unlisted package file") })
        } finally { root.deleteRecursively() }
    }

}
