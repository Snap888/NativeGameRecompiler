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

class NgrPackPackageManagerTest {
    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun createArchive(version: String, payload: String, declaredHash: String = sha256(payload), extra: List<Pair<String, String>> = emptyList()): File {
        val manifest = """
            {
              "formatVersion":1,
              "packageId":"test.game",
              "displayName":"Test Game",
              "version":"$version",
              "backend":{"id":"test.backend","version":"1.0","targetAbi":"arm64-v8a"},
              "entryPoint":"bin/game.so",
              "files":[{"path":"bin/game.so","sha256":"$declaredHash"}]
            }
        """.trimIndent().toByteArray()
        val archive = File.createTempFile("ngr-package-", ".ngrpack")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            val entries = listOf("ngr-manifest.json" to manifest, "bin/game.so" to payload.toByteArray()) +
                extra.map { it.first to it.second.toByteArray() }
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return archive
    }

    @Test fun installsValidatedPackageAndListsIt() {
        val base = createTempDir(prefix = "ngr-pm-install-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "hello")
        try {
            val manager = NgrPackPackageManager(packages)
            val installed = manager.install(archive)
            assertTrue(installed.errors.joinToString(), installed.success)
            assertEquals("test.game", installed.packageInfo?.packageId)
            assertEquals("hello", File(packages, "test.game/bin/game.so").readText())
            val inventory = manager.listInstalled()
            assertTrue(inventory.errors.joinToString(), inventory.errors.isEmpty())
            assertEquals(1, inventory.packages.size)
            assertEquals("1.0", inventory.packages.single().version)
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

    @Test fun rejectsPayloadHashMismatchAndLeavesNoInstalledPackage() {
        val base = createTempDir(prefix = "ngr-pm-hash-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "tampered", declaredHash = sha256("original"))
        try {
            val manager = NgrPackPackageManager(packages)
            val result = manager.install(archive)
            assertFalse(result.success)
            assertTrue(result.errors.any { it.contains("SHA-256 mismatch") })
            assertFalse(File(packages, "test.game").exists())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

    @Test fun rejectsZipSlipWithoutWritingOutsidePackageRoot() {
        val base = createTempDir(prefix = "ngr-pm-slip-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "hello", extra = listOf("../escape.txt" to "escaped"))
        try {
            val manager = NgrPackPackageManager(packages)
            val result = manager.install(archive)
            assertFalse(result.success)
            assertFalse(File(base, "escape.txt").exists())
            assertFalse(File(packages, "test.game").exists())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

    @Test fun updatesExistingPackageOnlyAfterNewPackageValidates() {
        val base = createTempDir(prefix = "ngr-pm-update-")
        val packages = File(base, "packages")
        val first = createArchive("1.0", "version-one")
        val second = createArchive("2.0", "version-two")
        try {
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(first).success)
            val update = manager.install(second)
            assertTrue(update.errors.joinToString(), update.success)
            assertEquals("version-two", File(packages, "test.game/bin/game.so").readText())
            assertEquals("2.0", manager.listInstalled().packages.single().version)
            assertFalse(packages.listFiles()!!.any { it.name.startsWith(".backup-") || it.name.startsWith(".staging-") })
        } finally {
            first.delete()
            second.delete()
            base.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkAsExistingPackageDestination() {
        val base = createTempDir(prefix = "ngr-pm-symlink-")
        val packages = File(base, "packages")
        val outside = File(base, "outside")
        val archive = createArchive("1.0", "hello")
        try {
            packages.mkdirs()
            outside.mkdirs()
            Files.createSymbolicLink(File(packages, "test.game").toPath(), outside.toPath())
            val manager = NgrPackPackageManager(packages)
            val result = manager.install(archive)
            assertFalse(result.success)
            assertTrue(Files.isSymbolicLink(File(packages, "test.game").toPath()))
            assertTrue(outside.isDirectory)
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

    @Test fun removesInstalledPackage() {
        val base = createTempDir(prefix = "ngr-pm-remove-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "hello")
        try {
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(archive).success)
            val removed = manager.remove("test.game")
            assertTrue(removed.errors.joinToString(), removed.success)
            assertFalse(File(packages, "test.game").exists())
            assertTrue(manager.listInstalled().packages.isEmpty())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

    @Test fun rejectsArchivePayloadNotDeclaredInManifest() {
        val base = createTempDir(prefix = "ngr-pm-unlisted-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "hello", extra = listOf("extras/hidden.bin" to "unhashed"))
        try {
            val manager = NgrPackPackageManager(packages)
            val result = manager.install(archive)
            assertFalse(result.success)
            assertTrue(result.errors.any { it.contains("Unlisted package file") })
            assertFalse(File(packages, "test.game").exists())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }


    @Test fun invalidUpdatePreservesPreviouslyInstalledPackage() {
        val base = createTempDir(prefix = "ngr-pm-rollback-")
        val packages = File(base, "packages")
        val first = createArchive("1.0", "known-good")
        val invalidUpdate = createArchive("2.0", "tampered", declaredHash = sha256("expected"))
        try {
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(first).success)
            val update = manager.install(invalidUpdate)
            assertFalse(update.success)
            assertEquals("known-good", File(packages, "test.game/bin/game.so").readText())
            assertEquals("1.0", manager.listInstalled().packages.single().version)
        } finally {
            first.delete()
            invalidUpdate.delete()
            base.deleteRecursively()
        }
    }

    @Test fun restoresBackupWhenInterruptedBeforeNewPackageRename() {
        val base = createTempDir(prefix = "ngr-pm-recovery-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "recover-me")
        try {
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(archive).success)
            val installed = File(packages, "test.game")
            val backup = File(packages, ".backup-test.game-interrupted")
            Files.move(installed.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            val inventory = manager.listInstalled()
            assertTrue(inventory.errors.joinToString(), inventory.errors.isEmpty())
            assertEquals(1, inventory.packages.size)
            assertEquals("recover-me", File(packages, "test.game/bin/game.so").readText())
            assertFalse(backup.exists())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }


    @Test fun listsAndSafelyRemovesCorruptPackageDirectory() {
        val base = createTempDir(prefix = "ngr-pm-corrupt-")
        val packages = File(base, "packages")
        val archive = createArchive("1.0", "hello")
        try {
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(archive).success)
            File(packages, "test.game/unlisted.bin").writeText("corrupt extra data")
            val inventory = manager.listInstalled()
            assertTrue(inventory.packages.isEmpty())
            assertTrue(inventory.invalidPackageIds.contains("test.game"))
            val removed = manager.remove("test.game")
            assertTrue(removed.message, removed.success)
            assertFalse(File(packages, "test.game").exists())
        } finally {
            archive.delete()
            base.deleteRecursively()
        }
    }

}
