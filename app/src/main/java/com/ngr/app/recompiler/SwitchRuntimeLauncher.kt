package com.ngr.app.recompiler

import android.content.Context
import android.content.Intent
import android.app.NativeActivity
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.security.MessageDigest
import org.json.JSONObject

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
        return SwitchRuntimePackageVerifier.verify(root, titleId)
    }

    @Throws(IOException::class)
    fun validateGameDataRoot(
        externalFilesDir: File,
        titleId: String,
        expectedProfileHash: String? = null,
        expectedDataManifestHash: String? = null
    ): File {
        val root = externalFilesDir.canonicalFile
        val expected = gameDataRoot(root, titleId)
        val canonical = expected.canonicalFile
        val prefix = File(root, "switch-ports").canonicalPath + File.separator
        require(canonical.path.startsWith(prefix)) { "Switch data path escapes NGR storage" }
        require(canonical == expected) { "Switch data root must not resolve through a symbolic link" }
        require(expected.isDirectory) { "Switch data pack is missing" }
        val manifestFile = File(expected, "nx2a-data.json")
        require(manifestFile.isFile && manifestFile.length() in 2..65536 &&
            !Files.isSymbolicLink(manifestFile.toPath())) { "nx2android data manifest is missing or unsafe" }
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.optString("title_id").uppercase(Locale.ROOT) == titleId.uppercase(Locale.ROOT)) {
            "Switch data manifest title ID does not match the selected runtime"
        }
        if (expectedProfileHash != null) {
            require(manifest.optString("profile_h").equals(expectedProfileHash, ignoreCase = true)) {
                "Switch data pack was built for a different game profile"
            }
        }
        if (expectedDataManifestHash != null) {
            require(HASH.matches(expectedDataManifestHash) && sha256(manifestFile) == expectedDataManifestHash.lowercase(Locale.ROOT)) {
                "Switch data manifest hash does not match the runtime receipt"
            }
        }
        require(File(expected, "mods").isDirectory) { "Rewritten Switch modules are missing" }
        require(File(expected, "env.txt").isFile) { "Switch runtime environment file is missing" }
        require(File(expected, "assets/overlay/theme.tsv").isFile &&
            File(expected, "assets/font/ui.bin").isFile) { "Switch per-title overlay assets are missing" }
        return expected
    }

    private val HASH = Regex("^[0-9a-fA-F]{64}$")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun launch(context: Context, titleId: String) {
        val library = validateRuntimeLibrary(context.filesDir, titleId)
        val externalRoot = context.getExternalFilesDir(null)
            ?: throw IOException("App-specific external storage is unavailable")
        val runtimeRoot = library.parentFile.parentFile
        val receipt = JSONObject(File(runtimeRoot, "runtime-build.json").readText(Charsets.UTF_8))
        val expectedProfileHash = receipt.optString("profile_header_sha256")
        val expectedDataManifestHash = receipt.optString("data_manifest_sha256")
        val gameRoot = validateGameDataRoot(externalRoot, titleId, expectedProfileHash, expectedDataManifestHash)
        val intent = Intent(context, NativeActivity::class.java).apply {
            putExtra(EXTRA_RUNTIME_LIBRARY, library.absolutePath)
            putExtra(EXTRA_GAME_ROOT, gameRoot.absolutePath)
            putExtra(EXTRA_TITLE_ID, titleId.uppercase())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
