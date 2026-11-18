package com.ngr.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.InputDevice
import android.view.View
import android.text.InputType
import android.hardware.input.InputManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
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
    private lateinit var profileStore: NgrEnvironmentProfileStore
    private lateinit var profileStatus: TextView
    private lateinit var profileList: LinearLayout
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
        profileStore = NgrEnvironmentProfileStore(File(filesDir, "ngr-runtime"))

        window.statusBarColor = Color.rgb(15, 18, 24)
        window.navigationBarColor = Color.rgb(15, 18, 24)
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.rgb(15, 18, 24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.rgb(15, 18, 24))
            addView(root)
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "NGR"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        val brandSub = TextView(this).apply {
            text = "  NATIVE GAME RECOMPILER"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(142, 154, 174))
            typeface = Typeface.DEFAULT_BOLD
        }
        brandRow.addView(brand)
        brandRow.addView(brandSub)
        root.addView(brandRow)
        root.addView(TextView(this).apply {
            text = "Your games. Your runtime. Your platform."
            textSize = 13f
            setTextColor(Color.rgb(151, 163, 182))
            setPadding(0, dp(4), 0, dp(18))
        })

        val hero = createCard()
        hero.addView(TextView(this).apply {
            text = "GAME LIBRARY"
            textSize = 10f
            letterSpacing = 0.16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
        })
        hero.addView(TextView(this).apply {
            text = "Ready to build\nyour game space?"
            textSize = 25f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(5))
        })
        hero.addView(TextView(this).apply {
            text = "Import a Windows or Linux game folder, ZIP, or an existing NGR package."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        status = readoutText("Checking runtime status…")
        hero.addView(status)
        val importActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        importActions.addView(primaryButton("＋  Import game folder") { chooseGameFolder() })
        importActions.addView(secondaryButton("Import game ZIP") { chooseGameZip() })
        importActions.addView(secondaryButton("Import / update NGRPACK") { choosePackageArchive() })
        hero.addView(importActions)
        root.addView(hero, sectionParams())

        addSection(root, "LIBRARY", "Installed games")
        packageStatus = mutedText()
        root.addView(packageStatus)
        packageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(packageList)

        addSection(root, "COMPATIBILITY", "Runtime environments")
        val runtimeCard = createCard()
        runtimeCard.addView(TextView(this).apply {
            text = "Configure Wine / Proton layers, CPU translation and graphics providers in reusable profiles."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })        runtimeCard.addView(primaryButton("＋  Create environment profile") { showCreateEnvironmentProfileDialog() })
        profileStatus = mutedText()
        runtimeCard.addView(profileStatus)
        profileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        runtimeCard.addView(profileList)
        root.addView(runtimeCard, sectionParams())

        addSection(root, "DEVICE", "Hardware & graphics")
        val hardwareCard = createCard()
        val gpuHeader = TextView(this).apply {
            text = "VULKAN DEVICE"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
        }
        hardwareCard.addView(gpuHeader)
        NgrLogger.info("vulkan", "Starting Vulkan physical-device enumeration")
        val vulkanReport = nativeVulkanProbe()
        NgrLogger.info("vulkan", "Vulkan probe completed; reportChars=${vulkanReport.length}")
        hardwareCard.addView(readoutText(vulkanReport))
        val divider = View(this).apply { setBackgroundColor(Color.rgb(48, 57, 72)) }
        hardwareCard.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        hardwareCard.addView(TextView(this).apply {
            text = "RUNTIME CAPABILITIES"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
        })
        hardwareCard.addView(readoutText(nativeCapabilityReport()))
        hardwareCard.addView(TextView(this).apply {
            text = "CONNECTED CONTROLLERS"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
            setPadding(0, dp(14), 0, dp(5))
        })
        devices = readoutText("Scanning input devices…")
        hardwareCard.addView(devices)
        root.addView(hardwareCard, sectionParams())

        addSection(root, "SUPPORT", "Diagnostics")
        val diagnostics = createCard()
        diagnostics.addView(TextView(this).apply {
            text = "Runtime logs help investigate device, Vulkan, import and package issues."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        val diagnosticActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        diagnosticActions.addView(secondaryButton("View log") { showLogs() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        diagnosticActions.addView(secondaryButton("Export log") { saveLogs() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        diagnostics.addView(diagnosticActions)
        root.addView(diagnostics, sectionParams())

        setContentView(scroll)
        refresh()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.rgb(25, 30, 39))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(43, 52, 67))
        }
    }

    private fun sectionParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }

    private fun addSection(root: LinearLayout, eyebrow: String, heading: String) {
        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(10), 0, dp(9))
        }
        label.addView(TextView(this).apply {
            text = eyebrow
            textSize = 9f
            letterSpacing = 0.18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
        })
        label.addView(TextView(this).apply {
            text = heading
            textSize = 19f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, 0)
        })
        root.addView(label)
    }

    private fun mutedText() = TextView(this).apply {
        textSize = 12f
        setTextColor(Color.rgb(143, 155, 174))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun readoutText(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(Color.rgb(196, 207, 222))
        setPadding(0, dp(7), 0, 0)
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(39, 112, 220))
            cornerRadius = dp(12).toFloat()
        }
        setPadding(dp(12), 0, dp(12), 0)
        minHeight = dp(48)
        setOnClickListener { action() }
    }

    private fun secondaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.rgb(213, 222, 235))
        background = GradientDrawable().apply {
            setColor(Color.rgb(35, 43, 56))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(57, 69, 88))
        }
        minHeight = dp(46)
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refresh()
        refreshPackages()
        refreshProfiles()
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()    }

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
    private fun showCreateEnvironmentProfileDialog() {
        val nameInput = EditText(this).apply {
            hint = "Profile name"
            setSingleLine(true)
        }
        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val standard = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Windows x86_64 — Box64"
            isChecked = true
        }
        val arm64ec = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Windows ARM64EC — FEXCore"
        }
        modeGroup.addView(standard)
        modeGroup.addView(arm64ec)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
            addView(nameInput)
            addView(TextView(this@MainActivity).apply { text = "Architecture mode"; textSize = 14f })
            addView(modeGroup)
        }
        AlertDialog.Builder(this)
            .setTitle("New runtime profile")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val displayName = nameInput.text.toString().trim()
                if (displayName.isEmpty()) {
                    Toast.makeText(this, "Enter a profile name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val profileId = "env-" + UUID.randomUUID().toString().take(8)
                val profile = if (modeGroup.checkedRadioButtonId == arm64ec.id) {
                    NgrEnvironmentProfile(
                        profileId = profileId,
                        displayName = displayName,
                        containerMode = NgrEnvironmentProfile.ContainerMode.ARM64EC,
                        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE,
                        fexCoreComponentId = "fexcore-unselected"
                    )
                } else {
                    NgrEnvironmentProfile(
                        profileId = profileId,
                        displayName = displayName,
                        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
                        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64
                    )
                }
                val result = profileStore.save(profile)
                if (result.success) {
                    Toast.makeText(this, "Environment profile created", Toast.LENGTH_SHORT).show()
                    refreshProfiles()
                } else {
                    Toast.makeText(this, "Profile creation failed: ${result.error}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun refreshProfiles() {
        if (!::profileStore.isInitialized || !::profileList.isInitialized) return
        val loaded = profileStore.loadAll()
        profileStatus.text = buildString {
            append("${loaded.profiles.size} saved profile(s)")
            if (loaded.errors.isNotEmpty()) append("\n${loaded.errors.size} invalid profile file(s) — see runtime log")
        }
        loaded.errors.forEach { NgrLogger.warn("environment.profile", it) }
        profileList.removeAllViews()
        loaded.profiles.forEach { profile ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val readiness = profile.validateLaunchReady()
            item.addView(TextView(this).apply {
                text = "${profile.displayName}\n${profile.containerMode} · ${profile.cpuTranslator}\n" +
                    if (readiness.isEmpty()) "Ready for component resolution" else "Draft — ${readiness.joinToString("; ")}"
                textSize = 13f
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener { showEditEnvironmentProfileDialog(profile) }
            })
            actions.addView(Button(this).apply {
                text = "Details"
                setOnClickListener { showEnvironmentProfileDetails(profile) }
            })
            actions.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete profile?")
                        .setMessage(profile.displayName)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            val result = profileStore.delete(profile.profileId)
                            if (!result.success) Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_LONG).show()
                            refreshProfiles()
                        }
                        .show()
                }
            })
            item.addView(actions)
            profileList.addView(item)
        }
    }

    private fun showEditEnvironmentProfileDialog(profile: NgrEnvironmentProfile) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 8) }
        fun field(label: String, value: String?, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val input = EditText(this).apply {
                setSingleLine(true)
                setText(value.orEmpty())
                this.inputType = inputType
            }
            form.addView(TextView(this).apply { text = label; textSize = 12f })
            form.addView(input)
            return input
        }
        val wine = field("Wine / Proton layer ID", profile.wineLayerId)
        val box64 = field("Box64 component ID", profile.box64ComponentId)
        val box86 = field("Box86 component ID (optional)", profile.box86ComponentId)
        val fex = field("FEXCore component ID", profile.fexCoreComponentId)
        val fexUnix = field("FEX UnixLibs component ID", profile.fexUnixLibsComponentId)
        val wow = field("WOWBox64 component ID", profile.wowBox64ComponentId)
        val dxvk = field("DXVK component ID", profile.dxvkComponentId)        val vkd3d = field("VKD3D-Proton component ID", profile.vkd3dComponentId)
        val turnip = field("Turnip component ID", profile.turnipComponentId)
        val componentVersions = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 5
            setText(profile.componentVersions.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" })
            hint = "component-id=version, one per line"
        }
        form.addView(TextView(this).apply { text = "Pinned component versions"; textSize = 12f })
        form.addView(componentVersions)
        val width = field("Desktop width", profile.desktopWidth.toString(), InputType.TYPE_CLASS_NUMBER)
        val height = field("Desktop height", profile.desktopHeight.toString(), InputType.TYPE_CLASS_NUMBER)
        val dpi = field("DPI", profile.dpi.toString(), InputType.TYPE_CLASS_NUMBER)
        val displayGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val x11 = RadioButton(this).apply { id = View.generateViewId(); text = "X11"; isChecked = profile.displayBackend == NgrEnvironmentProfile.DisplayBackend.X11 }
        val wayland = RadioButton(this).apply { id = View.generateViewId(); text = "Wayland"; isChecked = profile.displayBackend == NgrEnvironmentProfile.DisplayBackend.WAYLAND }
        displayGroup.addView(x11); displayGroup.addView(wayland)
        form.addView(TextView(this).apply { text = "Display backend"; textSize = 12f })
        form.addView(displayGroup)        val graphicsGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val systemVk = RadioButton(this).apply { id = View.generateViewId(); text = "System Vulkan"; isChecked = profile.graphicsProvider == NgrEnvironmentProfile.GraphicsProvider.SYSTEM_VULKAN }
        val turnipVk = RadioButton(this).apply { id = View.generateViewId(); text = "Turnip"; isChecked = profile.graphicsProvider == NgrEnvironmentProfile.GraphicsProvider.TURNIP }
        graphicsGroup.addView(systemVk); graphicsGroup.addView(turnipVk)
        form.addView(TextView(this).apply { text = "Graphics provider"; textSize = 12f })
        form.addView(graphicsGroup)
        val waylandWine = CheckBox(this).apply { text = "Selected Wine layer supports Wayland"; isChecked = profile.waylandWineSupported }
        val waylandTurnip = CheckBox(this).apply { text = "Selected Turnip build supports Wayland"; isChecked = profile.waylandTurnipSupported }
        form.addView(waylandWine); form.addView(waylandTurnip)
        val reshadeEnabled = CheckBox(this).apply { text = "Enable vkBasalt / ReShade-compatible effects"; isChecked = profile.reshade.enabled }
        form.addView(reshadeEnabled)
        val reshadeComponent = field("vkBasalt component ID", profile.reshade.componentId)
        val reshadePreset = field("Preset path (relative)", profile.reshade.presetPath)
        val shaderPackages = field("Shader package IDs (comma-separated)", profile.reshade.shaderPackageIds.joinToString(","))
        val envVars = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 6
            setText(profile.environmentVariables.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" })
            hint = "KEY=VALUE, one per line"
        }
        form.addView(TextView(this).apply { text = "Environment variables"; textSize = 12f })
        form.addView(envVars)
        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle("Edit ${profile.displayName}")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val parsedEnv = linkedMapOf<String, String>()
                    envVars.text.toString().lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) return@forEach
                        val split = trimmed.indexOf('=')
                        require(split > 0) { "Environment variables must use KEY=VALUE" }
                        val key = trimmed.substring(0, split).trim()
                        require(!parsedEnv.containsKey(key)) { "Duplicate environment variable: $key" }
                        parsedEnv[key] = trimmed.substring(split + 1)
                    }
                    val parsedVersions = linkedMapOf<String, String>()
                    componentVersions.text.toString().lineSequence().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) return@forEach
                        val split = trimmed.indexOf('=')
                        require(split > 0) { "Component versions must use component-id=version" }
                        val id = trimmed.substring(0, split).trim()
                        require(!parsedVersions.containsKey(id)) { "Duplicate component version pin: $id" }
                        parsedVersions[id] = trimmed.substring(split + 1).trim()
                    }
                    val standardMode = profile.containerMode == NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64
                    val updated = profile.copy(
                        componentVersions = parsedVersions,
                        wineLayerId = wine.text.toString().trim().ifEmpty { null },
                        box64ComponentId = if (standardMode) box64.text.toString().trim().ifEmpty { null } else null,
                        box86ComponentId = if (standardMode) box86.text.toString().trim().ifEmpty { null } else null,
                        fexCoreComponentId = if (standardMode) null else fex.text.toString().trim().ifEmpty { null },
                        fexUnixLibsComponentId = if (standardMode) null else fexUnix.text.toString().trim().ifEmpty { null },
                        wowBox64ComponentId = if (standardMode) null else wow.text.toString().trim().ifEmpty { null },
                        dxvkComponentId = dxvk.text.toString().trim().ifEmpty { null },
                        vkd3dComponentId = vkd3d.text.toString().trim().ifEmpty { null },
                        turnipComponentId = turnip.text.toString().trim().ifEmpty { null },
                        graphicsProvider = if (graphicsGroup.checkedRadioButtonId == turnipVk.id) NgrEnvironmentProfile.GraphicsProvider.TURNIP else NgrEnvironmentProfile.GraphicsProvider.SYSTEM_VULKAN,
                        displayBackend = if (displayGroup.checkedRadioButtonId == wayland.id) NgrEnvironmentProfile.DisplayBackend.WAYLAND else NgrEnvironmentProfile.DisplayBackend.X11,
                        waylandWineSupported = waylandWine.isChecked,
                        waylandTurnipSupported = waylandTurnip.isChecked,
                        desktopWidth = width.text.toString().toInt(),
                        desktopHeight = height.text.toString().toInt(),
                        dpi = dpi.text.toString().toInt(),
                        reshade = NgrEnvironmentProfile.ReShadeProfile(
                            enabled = reshadeEnabled.isChecked,
                            componentId = reshadeComponent.text.toString().trim().ifEmpty { null },
                            presetPath = if (reshadeEnabled.isChecked) reshadePreset.text.toString().trim().ifEmpty { null } else null,
                            shaderPackageIds = shaderPackages.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        ),
                        environmentVariables = parsedEnv
                    )
                    val result = profileStore.save(updated)
                    if (!result.success) throw IllegalArgumentException(result.error ?: "Profile validation failed")
                    refreshProfiles()
                    Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Profile not saved: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
    private fun showEnvironmentProfileDetails(profile: NgrEnvironmentProfile) {
        val details = listOf(
            "Profile ID: ${profile.profileId}",
            "Mode: ${profile.containerMode}",
            "Wine/Proton: ${profile.wineLayerId ?: "not selected"}",
            "Box64: ${profile.box64ComponentId ?: "not selected"}",
            "Box86: ${profile.box86ComponentId ?: "not selected"}",
            "FEXCore: ${profile.fexCoreComponentId ?: "not selected"}",
            "FEX UnixLibs: ${profile.fexUnixLibsComponentId ?: "not selected"}",
            "WOWBox64: ${profile.wowBox64ComponentId ?: "not selected"}",
            "DXVK: ${profile.dxvkComponentId ?: "not selected"}",
            "VKD3D-Proton: ${profile.vkd3dComponentId ?: "not selected"}",
            "Graphics: ${profile.graphicsProvider} · Turnip: ${profile.turnipComponentId ?: "system/default"}",
            "Display: ${profile.displayBackend} · ${profile.desktopWidth}×${profile.desktopHeight} @ ${profile.dpi} DPI",
            "Audio: ${profile.audioBackend}",
            "vkBasalt/ReShade effects: ${if (profile.reshade.enabled) "enabled" else "disabled"}",
            "Pinned component versions: ${profile.componentVersions.size}",
            "Environment variables: ${profile.environmentVariables.size}",
            "Game overrides: ${profile.gameOverrides.size}",
            "Launch readiness: ${profile.validateLaunchReady().let { if (it.isEmpty()) "valid" else it.joinToString("; ") }}"
        ).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle(profile.displayName)
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
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
            ))        }
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
                    contentResolver.openOutputStream(destination)?.use { it.write(bytes) }                        ?: throw IllegalStateException("Unable to open destination")
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
                    val sourceFile = source ?: throw IOException("ZIP source disappeared before executable selection")
                    val generatedFile = generated ?: throw IOException("Generated package workspace is unavailable")
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
                val displayName = root.name ?: "Imported game"                NgrLogger.info("package.import", "Folder copied; files=${budget.files}; bytes=${budget.bytes}")
                generated = File.createTempFile("ngr-generated-", ".ngrpack", cacheDir)
                val builder = NgrGamePackageBuilder()
                val built = builder.buildFromFolder(copied, generated, displayName)
                if (!built.success) throw IOException(built.errors.joinToString("; ").ifBlank { built.message })
                NgrLogger.info("package.builder", "Folder converted; displayName=${built.displayName}; platform=${built.platform}; architecture=${built.architecture}; entryPoint=${built.entryPoint}; candidates=${built.candidates.size}")
                val generatedFile = generated ?: throw IOException("Generated package workspace is unavailable")
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
                    packageStatus.text = "Game folder import failed\n${e.message}"                    Toast.makeText(this, "Game folder import failed", Toast.LENGTH_LONG).show()
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