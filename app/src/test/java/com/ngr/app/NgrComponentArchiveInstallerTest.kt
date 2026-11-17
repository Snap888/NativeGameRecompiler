package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentArchiveInstallerTest {
    private fun archive(entries: List<Pair<String, ByteArray>>): File {
        val file = File.createTempFile("ngr-component-", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun component(hash: String) = NgrComponentCatalog.Component(
        id = "test-component",
        type = NgrComponentCatalog.Type.DXVK,
        version = "1.0.0",
        targetAbis = setOf(NgrComponentCatalog.TargetAbi.GUEST_X86_64),
        archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
        downloadUrl = "https://example.org/component.zip",
        sha256 = hash,
        licenseSpdx = "MIT",
        sourceRepository = "https://github.com/example/component",
        installSubdirectory = "graphics/dxvk"
    )

    @Test fun extractsVerifiedZipIntoFreshStagingDirectory() {
        val archive = archive(listOf(
            "x64/d3d11.dll" to byteArrayOf(1, 2, 3),
            "x64/dxgi.dll" to byteArrayOf(4, 5)
        ))
        val parent = createTempDir(prefix = "ngr-stage-parent-")
        try {
            val result = NgrComponentArchiveInstaller.extractZip(archive, component(sha256(archive)), parent)
            assertTrue(result.error ?: "extraction failed", result.success)
            assertEquals(2, result.extractedFiles)
            assertEquals(5L, result.extractedBytes)
            assertEquals(listOf(1, 2, 3), result.stagingDirectory!!.resolve("x64/d3d11.dll").readBytes().toList())
            assertTrue(result.stagingDirectory.resolve("x64/dxgi.dll").isFile)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test fun rejectsArchiveHashMismatchBeforeExtraction() {
        val archive = archive(listOf("bin/component.so" to byteArrayOf(1, 2, 3)))
        val parent = createTempDir(prefix = "ngr-stage-parent-")
        try {
            val result = NgrComponentArchiveInstaller.extractZip(archive, component("0".repeat(64)), parent)
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("SHA-256 mismatch"))
            assertEquals(0, parent.listFiles()?.size ?: 0)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test fun rejectsZipSlipWithoutLeavingStagingDirectory() {
        val archive = archive(listOf("../escape.bin" to byteArrayOf(1)))
        val parent = createTempDir(prefix = "ngr-stage-parent-")
        try {
            val result = NgrComponentArchiveInstaller.extractZip(archive, component(sha256(archive)), parent)
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("Unsafe ZIP entry path"))
            assertEquals(0, parent.listFiles()?.size ?: 0)
            assertFalse(File(parent.parentFile, "escape.bin").exists())
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test fun rejectsUnsupportedArchiveFormat() {
        val archive = archive(listOf("payload" to byteArrayOf(1)))
        val parent = createTempDir(prefix = "ngr-stage-parent-")
        try {
            val result = NgrComponentArchiveInstaller.extractZip(
                archive,
                component(sha256(archive)).copy(archiveFormat = NgrComponentCatalog.ArchiveFormat.TAR_GZ),
                parent
            )
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("Only ZIP"))
            assertEquals(0, parent.listFiles()?.size ?: 0)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }
    @Test fun rejectsUnsafeComponentInstallSubdirectory() {
        val archive = archive(listOf("payload" to byteArrayOf(1)))
        val parent = createTempDir(prefix = "ngr-stage-parent-")
        try {
            val result = NgrComponentArchiveInstaller.extractZip(
                archive,
                component(sha256(archive)).copy(installSubdirectory = "../../outside"),
                parent
            )
            assertFalse(result.success)
            assertTrue(result.error.orEmpty().contains("install subdirectory is unsafe"))
            assertEquals(0, parent.listFiles()?.size ?: 0)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }
}
