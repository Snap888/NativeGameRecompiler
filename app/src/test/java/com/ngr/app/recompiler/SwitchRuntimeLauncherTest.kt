package com.ngr.app.recompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SwitchRuntimeLauncherTest {
    @Test
    fun runtimePathIsScopedToTitleId() {
        val root = File("/tmp/ngr-test")
        assertEquals(
            File(root, "switch-ports/0100000000010000/runtime/libdmhost.so"),
            SwitchRuntimeLauncher.runtimeLibrary(root, "0100000000010000")
        )
    }

    @Test
    fun rejectsMalformedTitleIds() {
        assertThrows(IllegalArgumentException::class.java) {
            SwitchRuntimeLauncher.runtimeLibrary(File("/tmp/ngr-test"), "../../escape")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SwitchRuntimeLauncher.runtimeLibrary(File("/tmp/ngr-test"), "010000000001000")
        }
    }

    @Test
    fun validatesExistingRegularRuntime() {
        val root = Files.createTempDirectory("ngr-switch").toFile()
        try {
            val library = SwitchRuntimeLauncher.runtimeLibrary(root, "0100000000010000")
            library.parentFile.mkdirs()
            library.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
            assertEquals(library.canonicalFile, SwitchRuntimeLauncher.validateRuntimeLibrary(root, "0100000000010000"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingRuntime() {
        val root = Files.createTempDirectory("ngr-switch-missing").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                SwitchRuntimeLauncher.validateRuntimeLibrary(root, "0100000000010000")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
