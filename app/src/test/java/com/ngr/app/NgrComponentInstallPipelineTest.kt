package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentInstallPipelineTest {
    private fun archive(): File {
        val file = File.createTempFile("ngr-pipeline-", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("runtime/box64/box64"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return file
    }

    private fun sha256(file: File) =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun component(hash: String) = NgrComponentCatalog.Component(
        id = "box64",
        type = NgrComponentCatalog.Type.BOX64,
        version = "0.3.2",
        targetAbis = setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A, NgrComponentCatalog.TargetAbi.GUEST_X86_64),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/box64.zip",
        sha256 = hash,
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/ptitSeb/box64",
        installSubdirectory = "runtime/box64"
    )

    @Test fun installsVerifiedLocalArchiveAndActivatesVersion() {
        val archive = archive()
        val root = Files.createTempDirectory("ngr-pipeline-store-").toFile()
        try {
            val result = NgrComponentInstallPipeline(NgrComponentStore(root))
                .installVerifiedArchive(component(sha256(archive)), archive)
            assertTrue(result.error ?: "pipeline install failed", result.success)
            assertEquals("0.3.2", result.version)
            assertTrue(result.activeDirectory!!.resolve("runtime/box64/box64").isFile)
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }

    @Test fun rejectsHashMismatchAndLeavesNoActiveComponent() {
        val archive = archive()
        val root = Files.createTempDirectory("ngr-pipeline-store-").toFile()
        try {
            val store = NgrComponentStore(root)
            val result = NgrComponentInstallPipeline(store).installVerifiedArchive(component("0".repeat(64)), archive)
            assertFalse(result.success)
            assertEquals(null, store.activeVersionDirectory("box64"))
            assertEquals(0, store.stagingDirectory().listFiles()?.size ?: 0)
        } finally {
            archive.delete()
            root.deleteRecursively()
        }
    }
}
