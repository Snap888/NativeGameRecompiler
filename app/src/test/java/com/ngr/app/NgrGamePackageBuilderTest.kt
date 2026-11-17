package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrGamePackageBuilderTest {
    private fun peX64(): ByteArray = ByteArray(128).also { bytes ->
        bytes[0] = 0x4d
        bytes[1] = 0x5a
        bytes[0x3c] = 64
        bytes[64] = 0x50
        bytes[65] = 0x45
        bytes[66] = 0
        bytes[67] = 0
        bytes[68] = 0x64
        bytes[69] = 0x86.toByte()
    }

    @Test fun buildsManifestAndHashesFromGameFolder() {
        val base = createTempDir(prefix = "ngr-builder-folder-")
        val game = File(base, "Lethal Company")
        val exe = File(game, "Lethal Company.exe")
        val data = File(game, "Lethal Company_Data/level0")
        try {
            assertTrue(data.mkdirs())
            exe.writeBytes(peX64())
            File(data, "sharedassets0.assets").writeText("game-data")
            val archive = File(base, "generated.ngrpack")
            val built = NgrGamePackageBuilder().buildFromFolder(game, archive)
            assertTrue(built.errors.joinToString(), built.success)
            assertEquals("windows", built.platform)
            assertEquals("Lethal Company.exe", built.entryPoint)
            val packages = File(base, "packages")
            val installed = NgrPackPackageManager(packages).install(archive)
            assertTrue(installed.errors.joinToString(), installed.success)
            assertEquals("Lethal Company.exe", installed.packageInfo?.entryPoint)
            assertTrue(File(installed.packageInfo!!.directory, "Lethal Company_Data/level0/sharedassets0.assets").isFile)
            assertTrue(NgrManifestValidator.validate(
                File(installed.packageInfo.directory, "ngr-manifest.json").readText(),
                installed.packageInfo.directory
            ).valid)
        } finally { base.deleteRecursively() }
    }

    @Test fun buildsPackageFromOrdinaryZipWithSingleWrapperFolder() {
        val base = createTempDir(prefix = "ngr-builder-zip-")
        val source = File(base, "Lethal Company.zip")
        try {
            ZipOutputStream(FileOutputStream(source)).use { zip ->
                zip.putNextEntry(ZipEntry("Lethal Company/Lethal Company.exe"))
                zip.write(peX64())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Lethal Company/data/game.dat"))
                zip.write("payload".toByteArray())
                zip.closeEntry()
            }
            val generated = File(base, "generated.ngrpack")
            val built = NgrGamePackageBuilder().buildFromZip(source, generated)
            assertTrue(built.errors.joinToString(), built.success)
            assertEquals("Lethal Company.exe", built.entryPoint)
            val installed = NgrPackPackageManager(File(base, "packages")).install(generated)
            assertTrue(installed.errors.joinToString(), installed.success)
            assertTrue(File(installed.packageInfo!!.directory, "data/game.dat").isFile)
        } finally { base.deleteRecursively() }
    }

    @Test fun repeatedImportUsesStablePackageIdAndUpdatesExistingGame() {
        val base = createTempDir(prefix = "ngr-builder-update-")
        val game = File(base, "SameGame")
        try {
            assertTrue(game.mkdirs())
            File(game, "SameGame.exe").writeBytes(peX64())
            val builder = NgrGamePackageBuilder()
            val firstArchive = File(base, "first.ngrpack")
            val secondArchive = File(base, "second.ngrpack")
            val first = builder.buildFromFolder(game, firstArchive)
            val second = builder.buildFromFolder(game, secondArchive)
            assertTrue(first.errors.joinToString(), first.success)
            assertTrue(second.errors.joinToString(), second.success)
            val packages = File(base, "packages")
            val manager = NgrPackPackageManager(packages)
            assertTrue(manager.install(firstArchive).success)
            assertTrue(manager.install(secondArchive).success)
            assertEquals(1, manager.listInstalled().packages.size)
            assertEquals("import.samegame", manager.listInstalled().packages.single().packageId.substringBeforeLast('.'))
        } finally { base.deleteRecursively() }
    }

    @Test fun detectsLinuxElfArchitecture() {
        val base = createTempDir(prefix = "ngr-builder-elf-")
        val game = File(base, "LinuxGame")
        try {
            assertTrue(game.mkdirs())
            val elf = ByteArray(64)
            elf[0] = 0x7f
            elf[1] = 0x45
            elf[2] = 0x4c
            elf[3] = 0x46
            elf[4] = 2
            elf[5] = 1
            elf[18] = 62
            File(game, "game.x86_64").writeBytes(elf)
            val result = NgrGamePackageBuilder().buildFromFolder(game, File(base, "linux.ngrpack"))
            assertTrue(result.errors.joinToString(), result.success)
            assertEquals("linux", result.platform)
            assertEquals("x86_64", result.architecture)
            assertEquals("game.x86_64", result.entryPoint)
        } finally { base.deleteRecursively() }
    }

    @Test fun prefersGameExecutableAndIgnoresUninstaller() {
        val base = createTempDir(prefix = "ngr-builder-entrypoint-")
        val game = File(base, "Lethal Company")
        try {
            assertTrue(game.mkdirs())
            File(game, "unins000.exe").writeBytes(peX86())
            File(game, "Lethal Company.exe").writeBytes(peX64())
            val result = NgrGamePackageBuilder().buildFromFolder(game, File(base, "game.ngrpack"))
            assertTrue(result.errors.joinToString(), result.success)
            assertEquals("Lethal Company.exe", result.entryPoint)
            assertEquals("x86_64", result.architecture)
        } finally { base.deleteRecursively() }
    }

    @Test fun rejectsFolderContainingOnlyUninstallerExecutable() {
        val base = createTempDir(prefix = "ngr-builder-uninstaller-")
        val game = File(base, "Lethal Company")
        try {
            assertTrue(game.mkdirs())
            File(game, "unins000.exe").writeBytes(peX86())
            val result = NgrGamePackageBuilder().buildFromFolder(game, File(base, "game.ngrpack"))
            assertFalse(result.success)
            assertTrue(result.errors.joinToString().contains("Only installer, uninstaller or helper"))
        } finally { base.deleteRecursively() }
    }

    private fun peX86(): ByteArray = peX64().also { bytes ->
        bytes[68] = 0x4c
        bytes[69] = 0x01
    }

    @Test fun rejectsFolderWithoutRecognizedExecutable() {
        val base = createTempDir(prefix = "ngr-builder-noexe-")
        val game = File(base, "data-only")
        try {
            assertTrue(game.mkdirs())
            File(game, "readme.txt").writeText("not a game executable")
            val result = NgrGamePackageBuilder().buildFromFolder(game, File(base, "out.ngrpack"))
            assertFalse(result.success)
            assertTrue(result.errors.any { it.contains("No valid Windows PE EXE or Linux ELF") })
        } finally { base.deleteRecursively() }
    }
}
