package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
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
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
        try {
            val result = NgrComponentArchiveInstaller.extractZip(archive, component(sha256(archive)), parent)
            assertTrue(result.error ?: "extraction failed", result.success)
            assertEquals(2, result.extractedFiles)
            assertEquals(5L, result.extractedBytes)
            assertEquals(listOf<Byte>(1, 2, 3), result.stagingDirectory!!.resolve("x64/d3d11.dll").readBytes().toList())
            assertTrue(result.stagingDirectory.resolve("x64/dxgi.dll").isFile)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test fun rejectsArchiveHashMismatchBeforeExtraction() {
        val archive = archive(listOf("bin/component.so" to byteArrayOf(1, 2, 3)))
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
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
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
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
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
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
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
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
    @Test fun preservesExecutableBitFromUnixZipMetadata() {
        val archive = archive(listOf("bin/runner" to byteArrayOf(0x7F, 0x45, 0x4C, 0x46)))
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
        try {
            RandomAccessFile(archive, "rw").use { raf ->
                val bytes = ByteArray(raf.length().toInt())
                raf.seek(0)
                raf.readFully(bytes)
                var patched = false
                for (i in 0..bytes.size - 46) {
                    if ((bytes[i].toInt() and 0xFF) == 0x50 &&
                        (bytes[i + 1].toInt() and 0xFF) == 0x4B &&
                        (bytes[i + 2].toInt() and 0xFF) == 0x01 &&
                        (bytes[i + 3].toInt() and 0xFF) == 0x02) {
                        val nameLength = (bytes[i + 28].toInt() and 0xFF) or ((bytes[i + 29].toInt() and 0xFF) shl 8)
                        val name = String(bytes, i + 46, nameLength, Charsets.UTF_8)
                        if (name == "bin/runner") {
                            raf.seek((i + 4).toLong())
                            raf.write(byteArrayOf(0x14, 0x03))
                            raf.seek((i + 38).toLong())
                            raf.write(byteArrayOf(0, 0, 0xED.toByte(), 0x81.toByte()))
                            patched = true
                            break
                        }
                    }
                }
                assertTrue("test ZIP central entry was not patched", patched)
            }
            val result = NgrComponentArchiveInstaller.extractZip(archive, component(sha256(archive)), parent)
            assertTrue(result.error ?: "extraction failed", result.success)
            assertTrue(result.stagingDirectory!!.resolve("bin/runner").canExecute())
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }

    @Test fun normalizesBackslashSeparatorsToSafeRelativePaths() {
        val archive = archive(listOf("bin\\runner.exe" to byteArrayOf(1, 2, 3)))
        val parent = Files.createTempDirectory("ngr-stage-parent-").toFile()
        try {
            val result = NgrComponentArchiveInstaller.extractZip(archive, component(sha256(archive)), parent)
            assertTrue(result.error ?: "extraction failed", result.success)
            assertTrue(result.stagingDirectory!!.resolve("bin/runner.exe").isFile)
        } finally {
            archive.delete()
            parent.deleteRecursively()
        }
    }
}
