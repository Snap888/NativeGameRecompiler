package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrEnvironmentProfileCodecTest {
    private fun profile() = NgrEnvironmentProfile(
        profileId = "desktop",
        displayName = "Desktop profile",
        containerMode = NgrEnvironmentProfile.ContainerMode.STANDARD_X86_64,
        wineLayerId = "wine-stable",
        box64ComponentId = "box64-stable",
        box86ComponentId = "box86-stable",
        cpuTranslator = NgrEnvironmentProfile.CpuTranslator.BOX64,
        box64Preset = NgrEnvironmentProfile.Box64Preset.PERFORMANCE,
        dxvkComponentId = "dxvk-2",
        graphicsProvider = NgrEnvironmentProfile.GraphicsProvider.SYSTEM_VULKAN,
        displayBackend = NgrEnvironmentProfile.DisplayBackend.X11,
        reshade = NgrEnvironmentProfile.ReShadeProfile(
            enabled = false,
            shaderPackageIds = listOf("reshade-shaders")
        ),
        audioBackend = NgrEnvironmentProfile.AudioBackend.AUTO,
        desktopWidth = 1920,
        desktopHeight = 1080,
        dpi = 120,
        environmentVariables = mapOf("DXVK_HUD" to "fps", "WINEDEBUG" to "-all"),
        gameOverrides = mapOf(
            "lethal-company" to NgrEnvironmentProfile.NgrGameEnvironmentOverride(
                dxvkComponentId = "dxvk-async",
                environmentVariables = mapOf("DXVK_FRAME_RATE" to "60")
            )
        )
    )

    @Test fun roundTripsAllProfileSettingsAndGameOverrides() {
        val original = profile()
        val decoded = NgrEnvironmentProfileCodec.decode(NgrEnvironmentProfileCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test fun rejectsUnknownKeysAndWrongFieldTypes() {
        val json = NgrEnvironmentProfileCodec.encode(profile())
        val unknown = json.replace("\"displayName\":", "\"unknown\": true, \"displayName\":")
        assertTrue(runCatching { NgrEnvironmentProfileCodec.decode(unknown) }.exceptionOrNull()
            ?.message.orEmpty().contains("Unknown key"))

        val wrongType = json.replace("\"dpi\": 120", "\"dpi\": \"120\"")
        assertTrue(runCatching { NgrEnvironmentProfileCodec.decode(wrongType) }.exceptionOrNull()
            ?.message.orEmpty().contains("dpi must be an integer"))
    }

    @Test fun rejectsOversizedAndInvalidProfiles() {
        val oversized = " ".repeat(NgrEnvironmentProfileCodec.MAX_PROFILE_BYTES + 1)
        assertTrue(runCatching { NgrEnvironmentProfileCodec.decode(oversized) }.exceptionOrNull()
            ?.message.orEmpty().contains("maximum JSON size"))

        val invalid = profile().copy(environmentVariables = mapOf("PATH" to "/tmp"))
        assertFalse(runCatching { NgrEnvironmentProfileCodec.encode(invalid) }.isSuccess)
    }
    @Test fun roundTripsArm64EcFexCoreAndUnixLibraries() {
        val arm64ec = NgrEnvironmentProfile(
            profileId = "arm64ec",
            displayName = "ARM64EC Wine",
            containerMode = NgrEnvironmentProfile.ContainerMode.ARM64EC,
            wineLayerId = "wine-arm64ec",
            cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE,
            fexCoreComponentId = "fexcore",
            fexUnixLibsComponentId = "fex-unixlibs",
            wowBox64ComponentId = "wowbox64"
        )
        assertEquals(arm64ec, NgrEnvironmentProfileCodec.decode(NgrEnvironmentProfileCodec.encode(arm64ec)))
    }
}
