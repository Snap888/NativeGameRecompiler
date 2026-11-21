package com.ngr.app.recompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class SwitchRuntimeLauncherTest {
    private val titleId = "0100000000010000"

    @Test
    fun runtimePathIsScopedToTitleId() {
        val root = File("/tmp/ngr-test")
        assertEquals(
            File(root, "switch-ports/$titleId/runtime/libdmhost.so"),
            SwitchRuntimeLauncher.runtimeLibrary(root, titleId)
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
    fun validatesRuntimeReceiptAndLibraryHash() {
        val root = Files.createTempDirectory("ngr-switch").toFile()
        try {
            val library = SwitchRuntimeLauncher.runtimeLibrary(root, titleId)
            library.parentFile.mkdirs()
            val bytes = byteArrayOf(0x7f, 0x45, 0x4c, 0x46)
            library.writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val packageDir = library.parentFile.parentFile
            val profile = File(packageDir, "runtime/game_profile.h")
            profile.writeText("synthetic profile header")
            val profileHash = MessageDigest.getInstance("SHA-256").digest(profile.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(packageDir, "runtime-build.json").writeText(
                """
                {
                  "format":"ngr-switch-runtime-build",
                  "schema_version":1,
                  "title_id":"$titleId",
                  "donor_revision":"${SwitchRuntimePackageVerifier.DONOR_REVISION}",
                  "runtime_library":"runtime/libdmhost.so",
                  "runtime_sha256":"$hash",
                  "profile_header":"runtime/game_profile.h",
                  "profile_header_sha256":"$profileHash",
                  "abi":"arm64-v8a",
                  "entry_point":"ANativeActivity_onCreate",
                  "apk_generated":false
                }
                """.trimIndent()
            )
            assertEquals(library.canonicalFile, SwitchRuntimeLauncher.validateRuntimeLibrary(root, titleId))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingRuntimeReceipt() {
        val root = Files.createTempDirectory("ngr-switch-missing").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                SwitchRuntimeLauncher.validateRuntimeLibrary(root, titleId)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsProfileHeaderHashMismatch() {
        val root = Files.createTempDirectory("ngr-switch-profile-tampered").toFile()
        try {
            val library = SwitchRuntimeLauncher.runtimeLibrary(root, titleId)
            library.parentFile.mkdirs()
            library.writeBytes(byteArrayOf(1, 2, 3, 4))
            val packageDir = library.parentFile.parentFile
            File(packageDir, "runtime/game_profile.h").writeText("tampered profile")
            val runtimeHash = MessageDigest.getInstance("SHA-256").digest(library.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(packageDir, "runtime-build.json").writeText(
                """
                {
                  "format":"ngr-switch-runtime-build",
                  "schema_version":1,
                  "title_id":"$titleId",
                  "donor_revision":"${SwitchRuntimePackageVerifier.DONOR_REVISION}",
                  "runtime_library":"runtime/libdmhost.so",
                  "runtime_sha256":"$runtimeHash",
                  "profile_header":"runtime/game_profile.h",
                  "profile_header_sha256":"${"0".repeat(64)}",
                  "abi":"arm64-v8a",
                  "entry_point":"ANativeActivity_onCreate",
                  "apk_generated":false
                }
                """.trimIndent()
            )
            assertThrows(IllegalArgumentException::class.java) {
                SwitchRuntimeLauncher.validateRuntimeLibrary(root, titleId)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRuntimeHashMismatch() {
        val root = Files.createTempDirectory("ngr-switch-tampered").toFile()
        try {
            val library = SwitchRuntimeLauncher.runtimeLibrary(root, titleId)
            library.parentFile.mkdirs()
            library.writeBytes(byteArrayOf(1, 2, 3, 4))
            val packageDir = library.parentFile.parentFile
            val profile = File(packageDir, "runtime/game_profile.h")
            profile.writeText("synthetic profile header")
            val profileHash = MessageDigest.getInstance("SHA-256").digest(profile.readBytes())
                .joinToString("") { "%02x".format(it) }
            File(packageDir, "runtime-build.json").writeText(
                """
                {
                  "format":"ngr-switch-runtime-build",
                  "schema_version":1,
                  "title_id":"$titleId",
                  "donor_revision":"${SwitchRuntimePackageVerifier.DONOR_REVISION}",
                  "runtime_library":"runtime/libdmhost.so",
                  "runtime_sha256":"${"0".repeat(64)}",
                  "profile_header":"runtime/game_profile.h",
                  "profile_header_sha256":"$profileHash",
                  "abi":"arm64-v8a",
                  "entry_point":"ANativeActivity_onCreate",
                  "apk_generated":false
                }
                """.trimIndent()
            )
            assertThrows(IllegalArgumentException::class.java) {
                SwitchRuntimeLauncher.validateRuntimeLibrary(root, titleId)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
