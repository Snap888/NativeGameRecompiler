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
}
