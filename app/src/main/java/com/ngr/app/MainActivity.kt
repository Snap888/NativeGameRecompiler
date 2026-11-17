package com.ngr.app

import android.app.Activity
import android.os.Bundle
import android.view.InputDevice
import android.hardware.input.InputManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var devices: TextView
    private lateinit var inputManager: InputManager

    companion object { init { System.loadLibrary("ngr_runtime") } }
    private external fun nativeRuntimeInfo(): String
    private external fun nativeVulkanProbe(): String
    private external fun nativeCapabilityReport(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(INPUT_SERVICE) as InputManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
        }
        root.addView(TextView(this).apply { text = "Native Game Recompiler"; textSize = 24f })
        status = TextView(this).apply { textSize = 14f }
        root.addView(status)
        root.addView(TextView(this).apply { text = "\nVulkan devices"; textSize = 18f })
        root.addView(TextView(this).apply { text = nativeVulkanProbe(); textSize = 13f })
        root.addView(TextView(this).apply { text = "\nInput devices"; textSize = 18f })
        devices = TextView(this).apply { textSize = 14f }
        root.addView(devices)
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        inputManager.registerInputDeviceListener(this, null)
        refresh()
    }
    override fun onPause() {
        inputManager.unregisterInputDeviceListener(this)
        super.onPause()
    }
    private fun refresh() {
        status.text = nativeRuntimeInfo()
        val found = inputManager.inputDeviceIds.mapNotNull { id ->
            inputManager.getInputDevice(id)?.takeIf {
                it.sources and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
            }?.let { "• ${it.name} (id=${it.id})" }
        }
        devices.text = if (found.isEmpty()) "No gamepad detected" else found.joinToString("\n")
    }
    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()
}
