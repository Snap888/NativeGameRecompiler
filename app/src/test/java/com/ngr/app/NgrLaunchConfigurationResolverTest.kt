package com.ngr.app

import org.junit.Assert.*
import org.junit.Test

class NgrLaunchConfigurationResolverTest {
    private fun profile() = NgrEnvironmentProfile(
        profileId = "desktop",
        displayName = "Desktop",
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64,
        wineLayerId = "wine-stable",
        box64ComponentId = "box64-stable",
        dxvkComponentId = "dxvk-base",
        environmentVariables = mapOf("DXVK_HUD" to "fps"),
        gameOverrides = mapOf(
            "test-game" to NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                dxvkComponentId = "dxvk-game",
                environmentVariables = mapOf("DXVK_ASYNC" to "0", "WINEDEBUG" to "-all")
            )
        )
    )

    private fun shortcut(mode: NgrGameShortcut.LaunchMode = NgrGameShortcut.LaunchMode.WINE_COMPATIBILITY) =
        NgrGameShortcut(
            shortcutId = "test-shortcut",
            title = "Test Game",
            packageId = "test-game",
            executablePath = "C:/Games/Test/game.exe",
            arguments = listOf("-windowed", "-novid"),
            environmentProfileId = "desktop",
            launchMode = mode,
            environmentOverrides = mapOf("DXVK_ASYNC" to "1")
        )

    @Test fun mergesProfileGameAndShortcutEnvironmentInDeterministicPrecedence() {
        val result = NgrLaunchConfigurationResolver.resolve(shortcut(), profile())
        assertTrue(result.errors.toString(), result.valid)
        val config = result.configuration!!
        assertEquals("1", config.environment["DXVK_ASYNC"])
        assertEquals("fps", config.environment["DXVK_HUD"])
        assertEquals("-all", config.environment["WINEDEBUG"])
        assertEquals("dxvk-game", config.dxvkComponentId)
        assertEquals("wine-stable", config.wineLayerId)
        assertEquals("C:/Games/Test", config.workingDirectory)
        assertEquals(listOf("-windowed", "-novid"), config.arguments)
        assertTrue(config.requiresWineCompatibilityRuntime)
    }

    @Test fun rejectsShortcutBoundToAnotherProfile() {
        val result = NgrLaunchConfigurationResolver.resolve(shortcut(), profile().copy(profileId = "other"))
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("different environment profile") })
    }

    @Test fun rejectsWineLaunchWithoutRequiredTranslatorAndWineComponents() {
        val incomplete = profile().copy(wineLayerId = null, box64ComponentId = null)
        val result = NgrLaunchConfigurationResolver.resolve(shortcut(), incomplete)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Wine/Proton") })
        assertTrue(result.errors.any { it.contains("Box64") })
    }

    @Test fun nativeAndStaticModesDoNotRequireWineLayer() {
        val noWine = profile().copy(wineLayerId = null, box64ComponentId = null)
        for (mode in listOf(
            NgrGameShortcut.LaunchMode.STATIC_RECOMPILED,
            NgrGameShortcut.LaunchMode.NATIVE_ARM64
        )) {
            val result = NgrLaunchConfigurationResolver.resolve(shortcut(mode), noWine)
            assertTrue("$mode: ${result.errors}", result.valid)
            assertFalse(result.configuration!!.requiresWineCompatibilityRuntime)
        }
    }

    @Test fun rejectsWaylandGameOverrideWithoutVerifiedProvider() {
        val invalid = profile().copy(
            gameOverrides = mapOf(
                "test-game" to NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                    displayBackend = NgrEnvironmentProfile.DisplayBackend.WAYLAND
                )
            ),
            waylandWineSupported = true,
            waylandTurnipSupported = false
        )
        val result = NgrLaunchConfigurationResolver.resolve(shortcut(), invalid)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Wayland override requires") })
    }


    @Test fun acceptsPerGameWaylandWhenOverrideSuppliesTurnipComponent() {
        val waylandProfile = profile().copy(
            waylandWineSupported = true,
            waylandTurnipSupported = true,
            gameOverrides = mapOf(
                "test-game" to NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                    displayBackend = NgrEnvironmentProfile.DisplayBackend.WAYLAND,
                    turnipComponentId = "turnip-wayland"
                )
            )
        )
        val result = NgrLaunchConfigurationResolver.resolve(shortcut(), waylandProfile)
        assertTrue(result.errors.toString(), result.valid)
        val config = result.configuration!!
        assertEquals(NgrEnvironmentProfile.DisplayBackend.WAYLAND, config.displayBackend)
        assertEquals(NgrEnvironmentProfile.GraphicsProvider.TURNIP, config.graphicsProvider)
        assertEquals("turnip-wayland", config.turnipComponentId)
    }

    @Test fun blocksVulkanDriverAndLoaderEnvironmentHijacking() {
        val forbidden = listOf(
            "VK_ICD_FILENAMES", "VK_DRIVER_FILES", "VK_LAYER_PATH",
            "ADRENOTOOLS_DRIVER_PATH", "WINEPREFIX", "LD_PRELOAD"
        )
        forbidden.forEach { key ->
            assertTrue(key, NgrEnvironmentVariablePolicy.isRuntimeManaged(key))
            val invalidProfile = profile().copy(environmentVariables = mapOf(key to "/tmp/override"))
            assertTrue(key, invalidProfile.validate().any { it.contains(key) })
            val invalidShortcut = shortcut().copy(environmentOverrides = mapOf(key to "/tmp/override"))
            assertTrue(key, invalidShortcut.validate().any { it.contains(key) })
        }
        assertFalse(NgrEnvironmentVariablePolicy.isRuntimeManaged("DXVK_HUD"))
        assertFalse(NgrEnvironmentVariablePolicy.isRuntimeManaged("WINEDEBUG"))
    }
}
