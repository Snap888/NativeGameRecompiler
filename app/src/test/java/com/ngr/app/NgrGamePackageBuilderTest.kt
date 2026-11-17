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
    @Test fun buildsManifestAndHashesFromGameFolder() {
        val base = createTempDir(prefix = "ngr-builder-folder-")
        val game = File(base, "Lethal Company")
        val exe = File(game, "Lethal Company.exe")
        val data = File(game, "Lethal Company_Data/level0")
        try {
            assertTrue(data.mkdirs())
            exe.writeBytes(byteArrayOf(0x4d, 0x5a, 0, 0))
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
                zip.write(byteArrayOf(0x4d, 0x5a, 1, 2))
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

    @Test fun rejectsFolderWithoutRecognizedExecutable() {
        val base = createTempDir(prefix = "ngr-builder-noexe-")
        val game = File(base, "data-only")
        try {
            assertTrue(game.mkdirs())
            File(game, "readme.txt").writeText("not a game executable")
            val result = NgrGamePackageBuilder().buildFromFolder(game, File(base, "out.ngrpack"))
            assertFalse(result.success)
            assertTrue(result.errors.any { it.contains("No Windows EXE or Linux ELF") })
        } finally { base.deleteRecursively() }
    }
}
