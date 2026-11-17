package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrPackArchiveInspectorTest {
    private val manifest = """{"formatVersion":1,"packageId":"test.game"}""".toByteArray()

    private fun archive(entries: List<Pair<String, ByteArray>>): File {
        val file = File.createTempFile("ngr-test-", ".ngrpack")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test fun acceptsBoundedArchiveWithRootManifest() {
        val file = archive(listOf("ngr-manifest.json" to manifest, "bin/game.so" to byteArrayOf(1, 2, 3)))
        try {
            val result = NgrPackArchiveInspector.inspect(file)
            assertTrue(result.errors.joinToString(), result.valid)
            assertTrue(result.entries.any { it.path == "bin/game.so" })
            assertTrue(result.manifestText != null)
        } finally { file.delete() }
    }

    @Test fun explainsManifestWrappedInTopLevelFolder() {
        val file = archive(listOf(
            "ExportedGame/ngr-manifest.json" to manifest,
            "ExportedGame/bin/game.so" to byteArrayOf(1, 2, 3)
        ))
        try {
            val result = NgrPackArchiveInspector.inspect(file)
            assertFalse(result.valid)
            assertTrue(result.errors.joinToString().contains("Manifest found only inside subdirectory"))
            assertTrue(result.errors.joinToString().contains("ExportedGame/ngr-manifest.json"))
        } finally { file.delete() }
    }

    @Test fun rejectsZipSlipEntry() {
        val file = archive(listOf("ngr-manifest.json" to manifest, "../outside" to byteArrayOf(1)))
        try {
            val result = NgrPackArchiveInspector.inspect(file)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Unsafe ZIP entry path") })
        } finally { file.delete() }
    }

    @Test fun rejectsCaseCollidingPaths() {
        val file = archive(listOf(
            "ngr-manifest.json" to manifest,
            "bin/Game.so" to byteArrayOf(1),
            "bin/game.so" to byteArrayOf(2)
        ))
        try {
            val result = NgrPackArchiveInspector.inspect(file)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("case-colliding") })
        } finally { file.delete() }
    }

    @Test fun rejectsExcessiveCompressionRatio() {
        val file = archive(listOf(
            "ngr-manifest.json" to manifest,
            "payload/repetitive.bin" to ByteArray(8192) { 0 }
        ))
        try {
            val result = NgrPackArchiveInspector.inspect(file, NgrPackArchiveInspector.Limits(maxCompressionRatio = 2))
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("compression ratio") })
        } finally { file.delete() }
    }

    @Test fun rejectsUnixSymbolicLinkEntry() {
        val file = archive(listOf(
            "ngr-manifest.json" to manifest,
            "bin/link" to "target".toByteArray()
        ))
        try {
            RandomAccessFile(file, "rw").use { raf ->
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
                        if (name == "bin/link") {
                            raf.seek((i + 4).toLong())
                            raf.write(byteArrayOf(0x14, 0x03))
                            raf.seek((i + 38).toLong())
                            raf.write(byteArrayOf(0, 0, 0xFF.toByte(), 0xA1.toByte()))
                            patched = true
                            break
                        }
                    }
                }
                assertTrue("test ZIP central entry was not patched", patched)
            }
            val result = NgrPackArchiveInspector.inspect(file)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("symbolic-link") })
        } finally { file.delete() }
    }

    @Test fun rejectsArchiveEntryCountLimit() {
        val file = archive(listOf("ngr-manifest.json" to manifest, "a" to byteArrayOf(1), "b" to byteArrayOf(2)))
        try {
            val result = NgrPackArchiveInspector.inspect(file, NgrPackArchiveInspector.Limits(maxEntries = 2))
            assertFalse(result.valid)
        } finally { file.delete() }
    }
}
