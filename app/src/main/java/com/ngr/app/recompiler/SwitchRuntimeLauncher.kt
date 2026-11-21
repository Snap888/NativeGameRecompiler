package com.ngr.app.recompiler

import android.content.Context
import android.content.Intent
import android.app.NativeActivity
import java.io.File
import java.io.IOException

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
        return File(filesDir, "switch-ports/${titleId.uppercase()}/runtime/libdmhost.so")
    }

    @Throws(IOException::class)
    fun validateRuntimeLibrary(filesDir: File, titleId: String): File =
        SwitchRuntimePackageVerifier.verify(filesDir, titleId)

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
