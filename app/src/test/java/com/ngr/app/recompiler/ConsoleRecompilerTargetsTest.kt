package com.ngr.app.recompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleRecompilerTargetsTest {
    @Test fun exposesOnlyXbox360AndNintendoSwitch() {
        assertEquals(
            setOf(ConsoleTargetId.XBOX_360, ConsoleTargetId.NINTENDO_SWITCH),
            ConsoleRecompilerTargets.all.map { it.id }.toSet()
        )
        assertEquals(2, ConsoleRecompilerTargets.all.size)
    }

    @Test fun xbox360UsesOnDevicePowerPcRecompilationReference() {
        val target = ConsoleRecompilerTargets.find(ConsoleTargetId.XBOX_360)!!
        assertEquals("PowerPC Xenon", target.guestCpu)
        assertEquals("arm64-v8a", target.androidAbi)
        assertEquals(PreparationMode.ON_DEVICE, target.preparationMode)
        assertTrue("iso" in target.acceptedExtensions)
        assertTrue("zip" in target.acceptedExtensions)
        assertFalse(target.requiresUserProvidedKeys)
        assertEquals(TargetIntegrationState.RESEARCHED_NOT_INTEGRATED, target.integrationState)
    }

    @Test fun switchUsesArm64RewriteAndRequiresUserKeys() {
        val target = ConsoleRecompilerTargets.find(ConsoleTargetId.NINTENDO_SWITCH)!!
        assertEquals("ARM64", target.guestCpu)
        assertEquals(PreparationMode.DESKTOP_ASSISTED, target.preparationMode)
        assertTrue(target.requiresUserProvidedKeys)
        assertTrue(setOf("nsp", "xci", "nsz", "xcz").all { it in target.acceptedExtensions })
        assertEquals(TargetIntegrationState.RESEARCHED_NOT_INTEGRATED, target.integrationState)
    }

    @Test fun detectsOnlyUnambiguousSupportedExtensions() {
        assertEquals(
            listOf(ConsoleTargetId.XBOX_360),
            ConsoleRecompilerTargets.detectByExtension("game.ISO").map { it.id }
        )
        assertEquals(
            listOf(ConsoleTargetId.NINTENDO_SWITCH),
            ConsoleRecompilerTargets.detectByExtension("update.NSZ").map { it.id }
        )
        assertTrue(ConsoleRecompilerTargets.detectByExtension("game.z64").isEmpty())
        assertTrue(ConsoleRecompilerTargets.detectByExtension("game.exe").isEmpty())
        assertTrue(ConsoleRecompilerTargets.detectByExtension("archive.zip").any { it.id == ConsoleTargetId.XBOX_360 })
    }
}
