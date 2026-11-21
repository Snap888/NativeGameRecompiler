package com.ngr.app.recompiler

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/** Verifies the build receipt emitted by tools/switch/build_runtime.py before loading native code. */
object SwitchRuntimePackageVerifier {
    const val DONOR_REVISION = "f32b3d563f0615d0e825747285d4e38d780e4ebb"
    private val HASH = Regex("^[a-fA-F0-9]{64}$")

    @Throws(IOException::class)
    fun verify(filesDir: File, titleId: String): File {
        val root = filesDir.canonicalFile
        val packageDir = File(root, "switch-ports/${titleId.uppercase()}")
        val runtime = SwitchRuntimeLauncher.runtimeLibrary(root, titleId)
        val receipt = File(packageDir, "runtime-build.json")
        val portsRoot = File(root, "switch-ports").canonicalFile
        val prefix = portsRoot.path + File.separator

        require(packageDir.canonicalFile.path.startsWith(prefix)) { "Switch package escapes private storage" }
        require(packageDir.canonicalFile == packageDir) { "Switch package directory must not be a symbolic link" }
        require(runtime.canonicalFile == runtime && runtime.isFile && runtime.length() > 0L) {
            "Switch runtime library is missing or resolves through a symbolic link"
        }
        require(receipt.canonicalFile == receipt && receipt.isFile && receipt.length() in 2..65536) {
            "Switch runtime build receipt is missing or unsafe"
        }
        require(!Files.isSymbolicLink(runtime.toPath()) && !Files.isSymbolicLink(receipt.toPath())) {
            "Switch runtime files must not be symbolic links"
        }

        val json = receipt.readText(Charsets.UTF_8)
        val manifest = JSONObject(json)
        require(manifest.optString("format") == "ngr-switch-runtime-build") { "Unknown Switch runtime receipt" }
        require(manifest.optInt("schema_version") == 1) { "Unsupported Switch runtime receipt version" }
        require(manifest.optString("title_id").equals(titleId, ignoreCase = true)) { "Switch title ID mismatch" }
        require(manifest.optString("donor_revision") == DONOR_REVISION) { "Switch runtime donor revision mismatch" }
        require(manifest.optString("runtime_library") == "runtime/libdmhost.so") { "Unexpected runtime library path" }
        require(manifest.optString("abi") == "arm64-v8a") { "Switch runtime ABI mismatch" }
        require(manifest.optString("entry_point") == "ANativeActivity_onCreate") { "Switch runtime entry point mismatch" }
        require(!manifest.optBoolean("apk_generated", true)) { "Per-game APK output is not accepted by NGR" }

        val expectedHash = manifest.optString("runtime_sha256")
        val profileHash = manifest.optString("profile_header_sha256")
        require(HASH.matches(expectedHash) && HASH.matches(profileHash)) { "Invalid Switch runtime hash receipt" }
        val actualHash = sha256(runtime)
        require(MessageDigest.isEqual(
            expectedHash.lowercase().toByteArray(Charsets.US_ASCII),
            actualHash.toByteArray(Charsets.US_ASCII)
        )) { "Switch runtime library hash mismatch" }
        return runtime
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
