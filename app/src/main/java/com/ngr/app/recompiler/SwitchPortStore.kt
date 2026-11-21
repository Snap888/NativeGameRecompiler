package com.ngr.app.recompiler

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

data class InstalledSwitchPort(
    val titleId: String,
    val name: String,
    val version: String,
    val runtimeFile: File,
    val dataRoot: File
)

/** Imports a prepared nx2android port folder into NGR without installing another APK. */
class SwitchPortStore(private val context: Context) {
    private val titlePattern = Regex("^[0-9A-F]{16}$")
    private val maxDataFileBytes = 32L * 1024 * 1024 * 1024
    private val maxManifestBytes = 64 * 1024
    private val maxRuntimeBytes = 512L * 1024 * 1024

    fun listInstalled(): List<InstalledSwitchPort> {
        val internal = File(context.filesDir, "switch-ports")
        val external = context.getExternalFilesDir(null)?.let { File(it, "switch-ports") } ?: return emptyList()
        if (!internal.isDirectory || !external.isDirectory) return emptyList()
        return internal.listFiles().orEmpty().mapNotNull { dir ->
            val id = dir.name.uppercase(Locale.ROOT)
            if (!titlePattern.matches(id)) return@mapNotNull null
            val runtime = File(dir, "runtime/libdmhost.so")
            val data = File(external, "$id/data")
            if (!runtime.isFile || !data.isDirectory || !File(data, "nx2a-data.json").isFile ||
                !File(data, "mods").isDirectory || !File(data, "env.txt").isFile ||
                !File(data, "assets/overlay/theme.tsv").isFile || !File(data, "assets/font/ui.bin").isFile) return@mapNotNull null
            val manifest = readJson(File(data, "nx2a-data.json")) ?: return@mapNotNull null
            if (manifest.optString("title_id").uppercase(Locale.ROOT) != id) return@mapNotNull null
            InstalledSwitchPort(id, manifest.optString("name", "Nintendo Switch game"),
                manifest.optString("display", manifest.optString("version", "")), runtime, data)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    @Throws(IOException::class)
    fun installFromTree(treeUri: Uri): InstalledSwitchPort {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Selected folder is unavailable")
        if (!tree.isDirectory) throw IOException("Select a prepared Switch port folder")
        val runtimeDir = tree.findFile("runtime")?.takeIf { it.isDirectory }
            ?: throw IOException("runtime/ folder is missing")
        val dataDir = tree.findFile("data")?.takeIf { it.isDirectory }
            ?: throw IOException("data/ folder is missing")
        val runtimeDoc = runtimeDir.findFile("libdmhost.so")?.takeIf { it.isFile }
            ?: throw IOException("runtime/libdmhost.so is missing")
        val manifestDoc = dataDir.findFile("nx2a-data.json")?.takeIf { it.isFile }
            ?: throw IOException("data/nx2a-data.json is missing")
        val runtimeReceiptDoc = tree.findFile("runtime-build.json")?.takeIf { it.isFile }
            ?: throw IOException("runtime-build.json is missing")

        val manifestBytes = context.contentResolver.openInputStream(manifestDoc.uri)?.use { it.readBytesBounded(maxManifestBytes) }
            ?: throw IOException("Unable to read nx2a-data.json")
        val manifest = runCatching { JSONObject(String(manifestBytes, Charsets.UTF_8)) }
            .getOrElse { throw IOException("Invalid nx2a-data.json", it) }
        val titleId = manifest.optString("title_id").uppercase(Locale.ROOT)
        if (!titlePattern.matches(titleId)) throw IOException("Data pack has an invalid title_id")
        val runtimeReceiptBytes = context.contentResolver.openInputStream(runtimeReceiptDoc.uri)?.use { it.readBytesBounded(maxManifestBytes) }
            ?: throw IOException("Unable to read runtime-build.json")
        val runtimeReceipt = runCatching { JSONObject(String(runtimeReceiptBytes, Charsets.UTF_8)) }
            .getOrElse { throw IOException("Invalid runtime-build.json", it) }
        if (runtimeReceipt.optString("format") != "ngr-switch-runtime-build" ||
            runtimeReceipt.optInt("schema_version") != 1 ||
            runtimeReceipt.optString("title_id").uppercase(Locale.ROOT) != titleId ||
            runtimeReceipt.optString("donor_revision") != SwitchRuntimePackageVerifier.DONOR_REVISION ||
            runtimeReceipt.optString("runtime_library") != "runtime/libdmhost.so" ||
            runtimeReceipt.optString("abi") != "arm64-v8a" ||
            runtimeReceipt.optString("entry_point") != "ANativeActivity_onCreate" ||
            runtimeReceipt.optBoolean("apk_generated", true)) throw IOException("Runtime receipt does not match this NGR Switch build")
        val expectedHash = runtimeReceipt.optString("runtime_sha256").lowercase(Locale.ROOT)
        if (!Regex("^[0-9a-f]{64}$").matches(expectedHash) ||
            !Regex("^[0-9a-fA-F]{64}$").matches(runtimeReceipt.optString("profile_header_sha256"))) {
            throw IOException("Runtime SHA-256/profile receipt is missing")
        }
        if (dataDir.findFile("mods")?.isDirectory != true) throw IOException("Rewritten modules folder is missing")
        if (dataDir.findFile("env.txt")?.isFile != true) throw IOException("env.txt is missing")
        val assets = dataDir.findFile("assets")?.takeIf { it.isDirectory }
            ?: throw IOException("NGR per-title assets folder is missing")
        if (assets.findFile("overlay")?.findFile("theme.tsv")?.isFile != true ||
            assets.findFile("font")?.findFile("ui.bin")?.isFile != true) {
            throw IOException("Switch gamepad theme or overlay font is missing")
        }

        val internalRoot = File(context.filesDir, "switch-ports")
        val externalBase = context.getExternalFilesDir(null) ?: throw IOException("External app storage is unavailable")
        val externalRoot = File(externalBase, "switch-ports")
        internalRoot.mkdirs(); externalRoot.mkdirs()
        requireSafeDirectory(internalRoot); requireSafeDirectory(externalRoot)

        val runtimeTemp = File(internalRoot, ".$titleId.partial")
        val dataTemp = File(externalRoot, ".$titleId.partial")
        deleteTree(runtimeTemp); deleteTree(dataTemp)
        runtimeTemp.mkdirs(); dataTemp.mkdirs()
        try {
            val runtimeOut = File(runtimeTemp, "runtime/libdmhost.so")
            runtimeOut.parentFile?.mkdirs()
            copyDocument(runtimeDoc, runtimeOut, maxRuntimeBytes)
            validateElf64Arm64(runtimeOut)
            if (sha256(runtimeOut) != expectedHash) throw IOException("Switch runtime SHA-256 mismatch")
            File(runtimeTemp, "runtime-build.json").writeBytes(runtimeReceiptBytes)
            tree.findFile("NX2ANDROID-LICENSE")?.takeIf { it.isFile }?.let { copyDocument(it, File(runtimeTemp, "NX2ANDROID-LICENSE"), 1024L * 1024) }

            val dataOut = File(dataTemp, "data")
            val copyBudget = SwitchPortCopyBudget()
            copyTree(dataDir, dataOut, copyBudget, 0)
            val checked = readJson(File(dataOut, "nx2a-data.json")) ?: throw IOException("Copied data manifest is invalid")
            if (checked.optString("title_id").uppercase(Locale.ROOT) != titleId) throw IOException("Title ID changed while importing")

            val oldRuntime = File(internalRoot, titleId)
            val oldData = File(externalRoot, titleId)
            val runtimeBackup = File(internalRoot, ".$titleId.backup")
            val dataBackup = File(externalRoot, ".$titleId.backup")
            deleteTree(runtimeBackup); deleteTree(dataBackup)
            if (oldRuntime.exists() && !oldRuntime.renameTo(runtimeBackup)) throw IOException("Unable to stage existing runtime")
            if (oldData.exists() && !oldData.renameTo(dataBackup)) {
                if (runtimeBackup.exists()) runtimeBackup.renameTo(oldRuntime)
                throw IOException("Unable to stage existing data pack")
            }
            val runtimeMoved = runtimeTemp.renameTo(oldRuntime)
            val dataMoved = runtimeMoved && dataTemp.renameTo(oldData)
            if (!dataMoved) {
                if (runtimeMoved) deleteTree(oldRuntime)
                if (runtimeBackup.exists()) runtimeBackup.renameTo(oldRuntime)
                if (dataBackup.exists()) dataBackup.renameTo(oldData)
                throw IOException("Unable to install Switch port")
            }
            deleteTree(runtimeBackup); deleteTree(dataBackup)
            return InstalledSwitchPort(titleId, checked.optString("name", "Nintendo Switch game"),
                checked.optString("display", checked.optString("version", "")),
                File(oldRuntime, "runtime/libdmhost.so"), File(oldData, "data"))
        } catch (t: Throwable) {
            deleteTree(runtimeTemp); deleteTree(dataTemp)
            if (t is IOException) throw t
            throw IOException(t.message ?: "Switch port import failed", t)
        }
    }

    private fun copyTree(
        source: DocumentFile,
        destination: File,
        budget: SwitchPortCopyBudget,
        depth: Int
    ) {
        budget.validateDepth(depth)
        if (!source.isDirectory) throw IOException("Data pack root is not a folder")
        if (destination.exists() || java.nio.file.Files.isSymbolicLink(destination.toPath())) {
            throw IOException("Switch data-pack destination already exists or is unsafe")
        }
        if (!destination.mkdirs()) throw IOException("Unable to create data directory")
        for (child in source.listFiles()) {
            val name = child.name ?: throw IOException("Unnamed item in data pack")
            if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
                name.contains('\\\\') || name.any { it.code < 0x20 }) throw IOException("Unsafe data-pack path")
            val target = File(destination, name)
            if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) {
                throw IOException("Duplicate or unsafe data-pack entry: $name")
            }
            if (child.isDirectory) {
                budget.recordEntry(isFile = false)
                copyTree(child, target, budget, depth + 1)
            } else if (child.isFile) {
                budget.recordEntry(isFile = true)
                copyDocument(child, target, maxDataFileBytes, budget)
            } else {
                throw IOException("Unsupported item in Switch data pack: $name")
            }
        }
    }

