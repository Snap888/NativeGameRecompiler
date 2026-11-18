package com.ngr.app

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class NgrControllerProfileStoreTest {
    private fun profile(id: String = "pad") = NgrControllerProfile(
        profileId = id,
        displayName = "Wireless Controller",
        deviceDescriptor = "usb-0001:0002",
        deviceName = "Gamepad",
        bindings = mapOf(
            NgrControllerProfile.Action.A to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.KEY, 96),
            NgrControllerProfile.Action.LEFT_STICK_X to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.AXIS, 0, 1),
            NgrControllerProfile.Action.DPAD_UP to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.HAT, 16, -1)
        ),
        leftStickDeadZone = 0.12,
        rightStickDeadZone = 0.2,
        leftStickSensitivity = 1.25,
        triggerThreshold = 0.3,
        invertRightY = true,
        stickCurve = NgrControllerProfile.StickCurve.EXPONENTIAL,
        touchLayout = NgrControllerProfile.TouchLayout.VIRTUAL_GAMEPAD,
        touchOpacity = 0.65,
        touchScale = 1.1
    )

    @Test fun savesLoadsUpdatesAndDeletesControllerProfile() {
        val root = Files.createTempDirectory("ngr-controller-store-").toFile()
        try {
            val store = NgrControllerProfileStore(root)
            val original = profile()
            assertTrue(store.save(original).success)
            assertEquals(original, store.get("pad"))
            val updated = original.copy(displayName = "Updated Pad", vibrationEnabled = false)
            assertTrue(store.save(updated).success)
            assertEquals(updated, store.get("pad"))
            assertTrue(store.loadAll().errors.isEmpty())
            assertTrue(store.delete("pad").success)
            assertNull(store.get("pad"))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsDuplicatePhysicalBindingsAndInvalidTuning() {
        val root = Files.createTempDirectory("ngr-controller-store-").toFile()
        try {
            val store = NgrControllerProfileStore(root)
            val duplicate = profile().copy(bindings = mapOf(
                NgrControllerProfile.Action.A to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.KEY, 96),
                NgrControllerProfile.Action.B to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.KEY, 96)
            ))
            assertFalse(store.save(duplicate).success)
            assertFalse(store.save(profile().copy(leftStickDeadZone = 0.9)).success)
            assertFalse(store.save(profile().copy(triggerThreshold = 0.0)).success)
            assertFalse(store.save(profile().copy(deviceDescriptor = "")).success)
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsInvalidBindingDirectionsAndUnknownSchemaFiles() {
        val root = Files.createTempDirectory("ngr-controller-store-").toFile()
        try {
            val store = NgrControllerProfileStore(root)
            val invalid = profile().copy(bindings = mapOf(
                NgrControllerProfile.Action.A to NgrControllerProfile.Binding(NgrControllerProfile.InputKind.AXIS, 0, 0)
            ))
            assertFalse(store.save(invalid).success)
            val directory = root.resolve("controller-profiles")
            directory.mkdirs()
            directory.resolve("future.json").writeText("""{"schemaVersion":99}""")
            val result = store.loadAll()
            assertTrue(result.profiles.isEmpty())
            assertEquals(1, result.errors.size)
        } finally { root.deleteRecursively() }
    }
}
