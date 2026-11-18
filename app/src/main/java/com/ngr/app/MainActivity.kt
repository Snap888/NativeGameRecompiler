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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var inputManager: InputManager
    private lateinit var packageManager: NgrPackPackageManager
    private lateinit var shortcutStore: NgrGameShortcutStore
    private lateinit var shortcutStatus: TextView
    private lateinit var shortcutList: LinearLayout
    private lateinit var catalogStore: NgrComponentCatalogStore
    private lateinit var componentStore: NgrComponentStore
    private lateinit var componentStatus: TextView
    private lateinit var componentList: LinearLayout
    private lateinit var controllerProfileStore: NgrControllerProfileStore
    private lateinit var controllerStatus: TextView
    private lateinit var controllerProfileList: LinearLayout
    private lateinit var profileStore: NgrEnvironmentProfileStore
    private lateinit var prefixManager: NgrWinePrefixManager
    private lateinit var prefixStatus: TextView
    private lateinit var prefixList: LinearLayout
    private lateinit var profileStatus: TextView
    private lateinit var profileList: LinearLayout
    private lateinit var packageStatus: TextView
    private lateinit var packageList: LinearLayout
    private val packageWorker = Executors.newSingleThreadExecutor()
    private val componentWorker = Executors.newSingleThreadExecutor()

    companion object {
        private const val REQUEST_NGRPACK = 4102
        private const val REQUEST_EXPORT_LOG = 4103
        private const val REQUEST_GAME_ZIP = 4104
        private const val REQUEST_GAME_FOLDER = 4105
        private const val REQUEST_COMPONENT_CATALOG = 4106
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
        shortcutStore = NgrGameShortcutStore(File(filesDir, "ngr-runtime"))
        catalogStore = NgrComponentCatalogStore(File(filesDir, "ngr-runtime"))
        componentStore = NgrComponentStore(File(filesDir, "ngr-runtime"))
        controllerProfileStore = NgrControllerProfileStore(File(filesDir, "ngr-runtime"))
        profileStore = NgrEnvironmentProfileStore(File(filesDir, "ngr-runtime"))
        prefixManager = NgrWinePrefixManager(File(filesDir, "ngr-runtime"))

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

        addSection(root, "GAME SHORTCUTS", "Per-game launch settings")
        val shortcutCard = createCard()
        shortcutCard.addView(TextView(this).apply {
            text = "Create per-game launch profiles with a selected executable, environment profile and arguments. Process launch and Android home-screen pinning are separate upcoming integrations."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        shortcutCard.addView(primaryButton("＋  Add game shortcut") { showCreateGameShortcutDialog() })
        shortcutStatus = mutedText()
        shortcutCard.addView(shortcutStatus)
        shortcutList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shortcutCard.addView(shortcutList)
        root.addView(shortcutCard, sectionParams())

        addSection(root, "COMPATIBILITY", "Runtime environments")
        val runtimeCard = createCard()
        runtimeCard.addView(TextView(this).apply {
            text = "Configure Wine / Proton layers, CPU translation and graphics providers in reusable profiles."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        runtimeCard.addView(primaryButton("＋  Create environment profile") { showCreateEnvironmentProfileDialog() })
        profileStatus = mutedText()
        runtimeCard.addView(profileStatus)
        profileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        runtimeCard.addView(profileList)
        root.addView(runtimeCard, sectionParams())

        addSection(root, "COMPONENTS & DRIVERS", "Runtime component manager")
        val componentCard = createCard()
        componentCard.addView(TextView(this).apply {
            text = "Import a validated component catalog, then download verified archives with SHA-256 checks and atomic version installation. Catalogs are untrusted metadata; installed components are not loaded or executed by this manager."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        componentCard.addView(primaryButton("＋  Import component catalog JSON") { chooseComponentCatalog() })
        componentStatus = mutedText()
        componentCard.addView(componentStatus)
        componentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        componentCard.addView(componentList)
        root.addView(componentCard, sectionParams())

        addSection(root, "WINE PREFIXES", "Prefix storage & lifecycle")
        val prefixCard = createCard()
        prefixCard.addView(TextView(this).apply {
            text = "Create prepared prefixes, clone initialized ones, and make verified private snapshots. Wine initialization and game launch are not available yet."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        prefixCard.addView(primaryButton("＋  Prepare Wine prefix") { showCreatePrefixDialog() })
        prefixStatus = mutedText()
        prefixCard.addView(prefixStatus)
        prefixList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        prefixCard.addView(prefixList)
        root.addView(prefixCard, sectionParams())

        addSection(root, "DEVICE", "Hardware & graphics")
        val hardwareCard = createCard()
        hardwareCard.addView(TextView(this).apply {
            text = "VULKAN DEVICE"
            textSize = 10f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(105, 166, 255))
        })
        NgrLogger.info("vulkan", "Starting Vulkan physical-device enumeration")
        val vulkanReport = nativeVulkanProbe()
        NgrLogger.info("vulkan", "Vulkan probe completed; reportChars=${vulkanReport.length}")
        hardwareCard.addView(readoutText(vulkanReport))
        val divider = View(this).apply { setBackgroundColor(Color.rgb(48, 57, 72)) }
        hardwareCard.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })
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

        addSection(root, "CONTROLLERS", "Controller profiles & mappings")
        val controllerCard = createCard()
        controllerCard.addView(TextView(this).apply {
            text = "Bind Android gamepad keys, axes and hats to game actions. Configure dead zones, sensitivity, curves, inversion, triggers, vibration and touch overlay. Profiles are stored now; game-session input injection is a separate runtime integration."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        controllerCard.addView(primaryButton("＋  Create controller profile") { showCreateControllerProfileDialog() })
        controllerStatus = mutedText()
        controllerCard.addView(controllerStatus)
        controllerProfileList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controllerCard.addView(controllerProfileList)
        root.addView(controllerCard, sectionParams())

        addSection(root, "SUPPORT", "Diagnostics")
        val diagnostics = createCard()
        diagnostics.addView(TextView(this).apply {
            text = "Runtime logs help investigate device, Vulkan, import and package issues."
            textSize = 13f
            setTextColor(Color.rgb(163, 174, 191))
        })
        val diagnosticActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        diagnosticActions.addView(
            secondaryButton("View log") { showLogs() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) }
        )
        diagnosticActions.addView(
            secondaryButton("Export log") { saveLogs() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) }
        )
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

    private fun sectionParams() = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(14)
    }

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
        refreshPrefixes()
        refreshShortcuts()
        refreshComponents()
        refreshControllerProfiles()
    }

    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        NgrLogger.info("app", "MainActivity.onDestroy")
        packageWorker.shutdownNow()
        componentWorker.shutdownNow()
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




    private fun detectedGameControllers(): List<InputDevice> =
        inputManager.inputDeviceIds.mapNotNull { id ->
            inputManager.getInputDevice(id)?.takeIf { device ->
                device.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
            }
        }.sortedBy { it.name }

    private fun showCreateControllerProfileDialog() {
        val controllers = detectedGameControllers()
        if (controllers.isEmpty()) {
            Toast.makeText(this, "Connect a gamepad or joystick first", Toast.LENGTH_LONG).show()
            return
        }
        val labels = controllers.map { "${it.name} · ${it.descriptor}" }
        AlertDialog.Builder(this)
            .setTitle("Select controller")
            .setItems(labels.toTypedArray()) { _, index ->
                val device = controllers[index]
                val existing = controllerProfileStore.loadAll().profiles.firstOrNull {
                    it.deviceDescriptor == device.descriptor && it.gamePackageId == null
                }
                if (existing != null) {
                    showEditControllerProfileDialog(existing)
                    return@setItems
                }
                val profile = NgrControllerProfile(
                    profileId = "pad-" + UUID.randomUUID().toString().take(8),
                    displayName = device.name,
                    deviceDescriptor = device.descriptor,
                    deviceName = device.name
                )
                val result = controllerProfileStore.save(profile)
                if (!result.success) {
                    Toast.makeText(this, "Controller profile creation failed: ${result.error}", Toast.LENGTH_LONG).show()
                } else {
                    refreshControllerProfiles()
                    showEditControllerProfileDialog(profile)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshControllerProfiles() {
        if (!::controllerProfileStore.isInitialized || !::controllerProfileList.isInitialized) return
        val loaded = controllerProfileStore.loadAll()
        val connected = detectedGameControllers().map { it.descriptor }.toSet()
        controllerStatus.text = "${loaded.profiles.size} profile(s) · ${connected.size} controller(s) connected"
        loaded.errors.forEach { NgrLogger.warn("input.profile", it) }
        controllerProfileList.removeAllViews()
        if (loaded.profiles.isEmpty()) {
            controllerProfileList.addView(TextView(this).apply {
                text = "No controller profiles saved"
                textSize = 12f
                setTextColor(Color.rgb(143, 155, 174))
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        loaded.profiles.forEach { profile ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            val state = if (profile.deviceDescriptor in connected) "connected" else "not connected"
            item.addView(TextView(this).apply {
                text = "${profile.displayName}\n${profile.deviceName} · $state\n" +
                    "${profile.bindings.size} binding(s) · dead zone L/R ${profile.leftStickDeadZone}/${profile.rightStickDeadZone}"
                textSize = 12f
                setTextColor(Color.rgb(177, 190, 208))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(secondaryButton("Edit mappings") { showEditControllerProfileDialog(profile) },
                LinearLayout.LayoutParams(0, dp(42), 1f))
            actions.addView(secondaryButton("Delete") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete controller profile?")
                    .setMessage(profile.displayName)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        val result = controllerProfileStore.delete(profile.profileId)
                        if (!result.success) Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_LONG).show()
                        refreshControllerProfiles()
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(6) })
            item.addView(actions)
            controllerProfileList.addView(item)
        }
    }

    private fun showEditControllerProfileDialog(profile: NgrControllerProfile) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        fun field(label: String, value: String, numeric: Boolean = false): EditText {
            form.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(Color.rgb(163, 174, 191)) })
            return EditText(this).apply {
                setSingleLine(true)
                setText(value)
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                form.addView(this)
            }
        }
        val name = field("Profile name", profile.displayName)
        val gamePackage = field("Game package ID (blank = global)", profile.gamePackageId.orEmpty())
        val leftDeadZone = field("Left stick dead zone (0.0–0.5)", profile.leftStickDeadZone.toString(), true)
        val rightDeadZone = field("Right stick dead zone (0.0–0.5)", profile.rightStickDeadZone.toString(), true)
        val leftSensitivity = field("Left stick sensitivity (0.25–3.0)", profile.leftStickSensitivity.toString(), true)
        val rightSensitivity = field("Right stick sensitivity (0.25–3.0)", profile.rightStickSensitivity.toString(), true)
        val trigger = field("Trigger threshold (0.1–0.9)", profile.triggerThreshold.toString(), true)
        val invertLeftX = CheckBox(this).apply { text = "Invert left X"; isChecked = profile.invertLeftX }
        val invertLeftY = CheckBox(this).apply { text = "Invert left Y"; isChecked = profile.invertLeftY }
        val invertRightX = CheckBox(this).apply { text = "Invert right X"; isChecked = profile.invertRightX }
        val invertRightY = CheckBox(this).apply { text = "Invert right Y"; isChecked = profile.invertRightY }
        val vibration = CheckBox(this).apply { text = "Enable vibration"; isChecked = profile.vibrationEnabled }
        listOf(invertLeftX, invertLeftY, invertRightX, invertRightY, vibration).forEach(form::addView)

        val curve = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val curveButtons = NgrControllerProfile.StickCurve.values().associateWith { value ->
            RadioButton(this@MainActivity).apply {
                id = View.generateViewId()
                text = value.name
                isChecked = value == profile.stickCurve
                curve.addView(this)
            }
        }
        form.addView(TextView(this).apply { text = "Stick response curve"; textSize = 12f })
        form.addView(curve)

        val touch = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val touchButtons = NgrControllerProfile.TouchLayout.values().associateWith { value ->
            RadioButton(this@MainActivity).apply {
                id = View.generateViewId()
                text = value.name.replace('_', ' ')
                isChecked = value == profile.touchLayout
                touch.addView(this)
            }
        }
        form.addView(TextView(this).apply { text = "Touch overlay layout"; textSize = 12f })
        form.addView(touch)
        val touchOpacity = field("Touch overlay opacity (0.1–1.0)", profile.touchOpacity.toString(), true)
        val touchScale = field("Touch overlay scale (0.5–1.5)", profile.touchScale.toString(), true)

        val bindingText = field(
            "Bindings (ACTION=KEY:code or AXIS:code:+1 or HAT:code:-1, one per line)",
            profile.bindings.toSortedMap(compareBy { it.name }).entries.joinToString("\n") {
                "${it.key.name}=${it.value.kind.name}:${it.value.code}" +
                    if (it.value.kind == NgrControllerProfile.InputKind.KEY) "" else ":${if (it.value.direction > 0) "+1" else "-1"}"
            }
        ).apply {
            minLines = 4
            maxLines = 12
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle("Controller mapping")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val bindings = linkedMapOf<NgrControllerProfile.Action, NgrControllerProfile.Binding>()
                    bindingText.text.toString().lineSequence().forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEach
                        val split = line.indexOf('=')
                        require(split > 0) { "Binding must use ACTION=KIND:CODE[:DIRECTION]" }
                        val action = NgrControllerProfile.Action.valueOf(line.substring(0, split).trim().uppercase(Locale.ROOT))
                        val parts = line.substring(split + 1).trim().split(':')
                        require(parts.size in 2..3) { "Binding must use KIND:CODE[:DIRECTION]" }
                        val kind = NgrControllerProfile.InputKind.valueOf(parts[0].uppercase(Locale.ROOT))
                        val code = parts[1].toInt()
                        val direction = if (kind == NgrControllerProfile.InputKind.KEY) 0 else {
                            require(parts.size == 3) { "AXIS/HAT binding requires a direction" }
                            parts[2].removePrefix("+").toInt()
                        }
                        require(!bindings.containsKey(action)) { "Duplicate action binding: $action" }
                        bindings[action] = NgrControllerProfile.Binding(kind, code, direction)
                    }
                    val selectedCurve = curveButtons.entries.firstOrNull { it.value.isChecked }?.key
                        ?: NgrControllerProfile.StickCurve.LINEAR
                    val selectedTouch = touchButtons.entries.firstOrNull { it.value.isChecked }?.key
                        ?: NgrControllerProfile.TouchLayout.HIDDEN
                    val updated = profile.copy(
                        displayName = name.text.toString().trim(),
                        gamePackageId = gamePackage.text.toString().trim().ifEmpty { null },
                        bindings = bindings,
                        leftStickDeadZone = leftDeadZone.text.toString().toDouble(),
                        rightStickDeadZone = rightDeadZone.text.toString().toDouble(),
                        leftStickSensitivity = leftSensitivity.text.toString().toDouble(),
                        rightStickSensitivity = rightSensitivity.text.toString().toDouble(),
                        triggerThreshold = trigger.text.toString().toDouble(),
                        invertLeftX = invertLeftX.isChecked,
                        invertLeftY = invertLeftY.isChecked,
                        invertRightX = invertRightX.isChecked,
                        invertRightY = invertRightY.isChecked,
                        stickCurve = selectedCurve,
                        vibrationEnabled = vibration.isChecked,
                        touchLayout = selectedTouch,
                        touchOpacity = touchOpacity.text.toString().toDouble(),
                        touchScale = touchScale.text.toString().toDouble()
                    )
                    val result = controllerProfileStore.save(updated)
                    if (!result.success) throw IllegalArgumentException(result.error ?: "Controller profile validation failed")
                    refreshControllerProfiles()
                    Toast.makeText(this, "Controller profile saved. It is not active in a game session yet.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Controller profile not saved: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun chooseComponentCatalog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
        }
        startActivityForResult(intent, REQUEST_COMPONENT_CATALOG)
    }

    private fun importComponentCatalog(uri: Uri) {
        componentStatus.text = "Reading and validating component catalog…"
        componentWorker.execute {
            val result = try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open selected catalog")
                val output = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= NgrComponentCatalogParser.MAX_CATALOG_BYTES) {
                            "Catalog exceeds the 4 MiB import limit"
                        }
                        output.write(buffer, 0, count)
                    }
                }
                val json = output.toByteArray().toString(Charsets.UTF_8)
                catalogStore.save(json)
            } catch (e: Exception) {
                NgrComponentCatalogStore.Result(false, error = e.message ?: "Catalog import failed")
            }
            runOnUiThread {
                componentStatus.text = if (result.success) {
                    "Catalog ${result.entry!!.catalog.catalogId} · revision ${result.entry.catalog.revision} imported"
                } else {
                    "Catalog import failed: ${result.error}"
                }
                if (!result.success) NgrLogger.error("component.catalog", result.error ?: "Catalog import failed")
                refreshComponents()
            }
        }
    }

    private fun refreshComponents() {
        if (!::catalogStore.isInitialized || !::componentList.isInitialized) return
        val loaded = catalogStore.loadAll()
        componentStatus.text = buildString {
            append("${loaded.entries.size} catalog(s)")
            if (loaded.errors.isNotEmpty()) append(" · ${loaded.errors.size} invalid catalog file(s)")
            append(" · payloads remain inert until a verified runtime backend is implemented")
        }
        loaded.errors.forEach { NgrLogger.warn("component.catalog", it) }
        componentList.removeAllViews()
        if (loaded.entries.isEmpty()) {
            componentList.addView(TextView(this).apply {
                text = "No component catalog imported"
                textSize = 12f
                setTextColor(Color.rgb(143, 155, 174))
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        loaded.entries.forEach { entry ->
            val catalog = entry.catalog
            componentList.addView(TextView(this).apply {
                text = "${catalog.catalogId} · ${catalog.revision} · ${catalog.components.size} component(s)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(196, 207, 222))
                setPadding(0, dp(12), 0, dp(6))
            })
            catalog.components.sortedWith(compareBy({ it.type.name }, { it.id })).forEach { component ->
                val installedVersion = componentStore.activeVersionDirectory(component.id)?.name
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(7), 0, dp(7))
                }
                row.addView(TextView(this).apply {
                    text = "${component.type} · ${component.id} · v${component.version}\n" +
                        "License: ${component.licenseSpdx} · ABI: ${component.targetAbis.joinToString()}\n" +
                        if (installedVersion == component.version) "Stored version: installed" else
                            "Stored version: ${installedVersion ?: "not installed"}"
                    textSize = 12f
                    setTextColor(Color.rgb(177, 190, 208))
                })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val canInstall = component.archiveFormat == NgrComponentCatalog.ArchiveFormat.ZIP
                val installButton = secondaryButton(
                    if (installedVersion == component.version) "Installed" else if (installedVersion == null) "Install" else "Update"
                ) { installCatalogComponent(catalog, component) }
                installButton.isEnabled = canInstall && installedVersion != component.version
                if (!canInstall) installButton.text = "Unsupported archive"
                actions.addView(installButton, LinearLayout.LayoutParams(0, dp(42), 1f))
                actions.addView(secondaryButton("Source") {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(component.sourceRepository)))
                    }.onFailure {
                        Toast.makeText(this@MainActivity, "Unable to open source repository", Toast.LENGTH_SHORT).show()
                    }
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(6) })
                row.addView(actions)
                if (installedVersion != null) {
                    val maintenance = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    maintenance.addView(secondaryButton("Rollback") {
                        val result = componentStore.rollback(component.id)
                        Toast.makeText(this@MainActivity,
                            result.error ?: "Stored component version rolled back to ${result.activeVersion}",
                            Toast.LENGTH_LONG).show()
                        refreshComponents()
                    }, LinearLayout.LayoutParams(0, dp(40), 1f))
                    maintenance.addView(secondaryButton("Uninstall") {
                        val references = componentReferences(component.id, catalog)
                        if (references.isNotEmpty()) {
                            Toast.makeText(this@MainActivity,
                                "Component is still referenced by: ${references.joinToString()}",
                                Toast.LENGTH_LONG).show()
                        } else {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Uninstall stored component?")
                                .setMessage("${component.id} · v$installedVersion. This removes its stored versions; no running runtime is modified.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Uninstall") { _, _ ->
                                    val result = componentStore.uninstall(component.id)
                                    Toast.makeText(this@MainActivity,
                                        result.error ?: "Component removed from private store",
                                        Toast.LENGTH_LONG).show()
                                    refreshComponents()
                                }.show()
                        }
                    }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(6) })
                    row.addView(maintenance)
                }
                componentList.addView(row)
            }
            componentList.addView(secondaryButton("Remove catalog") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Remove catalog metadata?")
                    .setMessage("Installed component payloads will not be removed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        val result = catalogStore.delete(catalog.catalogId)
                        if (!result.success) Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_LONG).show()
                        refreshComponents()
                    }.show()
            })
        }
    }


    private fun componentReferences(
        componentId: String,
        catalog: NgrComponentCatalog
    ): List<String> {
        val references = mutableListOf<String>()
        profileStore.loadAll().profiles.forEach { profile ->
            val baseReferences = listOfNotNull(
                profile.wineLayerId, profile.box64ComponentId, profile.box86ComponentId,
                profile.fexCoreComponentId, profile.fexUnixLibsComponentId, profile.wowBox64ComponentId,
                profile.dxvkComponentId, profile.vkd3dComponentId, profile.turnipComponentId,
                profile.reshade.componentId
            ) + profile.reshade.shaderPackageIds
            val overrideReferences = profile.gameOverrides.values.flatMap { override ->
                listOfNotNull(override.wineLayerId, override.dxvkComponentId,
                    override.vkd3dComponentId, override.turnipComponentId)
            }
            if (componentId in baseReferences || componentId in overrideReferences) {
                references += "profile:${profile.displayName}"
            }
        }
        catalog.components.filter { candidate ->
            candidate.id != componentId &&
                (candidate.dependencies + candidate.constraints.requiredComponents).contains(componentId) &&
                componentStore.activeVersionDirectory(candidate.id) != null
        }.forEach { references += "installed dependent:${it.id}" }
        return references.distinct()
    }

    private fun installCatalogComponent(
        catalog: NgrComponentCatalog,
        selected: NgrComponentCatalog.Component
    ) {
        val installedVersions = catalog.components.mapNotNull { component ->
            componentStore.activeVersionDirectory(component.id)?.name?.let { component.id to it }
        }.toMap()
        val installedIds = installedVersions.keys
        val plan = NgrComponentInstallPlanner.resolve(
            catalog, setOf(selected.id), installedIds, installedVersions
        )
        if (!plan.valid) {
            AlertDialog.Builder(this)
                .setTitle("Component dependency plan rejected")
                .setMessage(plan.errors.joinToString("\n") { "• $it" })
                .setPositiveButton("Close", null)
                .show()
            return
        }
        val unsupported = plan.installOrder.filter { it.archiveFormat != NgrComponentCatalog.ArchiveFormat.ZIP }
        if (unsupported.isNotEmpty()) {
            Toast.makeText(this, "Unsupported archive format: ${unsupported.joinToString { it.id }}", Toast.LENGTH_LONG).show()
            return
        }
        if (plan.installOrder.isEmpty()) {
            Toast.makeText(this, "All required component versions are already installed", Toast.LENGTH_SHORT).show()
            return
        }
        componentStatus.text = "Installing ${selected.id} and ${plan.installOrder.size - 1} dependency component(s)…"
        componentWorker.execute {
            val pipeline = NgrComponentInstallPipeline(componentStore)
            val installed = mutableListOf<String>()
            var failure: String? = null
            for (component in plan.installOrder) {
                NgrLogger.info("component.install", "Activating or installing ${component.id} v${component.version}")
                val stored = componentStore.storedVersionDirectory(component.id, component.version)
                val result = if (stored != null) {
                    componentStore.activateStoredVersion(component)
                } else {
                    pipeline.downloadAndInstall(component)
                }
                if (!result.success) {
                    failure = "${component.id}: ${result.error ?: "install failed"}"
                    NgrLogger.error("component.install", failure)
                    break
                }
                installed += component.id
            }
            runOnUiThread {
                componentStatus.text = if (failure == null) {
                    "Installed: ${installed.joinToString()}"
                } else {
                    "Partial installation: ${installed.joinToString().ifEmpty { "none" }} · Failed: $failure"
                }
                if (failure != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Component installation incomplete")
                        .setMessage("Installed before failure: ${installed.joinToString().ifEmpty { "none" }}\n$failure\nPreviously installed components remain available; review the component list before retrying.")
                        .setPositiveButton("Close", null)
                        .show()
                }
                refreshComponents()
            }
        }
    }

    private fun showCreatePrefixDialog() {
        val name = EditText(this).apply { hint = "Prefix name"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Prepare Wine prefix").setView(name)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val displayName = name.text.toString().trim()
                val id = "prefix-" + UUID.randomUUID().toString().take(8)
                val result = prefixManager.create(id, displayName)
                if (!result.success) Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
                else Toast.makeText(this, "Prepared prefix created (not initialized by Wine)", Toast.LENGTH_LONG).show()
                refreshPrefixes()
            }.show()
    }

    private fun refreshPrefixes() {
        if (!::prefixManager.isInitialized || !::prefixList.isInitialized) return
        val prefixes = prefixManager.list()
        prefixStatus.text = "${prefixes.size} prefix(es) · READY means Wine registry and drive markers were verified"
        prefixList.removeAllViews()
        prefixes.forEach { prefix ->
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 12) }
            item.addView(TextView(this).apply {
                text = "${prefix.displayName}\n${prefix.state} · ${prefix.fileCount} files · ${prefix.totalBytes / (1024 * 1024)} MiB"
                textSize = 13f
                setTextColor(if (prefix.state == NgrWinePrefixManager.State.READY) Color.rgb(116, 210, 157) else Color.rgb(220, 185, 110))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (prefix.state == NgrWinePrefixManager.State.READY) {
                actions.addView(Button(this).apply {
                    text = "Clone"
                    setOnClickListener {
                        val name = EditText(this@MainActivity).apply { hint = "Clone name"; setSingleLine(true) }
                        AlertDialog.Builder(this@MainActivity).setTitle("Clone ${prefix.displayName}").setView(name)
                            .setNegativeButton("Cancel", null).setPositiveButton("Clone") { _, _ ->
                                val id = "prefix-" + UUID.randomUUID().toString().take(8)
                                val result = prefixManager.clone(prefix.id, id, name.text.toString().trim())
                                Toast.makeText(this@MainActivity, result.error ?: "Prefix cloned", Toast.LENGTH_LONG).show()
                                refreshPrefixes()
                            }.show()
                    }
                })
                actions.addView(Button(this).apply {
                    text = "Backup"
                    setOnClickListener {
                        val id = "backup-" + UUID.randomUUID().toString().take(8)
                        val result = prefixManager.backup(prefix.id, id)
                        Toast.makeText(this@MainActivity, result.error ?: "Private backup created: $id", Toast.LENGTH_LONG).show()
                    }
                })
            }
            actions.addView(Button(this).apply {
                text = "Reset"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Reset prefix?")
                        .setMessage("All files in ${prefix.displayName} will be removed. This cannot be undone.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Reset") { _, _ ->
                            val result = prefixManager.reset(prefix.id)
                            Toast.makeText(this@MainActivity, result.error ?: "Prefix reset", Toast.LENGTH_LONG).show()
                            refreshPrefixes()
                        }.show()
                }
            })
            actions.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    val linkedProfiles = profileStore.loadAll().profiles.filter { it.winePrefixId == prefix.id }
                    if (linkedProfiles.isNotEmpty()) {
                        Toast.makeText(this@MainActivity,
                            "Prefix is assigned to: ${linkedProfiles.joinToString { it.displayName }}. Unassign it from profiles first.",
                            Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    AlertDialog.Builder(this@MainActivity).setTitle("Delete prefix?")
                        .setMessage(prefix.displayName).setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            val result = prefixManager.remove(prefix.id)
                            Toast.makeText(this@MainActivity, result.error ?: "Prefix deleted", Toast.LENGTH_LONG).show()
                            refreshPrefixes()
                        }.show()
                }
            })
            item.addView(actions)
            prefixList.addView(item)
        }
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
            val readiness = profile.validateLaunchReady().toMutableList()
            val linkedPrefix = profile.winePrefixId?.let(prefixManager::inspect)
            when {
                profile.winePrefixId.isNullOrBlank() -> readiness += "Wine prefix is not assigned"
                linkedPrefix == null -> readiness += "Assigned Wine prefix is missing or unsafe"
                linkedPrefix.state != NgrWinePrefixManager.State.READY -> readiness += "Wine prefix is ${linkedPrefix.state}"
            }
            item.addView(TextView(this).apply {
                text = "${profile.displayName}\n${profile.containerMode} · ${profile.cpuTranslator}\n" +
                    "Wine prefix: ${profile.winePrefixId ?: "not assigned"}\n" +
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
        val winePrefix = field("Managed Wine prefix ID", profile.winePrefixId)
        form.addView(secondaryButton("Choose existing prefix") {
            val available = prefixManager.list()
            val labels = listOf("No prefix") + available.map { "${it.displayName} · ${it.id} · ${it.state}" }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Assign Wine prefix")
                .setItems(labels.toTypedArray()) { _, which ->
                    winePrefix.setText(if (which == 0) "" else available[which - 1].id)
                }
                .show()
        })
        val box64 = field("Box64 component ID", profile.box64ComponentId)
        val box86 = field("Box86 component ID (optional)", profile.box86ComponentId)
        val fex = field("FEXCore component ID", profile.fexCoreComponentId)
        val fexUnix = field("FEX UnixLibs component ID", profile.fexUnixLibsComponentId)
        val wow = field("WOWBox64 component ID", profile.wowBox64ComponentId)
        val dxvk = field("DXVK component ID", profile.dxvkComponentId)
        val vkd3d = field("VKD3D-Proton component ID", profile.vkd3dComponentId)
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
        form.addView(displayGroup)
        val graphicsGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
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
                        winePrefixId = winePrefix.text.toString().trim().ifEmpty { null },
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
        if (requestCode == REQUEST_COMPONENT_CATALOG) {
            if (resultCode == RESULT_OK) data?.data?.let { importComponentCatalog(it) }
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
                val displayName = root.name ?: "Imported game"
                NgrLogger.info("package.import", "Folder copied; files=${budget.files}; bytes=${budget.bytes}")
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
        val limits = NgrPackArchiveInspector.Limits()
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
                            if (fileBytes > limits.maxFileBytes)
                                throw IOException("Game file exceeds the ${limits.maxFileBytes / (1024L * 1024)} MiB per-file import limit: $childRelative")
                            if (budget.bytes > limits.maxTotalBytes)
                                throw IOException("Game folder exceeds the ${limits.maxTotalBytes / (1024L * 1024)} MiB total import limit after copying $childRelative")
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


    private fun showCreateGameShortcutDialog() {
        val packages = packageManager.listInstalled().packages
        val profiles = profileStore.loadAll().profiles
        if (packages.isEmpty()) {
            Toast.makeText(this, "Import a game package first", Toast.LENGTH_LONG).show()
            return
        }
        if (profiles.isEmpty()) {
            Toast.makeText(this, "Create an environment profile first", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Select installed game")
            .setItems(packages.map { "${it.displayName} · ${it.sourceArchitecture ?: "unknown"}" }.toTypedArray()) { _, packageIndex ->
                val selectedPackage = packages[packageIndex]
                AlertDialog.Builder(this)
                    .setTitle("Select environment profile")
                    .setItems(profiles.map { "${it.displayName} · ${it.containerMode}" }.toTypedArray()) { _, profileIndex ->
                        val selectedProfile = profiles[profileIndex]
                        val shortcut = NgrGameShortcut(
                            shortcutId = "game-" + UUID.randomUUID().toString().take(8),
                            title = selectedPackage.displayName,
                            packageId = selectedPackage.packageId,
                            executablePath = selectedPackage.entryPoint,
                            environmentProfileId = selectedProfile.profileId
                        )
                        val result = shortcutStore.save(shortcut)
                        if (!result.success) {
                            Toast.makeText(this, "Shortcut creation failed: ${result.error}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Game shortcut created", Toast.LENGTH_SHORT).show()
                            refreshShortcuts()
                            showEditGameShortcutDialog(shortcut)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshShortcuts() {
        if (!::shortcutStore.isInitialized || !::shortcutList.isInitialized) return
        val loaded = shortcutStore.loadAll()
        shortcutStatus.text = "${loaded.shortcuts.size} shortcut(s) · metadata only; launch backend is not connected"
        loaded.errors.forEach { NgrLogger.warn("game.shortcut", it) }
        shortcutList.removeAllViews()
        if (loaded.shortcuts.isEmpty()) {
            shortcutList.addView(TextView(this).apply {
                text = "No game shortcuts yet"
                textSize = 12f
                setTextColor(Color.rgb(143, 155, 174))
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        loaded.shortcuts.forEach { shortcut ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            item.addView(TextView(this).apply {
                text = "${shortcut.title}\n${shortcut.executablePath}\nProfile: ${shortcut.environmentProfileId} · ${shortcut.launchMode}"
                textSize = 13f
                setTextColor(Color.rgb(196, 207, 222))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(secondaryButton("Launch settings") { showEditGameShortcutDialog(shortcut) },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) })
            actions.addView(secondaryButton("Delete") {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete game shortcut?")
                    .setMessage(shortcut.title)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        val result = shortcutStore.delete(shortcut.shortcutId)
                        if (!result.success) Toast.makeText(this@MainActivity, result.error, Toast.LENGTH_LONG).show()
                        refreshShortcuts()
                    }.show()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(5) })
            item.addView(actions)
            item.addView(secondaryButton("Preview resolved launch configuration") {
                showLaunchConfigurationPreview(shortcut)
            })
            shortcutList.addView(item)
        }
    }


    private fun showLaunchConfigurationPreview(shortcut: NgrGameShortcut) {
        val profile = profileStore.get(shortcut.environmentProfileId)
        if (profile == null) {
            AlertDialog.Builder(this)
                .setTitle("Launch configuration unavailable")
                .setMessage("The shortcut's environment profile is missing or invalid.")
                .setPositiveButton("Close", null)
                .show()
            return
        }
        val result = NgrLaunchConfigurationResolver.resolve(shortcut, profile)
        if (!result.valid) {
            AlertDialog.Builder(this)
                .setTitle("Configuration validation failed")
                .setMessage(result.errors.joinToString("\n") { "• $it" })
                .setPositiveButton("Close", null)
                .show()
            return
        }
        val config = result.configuration!!
        val details = buildString {
            appendLine("Mode: ${config.launchMode}")
            appendLine("Executable: ${config.executablePath}")
            appendLine("Working directory: ${config.workingDirectory ?: "(package default)"}")
            appendLine("Arguments: ${config.arguments.joinToString(" ").ifEmpty { "(none)" }}")
            appendLine("Wine/Proton: ${config.wineLayerId ?: "not required"}")
            appendLine("DXVK: ${config.dxvkComponentId ?: "not selected"}")
            appendLine("VKD3D-Proton: ${config.vkd3dComponentId ?: "not selected"}")
            appendLine("Graphics: ${config.graphicsProvider} · Turnip: ${config.turnipComponentId ?: "system/default"}")
            appendLine("Display: ${config.displayBackend}")
            appendLine()
            appendLine("Effective environment:")
            config.environment.forEach { (key, value) -> appendLine("$key=$value") }
            appendLine()
            append("Configuration only — no process will be started. Runtime activation and component readiness are verified by later launch stages.")
        }
        AlertDialog.Builder(this)
            .setTitle("Resolved: ${shortcut.title}")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showEditGameShortcutDialog(shortcut: NgrGameShortcut) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        fun field(label: String, value: String, multiline: Boolean = false): EditText {
            form.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(Color.rgb(163, 174, 191)) })
            return EditText(this).apply {
                setText(value)
                if (multiline) { minLines = 2; maxLines = 5; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
                else setSingleLine(true)
                form.addView(this)
            }
        }
        val title = field("Shortcut title", shortcut.title)
        val executable = field("Package-relative executable path", shortcut.executablePath)
        val workingDirectory = field("Working directory (optional)", shortcut.workingDirectory.orEmpty())
        val arguments = field("Launch arguments (one per line)", shortcut.arguments.joinToString("\n"), true)
        val profile = field("Environment profile ID", shortcut.environmentProfileId)
        val environment = field("Per-game environment overrides (KEY=VALUE)", shortcut.environmentOverrides.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }, true)
        val modes = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val modeButtons = NgrGameShortcut.LaunchMode.values().associateWith { mode ->
            RadioButton(this@MainActivity).apply {
                id = View.generateViewId()
                text = mode.name.replace('_', ' ')
                isChecked = mode == shortcut.launchMode
                modes.addView(this)
            }
        }
        form.addView(TextView(this).apply { text = "Execution mode"; textSize = 12f })
        form.addView(modes)
        val scroll = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle("Launch settings")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val env = linkedMapOf<String, String>()
                    environment.text.toString().lineSequence().forEach { line ->
                        val value = line.trim()
                        if (value.isEmpty()) return@forEach
                        val split = value.indexOf('=')
                        require(split > 0) { "Environment overrides must use KEY=VALUE" }
                        val key = value.substring(0, split).trim()
                        require(!env.containsKey(key)) { "Duplicate environment override: $key" }
                        env[key] = value.substring(split + 1)
                    }
                    val selectedMode = modeButtons.entries.firstOrNull { it.value.isChecked }?.key
                        ?: NgrGameShortcut.LaunchMode.WINE_COMPATIBILITY
                    val updated = shortcut.copy(
                        title = title.text.toString().trim(),
                        executablePath = executable.text.toString().trim(),
                        workingDirectory = workingDirectory.text.toString().trim().ifEmpty { null },
                        arguments = arguments.text.toString().lineSequence().filter { it.isNotBlank() }.toList(),
                        environmentProfileId = profile.text.toString().trim(),
                        environmentOverrides = env,
                        launchMode = selectedMode
                    )
                    require(profileStore.get(updated.environmentProfileId) != null) { "Selected environment profile does not exist" }
                    val result = shortcutStore.save(updated)
                    if (!result.success) throw IllegalArgumentException(result.error ?: "Shortcut validation failed")
                    refreshShortcuts()
                    Toast.makeText(this, "Launch settings saved. Runtime launch is not yet connected.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Settings not saved: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
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
                refreshShortcuts()
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
                    if (result.success) {
                        shortcutStore.loadAll().shortcuts.filter { it.packageId == packageId }.forEach { shortcutStore.delete(it.shortcutId) }
                    }
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
