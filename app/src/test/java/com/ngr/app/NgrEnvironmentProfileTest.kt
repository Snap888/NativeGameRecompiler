package com.ngr.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NgrEnvironmentProfileTest {
    private fun standard() = NgrEnvironmentProfile(
        profileId = "default-win64",
        displayName = "Default Windows x64",
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64
    )

    @Test fun acceptsMinimalStandardBox64Profile() {
        assertTrue(standard().validate().toString(), standard().validate().isEmpty())
    }

    @Test fun rejectsFexForStandardX64Container() {
        val errors = standard().copy(cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE).validate()
        assertTrue(errors.any { it.contains("requires Box64") })
    }

    @Test fun requiresFexCoreForArm64Ec() {
        val profile = standard().copy(
            containerMode = NgrEnvironmentProfile.ContainerMode.ARM64EC,
            cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE
        )
        assertTrue(profile.validate().any { it.contains("requires a selected FEXCore") })
        assertTrue(profile.copy(fexCoreComponentId = "fexcore-2611").validate().isEmpty())
    }

    @Test fun waylandRequiresMatchingWineAndTurnipSupport() {
        val profile = standard().copy(
            displayBackend = NgrEnvironmentProfile.DisplayBackend.WAYLAND,
            graphicsProvider = NgrEnvironmentProfile.GraphicsProvider.TURNIP,
            turnipComponentId = "turnip-adreno",
            waylandWineSupported = true,
            waylandTurnipSupported = false
        )
        assertTrue(profile.validate().any { it.contains("Wayland requires") })
        assertFalse(profile.copy(waylandTurnipSupported = true).validate().any { it.contains("Wayland requires") })
    }

    @Test fun reshadePresetRequiresInstalledComponentAndEnabledState() {
        val invalid = standard().copy(
            reshade = NgrEnvironmentProfile.ReShadeProfile(enabled = false, presetPath = "Presets/game.ini")
        )
        assertTrue(invalid.validate().any { it.contains("preset requires") })
        val valid = standard().copy(
            reshade = NgrEnvironmentProfile.ReShadeProfile(
                enabled = true,
                componentId = "reshade-vulkan",
                presetPath = "Presets/game.ini",
                shaderPackageIds = listOf("sweetfx-pack")
            )
        )
        assertTrue(valid.validate().toString(), valid.validate().isEmpty())
    }

    @Test fun rejectsRuntimeManagedEnvironmentVariables() {
        val errors = standard().copy(
            environmentVariables = mapOf(
                "DXVK_HUD" to "fps",
                "LD_PRELOAD" to "/tmp/inject.so",
                "PATH" to "/data/local/tmp"
            )
        ).validate()
        assertTrue(errors.any { it.contains("LD_PRELOAD") })
        assertTrue(errors.any { it.contains("PATH") })
        assertFalse(errors.any { it.contains("DXVK_HUD") })
    }

    @Test fun rejectsInvalidDimensionsAndSchema() {
        val errors = standard().copy(
            schemaVersion = 99,
            desktopWidth = 100,
            dpi = 900
        ).validate()
        assertTrue(errors.any { it.contains("Unsupported environment profile schema") })
        assertTrue(errors.any { it.contains("Desktop dimensions") })
        assertTrue(errors.any { it.contains("DPI") })
    }
    @Test fun rejectsUnsafeReshadePresetPathsAndWaylandOverridesWithoutPrerequisites() {
        val unsafePreset = standard().copy(
            reshade = NgrEnvironmentProfile.ReShadeProfile(
                enabled = true,
                componentId = "vkbasalt",
                presetPath = "../outside.ini"
            )
        )
        assertTrue(unsafePreset.validate().any { it.contains("safe relative path") })

        val waylandOverride = standard().copy(
            gameOverrides = mapOf(
                "game-a" to NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                    displayBackend = NgrEnvironmentProfile.DisplayBackend.WAYLAND
                )
            )
        )
        assertTrue(waylandOverride.validate().any { it.contains("Wayland override") })
    }

    @Test fun launchReadyProfilesRequireWineAndArchitectureSpecificCpuComponents() {
        assertTrue(standard().validateLaunchReady().any { it.contains("Wine/Proton") })
        assertTrue(standard().validateLaunchReady().any { it.contains("Box64 component") })
        val ready = standard().copy(wineLayerId = "wine-stable", box64ComponentId = "box64-stable")
        assertTrue(ready.validateLaunchReady().toString(), ready.validateLaunchReady().isEmpty())

        val arm64ec = standard().copy(
            containerMode = NgrEnvironmentProfile.ContainerMode.ARM64EC,
            cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE,
            fexCoreComponentId = "fexcore",
            wineLayerId = "wine-arm64ec"
        )
        assertTrue(arm64ec.validateLaunchReady().any { it.contains("FEX UnixLibs") })
        assertTrue(arm64ec.copy(fexUnixLibsComponentId = "fex-unixlibs").validateLaunchReady().isEmpty())
    }
}
