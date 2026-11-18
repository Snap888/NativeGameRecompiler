package com.ngr.app

/**
 * Environment names controlled by NGR's host/runtime assembly.
 *
 * These variables are not ordinary game preferences: allowing a profile or per-game override
 * to replace them could redirect loaders, Vulkan layers, driver injection or the selected prefix.
 */
object NgrEnvironmentVariablePolicy {
    private val exactProtected = setOf(
        "PATH", "HOME", "PREFIX",
        "VK_ICD_FILENAMES", "VK_DRIVER_FILES", "VK_LAYER_PATH", "VK_ADD_LAYER_PATH",
        "VK_INSTANCE_LAYERS", "VK_DEVICE_LAYERS",
        "WINEPREFIX", "WINELOADER", "WINESERVER",
        "FEX_ROOTFS", "FEX_CONFIG_FILE", "FEX_APP_DATA_DIRECTORY",
        "ANDROID_ROOT", "ANDROID_DATA"
    )

    fun isRuntimeManaged(name: String): Boolean {
        val key = name.uppercase()
        return key in exactProtected ||
            key.startsWith("LD_") ||
            key.startsWith("NGR_") ||
            key.startsWith("ADRENOTOOLS_")
    }
}
