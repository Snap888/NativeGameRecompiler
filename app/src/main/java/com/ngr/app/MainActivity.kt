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
import java.io.File
import java.io.FileOutputStream
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
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
            ))
        }
        NgrLogger.info("package.import", "Opening document picker")
        startActivityForResult(intent, REQUEST_NGRPACK)
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
