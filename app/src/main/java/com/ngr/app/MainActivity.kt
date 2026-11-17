package com.ngr.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Bundle
import android.view.InputDevice
import android.hardware.input.InputManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var inputManager: InputManager
    private lateinit var packageManager: NgrPackPackageManager
    private lateinit var packageStatus: TextView
    private lateinit var packageList: LinearLayout
    private val packageWorker = Executors.newSingleThreadExecutor()

    companion object {
        private const val REQUEST_NGRPACK = 4102
        private const val REQUEST_EXPORT_LOG = 4103
        private const val REQUEST_GAME_ZIP = 4104
        private const val REQUEST_GAME_FOLDER = 4105
        init { System.loadLibrary("ngr_runtime") }
    }

    private external fun nativeRuntimeInfo(): String
    private external fun nativeVulkanProbe(): String
    private external fun nativeCapabilityReport(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NgrLogger.initialize(this)
        NgrLogger.info("app", "MainActivity.onCreate")
        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        packageManager = NgrPackPackageManager(File(filesDir, "ngr-packages"))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
        }
        root.addView(TextView(this).apply { text = "Native Game Recompiler"; textSize = 24f })
        status = TextView(this).apply { textSize = 14f }
        root.addView(status)
        root.addView(TextView(this).apply { text = "\nVulkan devices"; textSize = 18f })
        NgrLogger.info("vulkan", "Starting Vulkan physical-device enumeration")
        val vulkanReport = nativeVulkanProbe()
        NgrLogger.info("vulkan", "Vulkan probe completed; reportChars=${vulkanReport.length}")
        root.addView(TextView(this).apply { text = vulkanReport; textSize = 13f })
        root.addView(TextView(this).apply { text = "\nRuntime capabilities"; textSize = 18f })
        root.addView(TextView(this).apply { text = nativeCapabilityReport(); textSize = 12f })
        root.addView(TextView(this).apply { text = "\nInput devices"; textSize = 18f })
        devices = TextView(this).apply { textSize = 14f }
        root.addView(devices)

        root.addView(TextView(this).apply { text = "\nNGRPACK packages"; textSize = 18f })
        root.addView(Button(this).apply {
            text = "Import game folder"
            setOnClickListener { chooseGameFolder() }
        })
        root.addView(Button(this).apply {
            text = "Import ordinary game ZIP"
            setOnClickListener { chooseGameZip() }
        })
        root.addView(Button(this).apply {
            text = "Import / Update NGRPACK"
            setOnClickListener { choosePackageArchive() }
        })
        packageStatus = TextView(this).apply { textSize = 12f }
        root.addView(packageStatus)
        packageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(packageList)

        root.addView(TextView(this).apply { text = "\nDiagnostics"; textSize = 18f })
        root.addView(Button(this).apply { text = "View runtime log"; setOnClickListener { showLogs() } })
        root.addView(Button(this).apply { text = "Save log to storage"; setOnClickListener { saveLogs() } })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refresh()
        refreshPackages()
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        NgrLogger.info("app", "MainActivity.onDestroy")
        packageWorker.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        NgrLogger.info("runtime", "Refreshing runtime information and Vulkan probe")
        status.text = nativeRuntimeInfo()
        NgrLogger.info("runtime", "Runtime info refreshed; chars=${status.text.length}")
        val found = inputManager.inputDeviceIds.asSequence().mapNotNull { id: Int ->
            inputManager.getInputDevice(id)?.takeIf {
                it.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
            }?.let { "• ${it.name} (id=${it.id})" }
        }.toList()
        devices.text = if (found.isEmpty()) "No gamepad detected" else found.joinToString("\n")
        NgrLogger.info("input", "Enumerated ${found.size} gamepad/joystick devices: ${found.joinToString("; ")}")
    }

    private fun choosePackageArchive() {
        chooseZipDocument(REQUEST_NGRPACK, "Opening NGRPACK document picker")
    }

    private fun chooseGameZip() {
        chooseZipDocument(REQUEST_GAME_ZIP, "Opening ordinary game ZIP picker")
    }

    private fun chooseZipDocument(requestCode: Int, logMessage: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
            ))
        }
        NgrLogger.info("package.import", logMessage)
        startActivityForResult(intent, requestCode)
    }

    private fun chooseGameFolder() {
        NgrLogger.info("package.import", "Opening game folder picker")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_GAME_FOLDER)
    }

    @Deprecated("Uses the platform document picker callback for compatibility with Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXPORT_LOG) {
            if (resultCode == RESULT_OK) {
                val destination = data?.data ?: return
                runCatching {
                    val bytes = NgrLogger.recentText(2_500_000).toByteArray(Charsets.UTF_8)
                    contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Unable to open destination")
                    Toast.makeText(this, "Log saved (${bytes.size} bytes)", Toast.LENGTH_SHORT).show()
                    NgrLogger.info("diagnostics", "Exported runtime log; bytes=${bytes.size}")
                }.onFailure {
                    NgrLogger.error("diagnostics", "Log export failed", it)
                    Toast.makeText(this, "Log export failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        if (requestCode == REQUEST_GAME_FOLDER) {
            if (resultCode == RESULT_OK) data?.data?.let { importGameFolder(it) }
            return
        }
        if (requestCode == REQUEST_GAME_ZIP) {
            if (resultCode == RESULT_OK) data?.data?.let { importOrdinaryGameZip(it) }
            return
        }
        if (requestCode != REQUEST_NGRPACK || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val pickedName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        NgrLogger.info("package.import", "Selected archive name=${pickedName ?: "unknown"}; scheme=${uri.scheme ?: "unknown"}")
        packageStatus.text = "Copying and inspecting package archive…"
        packageWorker.execute {
            var temporary: File? = null
            val result = try {
                temporary = File.createTempFile("ngr-import-", ".ngrpack", cacheDir)
                copyPickedArchive(uri, temporary)
                NgrLogger.info("package.import", "Private archive copy ready; bytes=${temporary.length()}")
                packageManager.install(temporary)
            } catch (e: Exception) {
                NgrLogger.error("package.import", "Import pipeline exception", e)
                NgrPackPackageManager.OperationResult(
                    false,
                    "Package import failed",
                    errors = listOf(e.message ?: e.javaClass.simpleName)
                )
            } finally {
                temporary?.delete()
            }
            NgrLogger.info("package.import", "Result success=${result.success}; message=${result.message}; errors=${result.errors.joinToString(" | ")}")
            runOnUiThread {
                packageStatus.text = buildString {
                    append(result.message)
                    if (result.errors.isNotEmpty()) append("\n").append(result.errors.joinToString("\n"))
                }
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                refreshPackages()
            }
        }
    }

    private data class FolderImportBudget(
        var files: Int = 0,
        var bytes: Long = 0,
        val paths: MutableSet<String> = mutableSetOf()
    )

    private fun importOrdinaryGameZip(uri: Uri) {
        val pickedName = queryDisplayName(uri) ?: "game.zip"
        val gameName = pickedName.substringBeforeLast('.')
        NgrLogger.info("package.import", "Selected ordinary ZIP name=$pickedName")
        packageStatus.text = "Scanning game ZIP…"
        packageWorker.execute {
            var source: File? = null
            var generated: File? = null
            var ownershipTransferred = false
            try {
                source = File.createTempFile("ngr-game-source-", ".zip", cacheDir)
                copyPickedArchive(uri, source)
                val inspected = NgrPackArchiveInspector.inspect(source, requireManifest = false)
                if (!inspected.valid) throw IOException(inspected.errors.joinToString("; "))
                if (inspected.manifestText != null) {
                    val installed = packageManager.install(source)
                    runOnUiThread {
                        packageStatus.text = installed.errors.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: installed.message
                        Toast.makeText(this, installed.message, Toast.LENGTH_LONG).show()
                        refreshPackages()
                    }
                } else {
                    generated = File.createTempFile("ngr-generated-", ".ngrpack", cacheDir)
                    val builder = NgrGamePackageBuilder()
                    val built = builder.buildFromZip(source, generated, gameName)
                    if (!built.success) throw IOException(built.errors.joinToString("; ").ifBlank { built.message })
                    NgrLogger.info("package.builder", "ZIP converted; displayName=${built.displayName}; platform=${built.platform}; architecture=${built.architecture}; entryPoint=${built.entryPoint}; candidates=${built.candidates.size}")
                    val sourceFile = source
                    val generatedFile = generated
                    completeBuiltGameImport(generatedFile, built, { selected ->
                        builder.buildFromZip(sourceFile, generatedFile, gameName, selected)
                    }) {
                        sourceFile.delete()
                        generatedFile.delete()
                    }
                    ownershipTransferred = true
                }
            } catch (e: Exception) {
                NgrLogger.error("package.import", "Ordinary ZIP import failed", e)
                runOnUiThread {
                    packageStatus.text = "Game ZIP import failed\n${e.message}"
                    Toast.makeText(this, "Game ZIP import failed", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!ownershipTransferred) {
                    source?.delete()
                    generated?.delete()
                }
            }
        }
    }

    private fun importGameFolder(treeUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        packageStatus.text = "Scanning selected game folder…"
        packageWorker.execute {
            val copied = File(cacheDir, "ngr-folder-${UUID.randomUUID()}")
            var generated: File? = null
            var ownershipTransferred = false
            try {
                val root = DocumentFile.fromTreeUri(this, treeUri)
                    ?: throw IOException("Unable to open selected folder")
                if (!root.isDirectory) throw IOException("Selected item is not a directory")
                if (!copied.mkdirs()) throw IOException("Unable to create private import workspace")
                val budget = FolderImportBudget()
                copyDocumentDirectory(root, copied, "", budget)
                if (budget.files == 0) throw IOException("Selected folder contains no files")
                val displayName = root.name ?: "Imported game"
                NgrLogger.info("package.import", "Folder copied; files=${budget.files}; bytes=${budget.bytes}")
                generated = File.createTempFile("ngr-generated-", ".ngrpack", cacheDir)
                val builder = NgrGamePackageBuilder()
                val built = builder.buildFromFolder(copied, generated, displayName)
                if (!built.success) throw IOException(built.errors.joinToString("; ").ifBlank { built.message })
                NgrLogger.info("package.builder", "Folder converted; displayName=${built.displayName}; platform=${built.platform}; architecture=${built.architecture}; entryPoint=${built.entryPoint}; candidates=${built.candidates.size}")
                val generatedFile = generated
                completeBuiltGameImport(generatedFile, built, { selected ->
                    builder.buildFromFolder(copied, generatedFile, displayName, selected)
                }) {
                    generatedFile.delete()
                    copied.deleteRecursively()
                }
                ownershipTransferred = true
            } catch (e: Exception) {
                NgrLogger.error("package.import", "Game folder import failed", e)
                runOnUiThread {
                    packageStatus.text = "Game folder import failed\n${e.message}"
                    Toast.makeText(this, "Game folder import failed", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!ownershipTransferred) {
                    generated?.delete()
                    copied.deleteRecursively()
                }
            }
        }
    }

    private fun completeBuiltGameImport(
        generated: File,
        initialBuild: NgrGamePackageBuilder.Result,
        rebuildWithEntryPoint: (String) -> NgrGamePackageBuilder.Result,
        cleanup: () -> Unit
    ) {
        val candidates = initialBuild.candidates
        fun installSelected(entryPoint: String?) {
            packageWorker.execute {
                try {
                    if (entryPoint != null) {
                        val selectedBuild = rebuildWithEntryPoint(entryPoint)
                        if (!selectedBuild.success) throw IOException(
                            selectedBuild.errors.joinToString("; ").ifBlank { selectedBuild.message }
                        )
                        NgrLogger.info("package.builder", "User selected executable=${selectedBuild.entryPoint}; platform=${selectedBuild.platform}; architecture=${selectedBuild.architecture}")
                    }
                    val installed = packageManager.install(generated)
                    NgrLogger.info("package.import", "Selected game install success=${installed.success}; message=${installed.message}")
                    runOnUiThread {
                        packageStatus.text = buildString {
                            append(installed.message)
                            if (installed.errors.isNotEmpty()) append("\n").append(installed.errors.joinToString("\n"))
                        }
                        Toast.makeText(this, installed.message, Toast.LENGTH_LONG).show()
                        refreshPackages()
                    }
                } catch (e: Exception) {
                    NgrLogger.error("package.import", "Selected game install failed", e)
                    runOnUiThread {
                        packageStatus.text = "Game installation failed\n${e.message}"
                        Toast.makeText(this, "Game installation failed", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    cleanup()
                }
            }
        }

        runOnUiThread {
            if (candidates.size > 1) {
                val labels = candidates.map { "${it.path}\n${it.platform} · ${it.architecture}" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Choose game executable")
                    .setMessage("Several executable files were detected. Select the main game executable. Only this file will be configured as the entry point.")
                    .setItems(labels) { _, which -> installSelected(candidates[which].path) }
                    .setNegativeButton("Cancel") { _, _ -> cleanup() }
                    .setOnCancelListener { cleanup() }
                    .show()
            } else {
                installSelected(null)
            }
        }
    }

    private fun copyDocumentDirectory(
        source: DocumentFile,
        destination: File,
        relative: String,
        budget: FolderImportBudget
    ) {
        val children = source.listFiles().sortedBy { it.name ?: "" }
        for (child in children) {
            val name = child.name ?: throw IOException("A selected file has no name")
            if (name.isBlank() || name == "." || name == ".." || name.contains('/') ||
                name.contains('\\') || name.contains(':') || name.contains('\u0000'))
                throw IOException("Unsafe file name in selected folder")
            val childRelative = if (relative.isEmpty()) name else "$relative/$name"
            if (childRelative.count { it == '/' } >= 64)
                throw IOException("Game folder nesting exceeds 64 levels")
            if (childRelative.length > NgrPackArchiveInspector.Limits().maxPathChars)
                throw IOException("Game path exceeds maximum length: $childRelative")
            val key = childRelative.lowercase(Locale.ROOT)
            if (!budget.paths.add(key)) throw IOException("Duplicate or case-colliding game path: $childRelative")
            val target = File(destination, name)
            if (child.isDirectory) {
                if (!target.mkdirs() && !target.isDirectory) throw IOException("Unable to create game directory: $childRelative")
                copyDocumentDirectory(child, target, childRelative, budget)
            } else if (child.isFile) {
                budget.files++
                if (budget.files > NgrPackArchiveInspector.Limits().maxEntries - 1)
                    throw IOException("Game contains too many files")
                if (!target.parentFile!!.exists() && !target.parentFile!!.mkdirs())
                    throw IOException("Unable to create game file directory")
                val input = contentResolver.openInputStream(child.uri)
                    ?: throw IOException("Unable to read game file: $childRelative")
                var fileBytes = 0L
                input.use { sourceStream ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = sourceStream.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            budget.bytes += read
                            if (fileBytes > NgrPackArchiveInspector.Limits().maxFileBytes ||
                                budget.bytes > NgrPackArchiveInspector.Limits().maxTotalBytes)
                                throw IOException("Game folder exceeds import size limits")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } else {
                throw IOException("Unsupported document type: $childRelative")
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    private fun copyPickedArchive(uri: Uri, destination: File) {
        val maxBytes = NgrPackArchiveInspector.Limits().maxArchiveBytes
        NgrLogger.info("package.import", "Copying SAF stream; limit=$maxBytes bytes")
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Unable to open selected file")
        var copied = 0L
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > maxBytes) throw IllegalArgumentException("Selected archive exceeds size limit")
                    output.write(buffer, 0, count)
                }
            }
        }
        if (copied == 0L) throw IllegalArgumentException("Selected archive is empty")
        NgrLogger.info("package.import", "SAF stream copied; bytes=$copied")
    }

    private fun refreshPackages() {
        packageWorker.execute {
            NgrLogger.info("package.inventory", "Scanning installed packages")
            val inventory = packageManager.listInstalled()
            NgrLogger.info("package.inventory", "Inventory valid=${inventory.packages.size}; invalid=${inventory.invalidPackageIds.size}; errors=${inventory.errors.size}")
            runOnUiThread {
                packageList.removeAllViews()
                if (inventory.packages.isEmpty()) {
                    packageList.addView(TextView(this).apply { text = "No installed packages" })
                }
                inventory.packages.forEach { pkg ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                    }
                    val description = TextView(this).apply {
                        text = "${pkg.displayName}\n${pkg.packageId} · v${pkg.version}\n${pkg.backendId} ${pkg.backendVersion}"
                        textSize = 13f
                    }
                    row.addView(description, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(Button(this).apply {
                        text = "Details"
                        setOnClickListener { showPackageDetails(pkg) }
                    })
                    row.addView(Button(this).apply {
                        text = "Remove"
                        setOnClickListener { confirmPackageRemoval(pkg.packageId) }
                    })
                    packageList.addView(row)
                }
                inventory.invalidPackageIds.forEach { packageId ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                    }
                    row.addView(TextView(this).apply {
                        text = "Invalid package\n$packageId"
                        textSize = 13f
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(Button(this).apply {
                        text = "Remove"
                        setOnClickListener { confirmPackageRemoval(packageId) }
                    })
                    packageList.addView(row)
                }
                if (inventory.errors.isNotEmpty()) {
                    packageStatus.text = inventory.errors.joinToString("\n")
                }
            }
        }
    }

    private fun showPackageDetails(pkg: NgrPackPackageManager.InstalledPackage) {
        val source = listOfNotNull(pkg.sourcePlatform, pkg.sourceArchitecture).joinToString(" · ")
            .ifBlank { "Unknown / not declared" }
        val message = """
            Game: ${pkg.displayName}
            Package ID: ${pkg.packageId}
            Version: ${pkg.version}

            Entry point:
            ${pkg.entryPoint}

            Source: $source

            Backend: ${pkg.backendId} ${pkg.backendVersion}

            Package path:
            ${pkg.directory.absolutePath}

            Status: imported package data only. A compatible execution backend is not yet configured.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Game package details")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun confirmPackageRemoval(packageId: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove package")
            .setMessage("Remove $packageId from this device?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                packageStatus.text = "Removing $packageId…"
                packageWorker.execute {
                    NgrLogger.info("package.remove", "Removing packageId=$packageId")
                    val result = packageManager.remove(packageId)
                    NgrLogger.info("package.remove", "Removal success=${result.success}; message=${result.message}")
                    runOnUiThread {
                        packageStatus.text = result.message
                        refreshPackages()
                    }
                }
            }
            .show()
    }


    private fun showLogs() {
        NgrLogger.info("diagnostics", "Opened log viewer")
        AlertDialog.Builder(this)
            .setTitle("NGR runtime log")
            .setMessage(NgrLogger.recentText())
            .setPositiveButton("Close", null)
            .setNeutralButton("Save…") { _, _ -> saveLogs() }
            .show()
    }

    private fun saveLogs() {
        NgrLogger.info("diagnostics", "Requested log export")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "ngr-runtime-log.txt")
        }
        startActivityForResult(intent, REQUEST_EXPORT_LOG)
    }

    override fun onInputDeviceAdded(deviceId: Int) { NgrLogger.info("input", "Device added id=$deviceId"); refresh() }
    override fun onInputDeviceRemoved(deviceId: Int) { NgrLogger.info("input", "Device removed id=$deviceId"); refresh() }
    override fun onInputDeviceChanged(deviceId: Int) { NgrLogger.info("input", "Device changed id=$deviceId"); refresh() }
}
