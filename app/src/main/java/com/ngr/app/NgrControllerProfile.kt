package com.ngr.app

/**
 * Per-controller mapping and tuning preferences. These settings are persisted independently
 * from Android's device enumeration; a runtime session adapter must consume them before they
 * affect game input.
 */
data class NgrControllerProfile(
    val profileId: String,
    val displayName: String,
    val deviceDescriptor: String,
    val deviceName: String,
    val gamePackageId: String? = null,
    val bindings: Map<Action, Binding> = emptyMap(),
    val leftStickDeadZone: Double = 0.18,
    val rightStickDeadZone: Double = 0.18,
    val leftStickSensitivity: Double = 1.0,
    val rightStickSensitivity: Double = 1.0,
    val triggerThreshold: Double = 0.25,
    val invertLeftX: Boolean = false,
    val invertLeftY: Boolean = false,
    val invertRightX: Boolean = false,
    val invertRightY: Boolean = false,
    val stickCurve: StickCurve = StickCurve.LINEAR,
    val vibrationEnabled: Boolean = true,
    val touchLayout: TouchLayout = TouchLayout.HIDDEN,
    val touchOpacity: Double = 0.75,
    val touchScale: Double = 1.0
) {
    enum class Action {
        A, B, X, Y, START, SELECT, GUIDE,
        LEFT_BUMPER, RIGHT_BUMPER, LEFT_TRIGGER, RIGHT_TRIGGER,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        LEFT_STICK_X, LEFT_STICK_Y, RIGHT_STICK_X, RIGHT_STICK_Y
    }
    enum class InputKind { KEY, AXIS, HAT }
    enum class StickCurve { LINEAR, EXPONENTIAL, AGGRESSIVE }
    enum class TouchLayout { HIDDEN, VIRTUAL_GAMEPAD, CUSTOM }

    data class Binding(
        val kind: InputKind,
        val code: Int,
        /** KEY bindings use 0; AXIS/HAT bindings use -1 or +1. */
        val direction: Int = 0
    )

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!profileId.matches(ID_PATTERN)) errors += "profileId is invalid"
        if (displayName.isBlank() || displayName.length > 80) errors += "displayName must contain 1..80 characters"
        if (deviceDescriptor.isBlank() || deviceDescriptor.length > 512 ||
            deviceDescriptor.any { it == '\u0000' || it.isISOControl() }) errors += "deviceDescriptor is invalid"
        if (deviceName.isBlank() || deviceName.length > 128) errors += "deviceName must contain 1..128 characters"
        if (gamePackageId != null && !gamePackageId.matches(ID_PATTERN)) errors += "gamePackageId is invalid"
        if (bindings.size > Action.values().size) errors += "too many controller bindings"
        val physical = mutableSetOf<String>()
        bindings.forEach { (action, binding) ->
            if (binding.code !in 0..4096) errors += "Binding code for $action is outside 0..4096"
            val validDirection = when (binding.kind) {
                InputKind.KEY -> binding.direction == 0
                InputKind.AXIS, InputKind.HAT -> binding.direction == -1 || binding.direction == 1
            }
            if (!validDirection) errors += "Binding direction for $action is invalid"
            val signature = "${binding.kind}:${binding.code}:${binding.direction}"
            if (!physical.add(signature)) errors += "Physical input $signature is assigned more than once"
        }
        if (leftStickDeadZone !in 0.0..0.5 || rightStickDeadZone !in 0.0..0.5) {
            errors += "Stick dead zones must be within 0.0..0.5"
        }
        if (leftStickSensitivity !in 0.25..3.0 || rightStickSensitivity !in 0.25..3.0) {
            errors += "Stick sensitivity must be within 0.25..3.0"
        }
        if (triggerThreshold !in 0.1..0.9) errors += "Trigger threshold must be within 0.1..0.9"
        if (touchOpacity !in 0.1..1.0) errors += "Touch opacity must be within 0.1..1.0"
        if (touchScale !in 0.5..1.5) errors += "Touch scale must be within 0.5..1.5"
        return errors
    }

    companion object {
        private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    }
}
