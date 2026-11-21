package com.ngr.app.recompiler

import android.content.Context
import android.content.Intent
import android.app.NativeActivity
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Launches nx2android's NativeActivity runtime inside the NGR package.
 *
 * The title-specific libdmhost.so is data in NGR's private files directory,
 * not an installed APK or an extracted native-library directory.
 */
object SwitchRuntimeLauncher {
    const val EXTRA_RUNTIME_LIBRARY = "com.ngr.app.SWITCH_RUNTIME_LIBRARY"
    private val TITLE_ID = Regex("^[0-9A-Fa-f]{16}$")

    fun runtimeLibrary(filesDir: File, titleId: String): File {
        require(TITLE_ID.matches(titleId)) { "Invalid Switch title ID" }
        return File(filesDir, "switch-ports/$titleId/runtime/libdmhost.so")
    }

    @Throws(IOException::class)
    fun validateRuntimeLibrary(filesDir: File, titleId: String): File {
        val root = filesDir.canonicalFile
        val expected = runtimeLibrary(root, titleId)
        val canonical = expected.canonicalFile
        val portsRoot = File(root, "switch-ports").canonicalFile
        val expectedPrefix = portsRoot.path + File.separator
        require(canonical.path.startsWith(expectedPrefix)) { "Runtime path escapes NGR storage" }
        require(canonical == expected) { "Runtime library must not resolve through a symbolic link" }
        require(!Files.isSymbolicLink(expected.toPath())) { "Runtime library must not be a symbolic link" }
        require(expected.isFile && expected.length() > 0L) { "Switch runtime library is missing" }
        return expected
    }

    fun launch(context: Context, titleId: String) {
        val library = validateRuntimeLibrary(context.filesDir, titleId)
        val intent = Intent(context, NativeActivity::class.java).apply {
            putExtra(EXTRA_RUNTIME_LIBRARY, library.absolutePath)
            putExtra("com.ngr.app.SWITCH_TITLE_ID", titleId.uppercase())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
