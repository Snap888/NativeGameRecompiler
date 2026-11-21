package com.ngr.app.recompiler

/**
 * The only console targets currently admitted to NGR's static-recompilation roadmap.
 *
 * This is metadata, not a claim that either compiler/runtime is already integrated.
 * Keep compiler preparation separate from the Android execution runtime.
 */
enum class ConsoleTargetId(val stableId: String) {
    XBOX_360("xbox360"),
    NINTENDO_SWITCH("nintendo-switch")
}

enum class PreparationMode {
    /** Reference implementation compiles the user's game on the Android device. */
    ON_DEVICE,
    /** Reference workflow prepares an Android APK/data pack on a desktop host. */
    DESKTOP_ASSISTED
}

enum class TargetIntegrationState {
    RESEARCHED_NOT_INTEGRATED
}

data class ConsoleTargetDefinition(
    val id: ConsoleTargetId,
    val displayName: String,
    val guestCpu: String,
    val androidAbi: String,
    val preparationMode: PreparationMode,
    val acceptedExtensions: Set<String>,
    val requiresUserProvidedKeys: Boolean,
    val compilerReference: String,
    val runtimeReference: String,
    val graphicsPath: String,
    val integrationState: TargetIntegrationState
)

object ConsoleRecompilerTargets {
    val all: List<ConsoleTargetDefinition> = listOf(
        ConsoleTargetDefinition(
            id = ConsoleTargetId.XBOX_360,
            displayName = "Xbox 360",
            guestCpu = "PowerPC Xenon",
            androidAbi = "arm64-v8a",
            preparationMode = PreparationMode.ON_DEVICE,
            acceptedExtensions = setOf("iso", "zip"),
            requiresUserProvidedKeys = false,
            compilerReference = "XenonRecomp (XDroid360-patched)",
            runtimeReference = "Title-specific DAORecomp / derived runtimes",
            graphicsPath = "Xenos native Vulkan renderer or Xenia Vulkan fallback",
            integrationState = TargetIntegrationState.RESEARCHED_NOT_INTEGRATED
        ),
        ConsoleTargetDefinition(
            id = ConsoleTargetId.NINTENDO_SWITCH,
            displayName = "Nintendo Switch",
            guestCpu = "ARM64",
            androidAbi = "arm64-v8a",
            preparationMode = PreparationMode.DESKTOP_ASSISTED,
            acceptedExtensions = setOf("nsp", "xci", "nsz", "xcz"),
            requiresUserProvidedKeys = true,
            compilerReference = "nxnative NSO/NRO rewriter",
            runtimeReference = "nxnative Android Horizon service host",
            graphicsPath = "NVN replay translated to Vulkan; OpenGL ES path for supported GL titles",
            integrationState = TargetIntegrationState.RESEARCHED_NOT_INTEGRATED
        )
    )

    fun find(id: ConsoleTargetId): ConsoleTargetDefinition? =
        all.firstOrNull { it.id == id }

    fun detectByExtension(fileName: String): List<ConsoleTargetDefinition> {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension.isBlank()) return emptyList()
        return all.filter { extension in it.acceptedExtensions }
    }
}
