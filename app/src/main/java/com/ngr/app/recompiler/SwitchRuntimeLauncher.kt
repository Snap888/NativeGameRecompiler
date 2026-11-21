package com.ngr.app.recompiler

import android.content.Context
import android.content.Intent
import android.app.NativeActivity
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Launches a title-specific nx2android NativeActivity runtime inside NGR.
 * The host .so is private app data; the large game data pack is in NGR's
 * app-specific external files directory. No per-game APK is installed.
 */
object SwitchRuntimeLauncher {
    const val EXTRA_RUNTIME_LIBRARY = "com.ngr.app.SWITCH_RUNTIME_LIBRARY"
    const val EXTRA_GAME_ROOT = "com.ngr.app.SWITCH_GAME_ROOT"
    const val EXTRA_TITLE_ID = "com.ngr.app.SWITCH_TITLE_ID"
    private val TITLE_ID = Regex("^[0-9A-Fa-f]{16}$")

    fun runtimeLibrary(filesDir: File, titleId: String): File {
        require(TITLE_ID.matches(titleId)) { "Invalid Switch title ID" }
        return File(filesDir, "switch-ports/${titleId.uppercase()}/runtime/libdmhost.so")
    }

    fun gameDataRoot(externalFilesDir: File, titleId: String): File {
        require(TITLE_ID.matches(titleId)) { "Invalid Switch title ID" }
        return File(externalFilesDir, "switch-ports/${titleId.uppercase()}/data")
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

    @Throws(IOException::class)
    fun validateGameDataRoot(externalFilesDir: File, titleId: String): File {
        val root = externalFilesDir.canonicalFile
        val expected = gameDataRoot(root, titleId)
        val canonical = expected.canonicalFile
        val prefix = File(root, "switch-ports").canonicalPath + File.separator
        require(canonical.path.startsWith(prefix)) { "Switch data path escapes NGR storage" }
        require(canonical == expected) { "Switch data root must not resolve through a symbolic link" }
        require(expected.isDirectory) { "Switch data pack is missing" }
        require(File(expected, "nx2a-data.json").isFile) { "nx2android data manifest is missing" }
        require(File(expected, "mods").isDirectory) { "Rewritten Switch modules are missing" }
        require(File(expected, "env.txt").isFile) { "Switch runtime environment file is missing" }
        return expected
    }

    fun launch(context: Context, titleId: String) {
        val library = validateRuntimeLibrary(context.filesDir, titleId)
        val externalRoot = context.getExternalFilesDir(null)
            ?: throw IOException("App-specific external storage is unavailable")
        val gameRoot = validateGameDataRoot(externalRoot, titleId)
        val intent = Intent(context, NativeActivity::class.java).apply {
            putExtra(EXTRA_RUNTIME_LIBRARY, library.absolutePath)
            putExtra(EXTRA_GAME_ROOT, gameRoot.absolutePath)
            putExtra(EXTRA_TITLE_ID, titleId.uppercase())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
