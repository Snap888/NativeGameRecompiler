package com.ngr.switchbackend

import java.io.File

/** A source checkout is not proof that a profile is playable or the toolchain is runnable. */
data class SwitchBackendReadiness(val engineCheckoutPresent: Boolean, val porterEntryPointPresent: Boolean, val androidHostSourcePresent: Boolean, val androidNdkAvailable: Boolean, val missingItems: List<String>) {
    val canStartPort: Boolean get() = engineCheckoutPresent && porterEntryPointPresent && androidHostSourcePresent && androidNdkAvailable
    companion object {
        fun inspect(repositoryRoot: File, ndkAvailable: Boolean): SwitchBackendReadiness {
            val engine = File(repositoryRoot, SwitchBackendContract.ENGINE_SUBMODULE)
            val checks = listOf(
                "upstream engine checkout" to engine.isDirectory,
                "Python porter entry point" to File(engine, "porter/__main__.py").isFile,
                "Android Vulkan host source" to File(engine, "nxnative/dm/android").isDirectory,
                "Android NDK" to ndkAvailable
            )
            return SwitchBackendReadiness(checks[0].second, checks[1].second, checks[2].second, checks[3].second, checks.filterNot { it.second }.map { it.first })
        }
    }
}
