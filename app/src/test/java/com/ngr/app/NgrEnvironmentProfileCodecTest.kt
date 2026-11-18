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
        componentVersions = mapOf("wine-stable" to "9.0.1", "box64-stable" to "0.3.2"),
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

    @Test fun rejectsInvalidComponentVersionPins() {
        val invalidId = profile().copy(componentVersions = mapOf("../box64" to "0.3.2"))
        assertFalse(runCatching { NgrEnvironmentProfileCodec.encode(invalidId) }.isSuccess)
        val invalidVersion = profile().copy(componentVersions = mapOf("box64" to "../latest"))
        assertFalse(runCatching { NgrEnvironmentProfileCodec.encode(invalidVersion) }.isSuccess)
    }
    @Test fun migratesSchemaV1ProfilesWithoutLosingSettings() {
        val legacy = NgrEnvironmentProfileCodec.encode(profile())
            .replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
            .replace("\"winePrefixId\": null,\n", "")
        val migrated = NgrEnvironmentProfileCodec.decode(legacy)
        assertEquals(NgrEnvironmentProfile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(profile(), migrated)
    }

    @Test fun roundTripsWinePrefixBinding() {
        val linked = profile().copy(winePrefixId = "prefix-main")
        val decoded = NgrEnvironmentProfileCodec.decode(NgrEnvironmentProfileCodec.encode(linked))
        assertEquals(linked, decoded)
        assertFalse(profile().copy(winePrefixId = "../outside").validate().isEmpty())
    }

    @Test fun rejectsOversizedAndInvalidProfiles() {
        val oversized = " ".repeat(NgrEnvironmentProfileCodec.MAX_PROFILE_BYTES + 1)
        assertTrue(runCatching { NgrEnvironmentProfileCodec.decode(oversized) }.exceptionOrNull()
            ?.message.orEmpty().contains("maximum JSON size"))

        val invalid = profile().copy(environmentVariables = mapOf("PATH" to "/tmp"))
        assertFalse(runCatching { NgrEnvironmentProfileCodec.encode(invalid) }.isSuccess)
    }
    @Test fun migratesSchemaV2ProfilesWithDefaultFexPreset() {
        val legacy = NgrEnvironmentProfileCodec.encode(profile())
            .replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
            .replace("\"fexPreset\": \"BALANCED\",\n", "")
        val migrated = NgrEnvironmentProfileCodec.decode(legacy)
        assertEquals(NgrEnvironmentProfile.FexPreset.BALANCED, migrated.fexPreset)
        assertEquals(NgrEnvironmentProfile.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
    }

    @Test fun roundTripsExplicitFexPreset() {
        val arm64ec = NgrEnvironmentProfile(
            profileId = "arm64ec",
            displayName = "ARM64EC",
            containerMode = NgrEnvironmentProfile.ContainerMode.ARM64EC,
            cpuTranslator = NgrEnvironmentProfile.CpuTranslator.FEXCORE,
            fexCoreComponentId = "fexcore",
            fexUnixLibsComponentId = "fex-unixlibs",
            fexPreset = NgrEnvironmentProfile.FexPreset.PERFORMANCE
        )
        assertEquals(arm64ec, NgrEnvironmentProfileCodec.decode(NgrEnvironmentProfileCodec.encode(arm64ec)))
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
