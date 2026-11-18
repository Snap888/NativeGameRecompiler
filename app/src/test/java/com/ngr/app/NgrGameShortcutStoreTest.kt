package com.ngr.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NgrGameShortcutStoreTest {
    private fun shortcut(id: String = "game") = NgrGameShortcut(
        shortcutId = id,
        title = "Test Game",
        packageId = "test-package",
        executablePath = "C:/Games/Test/game.exe",
        workingDirectory = "C:/Games/Test",
        arguments = listOf("-windowed", "-dx11"),
        environmentProfileId = "desktop",
        environmentOverrides = mapOf("DXVK_ASYNC" to "1")
    )

    @Test fun savesLoadsReplacesAndDeletesShortcut() {
        val root = Files.createTempDirectory("ngr-shortcuts-").toFile()
        try {
            val store = NgrGameShortcutStore(root)
            val original = shortcut()
            assertTrue(store.save(original).success)
            assertEquals(original, store.get("game"))
            assertTrue(store.save(original.copy(title = "Updated Game")).success)
            assertEquals("Updated Game", store.get("game")?.title)
            assertEquals(1, store.loadAll().shortcuts.size)
            assertTrue(store.loadAll().errors.isEmpty())
            assertTrue(store.delete("game").success)
            assertTrue(store.loadAll().shortcuts.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsUnsafeGuestPathsProtectedVariablesAndIds() {
        val root = Files.createTempDirectory("ngr-shortcuts-").toFile()
        try {
            val store = NgrGameShortcutStore(root)
            assertFalse(store.save(shortcut().copy(executablePath = "C:/Games/../evil.exe")).success)
            assertFalse(store.save(shortcut().copy(executablePath = "/data/local/tmp/evil.exe")).success)
            assertFalse(store.save(shortcut().copy(environmentOverrides = mapOf("LD_PRELOAD" to "evil.so"))).success)
            assertFalse(store.save(shortcut().copy(shortcutId = "../escape")).success)
            assertNull(store.get("../escape"))
            assertFalse(store.delete("../escape").success)
        } finally { root.deleteRecursively() }
    }

    @Test fun reportsCorruptAndUnknownSchemaFiles() {
        val root = Files.createTempDirectory("ngr-shortcuts-").toFile()
        try {
            val directory = File(root, "game-shortcuts")
            directory.mkdirs()
            File(directory, "broken.json").writeText("{bad json")
            File(directory, "future.json").writeText("""{"schemaVersion":99}""")
            val result = NgrGameShortcutStore(root).loadAll()
            assertTrue(result.shortcuts.isEmpty())
            assertEquals(2, result.errors.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun supportsAllExecutionModeIdentifiersWithoutClaimingTheyAreAvailable() {
        val root = Files.createTempDirectory("ngr-shortcuts-").toFile()
        try {
            val store = NgrGameShortcutStore(root)
            NgrGameShortcut.LaunchMode.values().forEachIndexed { index, mode ->
                val value = shortcut("game-$index").copy(launchMode = mode)
                assertTrue(store.save(value).success)
                assertEquals(mode, store.get(value.shortcutId)?.launchMode)
            }
        } finally { root.deleteRecursively() }
    }
}
