package com.ngr.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NgrWinePrefixManagerTest {
    private fun ready(manager: NgrWinePrefixManager, id: String = "base") {
        assertTrue(manager.create(id, "Base prefix").success)
        val dir = manager.inspect(id)!!.directory
        listOf("system.reg", "user.reg", "userdef.reg").forEach { File(dir, it).writeText("registry") }
        File(dir, "drive_c").mkdirs()
        File(dir, "dosdevices").mkdirs()
        File(dir, "drive_c/windows").mkdirs()
        File(dir, "drive_c/windows/win.ini").writeText("[windows]")
    }

    @Test fun createIsPreparedUntilRealWineMarkersExist() {
        val root = Files.createTempDirectory("ngr-prefix-").toFile()
        try {
            val manager = NgrWinePrefixManager(root)
            val created = manager.create("test", "Test")
            assertTrue(created.error ?: "create failed", created.success)
            assertEquals(NgrWinePrefixManager.State.PREPARED, manager.inspect("test")?.state)
            assertFalse(manager.inspect("test")?.let { it.state == NgrWinePrefixManager.State.READY } ?: true)
            ready(manager, "ready")
            assertEquals(NgrWinePrefixManager.State.READY, manager.inspect("ready")?.state)
        } finally { root.deleteRecursively() }
    }

    @Test fun cloneBackupAndRestorePreserveInitializedPrefix() {
        val root = Files.createTempDirectory("ngr-prefix-").toFile()
        try {
            val manager = NgrWinePrefixManager(root)
            ready(manager)
            val clone = manager.clone("base", "clone", "Clone")
            assertTrue(clone.error ?: "clone failed", clone.success)
            assertEquals(NgrWinePrefixManager.State.READY, clone.prefix?.state)
            assertEquals("[windows]", File(clone.prefix!!.directory, "drive_c/windows/win.ini").readText())

            val backup = manager.backup("base", "backup-1")
            assertTrue(backup.error ?: "backup failed", backup.success)
            val restored = manager.restore("backup-1", "restored", "Restored")
            assertTrue(restored.error ?: "restore failed", restored.success)
            assertEquals(NgrWinePrefixManager.State.READY, restored.prefix?.state)
            assertEquals("[windows]", File(restored.prefix!!.directory, "drive_c/windows/win.ini").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesCloneOfPreparedPrefixAndDuplicateIds() {
        val root = Files.createTempDirectory("ngr-prefix-").toFile()
        try {
            val manager = NgrWinePrefixManager(root)
            assertTrue(manager.create("empty", "Empty").success)
            assertFalse(manager.clone("empty", "copy", "Copy").success)
            assertFalse(manager.create("empty", "Duplicate").success)
            assertFalse(manager.create("../escape", "Bad").success)
        } finally { root.deleteRecursively() }
    }

    @Test fun resetReturnsPrefixToPreparedAndRemoveIsScoped() {
        val root = Files.createTempDirectory("ngr-prefix-").toFile()
        try {
            val manager = NgrWinePrefixManager(root)
            ready(manager)
            assertTrue(manager.reset("base").success)
            assertEquals(NgrWinePrefixManager.State.PREPARED, manager.inspect("base")?.state)
            assertTrue(manager.remove("base").success)
            assertNull(manager.inspect("base"))
            assertFalse(manager.remove("../outside").success)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsSymlinkInsidePrefixDuringInspectionAndRemoval() {
        val root = Files.createTempDirectory("ngr-prefix-").toFile()
        val outside = Files.createTempDirectory("ngr-prefix-outside-").toFile()
        try {
            val manager = NgrWinePrefixManager(root)
            assertTrue(manager.create("linked", "Linked").success)
            val prefix = manager.inspect("linked")!!.directory
            Files.createSymbolicLink(File(prefix, "drive_c").toPath(), outside.toPath())
            assertNull(manager.inspect("linked"))
            assertFalse(manager.remove("linked").success)
            assertTrue(outside.exists())
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }
}