    private fun copyDocument(
        document: DocumentFile,
        target: File,
        maxBytes: Long,
        budget: SwitchPortCopyBudget? = null
    ): Long {
        val input = context.contentResolver.openInputStream(document.uri)
            ?: throw IOException("Unable to open " + document.name)
        var total = 0L
        target.parentFile?.mkdirs()
        if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) {
            input.close()
            throw IOException("Refusing to overwrite a Switch package file")
        }
        FileOutputStream(target).use { output ->
            input.use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    if (total > maxBytes - n.toLong()) throw IOException("File exceeds permitted size")
                    total += n
                    budget?.recordBytes(total, n)
                    output.write(buffer, 0, n)
                }
                output.fd.sync()
            }
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateElf64Arm64(file: File) {
        val header = ByteArray(20)
        val read = file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val n = input.read(header, offset, header.size - offset)
                if (n < 0) break
                offset += n
            }
            offset
        }
        if (read < 20 || header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte() ||
            header[4] != 2.toByte() || header[5] != 1.toByte()) throw IOException("Runtime is not a little-endian ELF64 library")
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        if (machine != 183) throw IOException("Runtime ELF is not AArch64")
    }

    private fun readJson(file: File): JSONObject? = runCatching {
        if (!file.isFile || file.length() !in 1..maxManifestBytes.toLong()) return null
        JSONObject(file.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun requireSafeDirectory(file: File) {
        if (java.nio.file.Files.isSymbolicLink(file.toPath()) || !file.isDirectory) throw IOException("Unsafe Switch storage directory")
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        if (file.isDirectory && !java.nio.file.Files.isSymbolicLink(file.toPath())) file.listFiles().orEmpty().forEach(::deleteTree)
        file.delete()
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) throw IOException("Manifest is too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
