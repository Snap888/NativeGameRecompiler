package com.ngr.switchbackend

import android.net.Uri

/** Contract for the isolated nx2android porter worker. Host process never executes game code. */
object SwitchBackendContract {
    const val BACKEND_ID = "nx2android"
    const val UPSTREAM_REVISION = "d6980c39410189dc426f007822bc53f96a624171"
    const val ENGINE_SUBMODULE = "native/recompilers/nx2android"
    val acceptedExtensions = setOf("nsp", "xci", "nsz", "xcz")

    enum class ProfileStatus { PLAYABLE, BOOTS, WIP, EXPERIMENTAL, UNSUPPORTED }
    enum class BuildStage { QUEUED, VALIDATING_INPUT, EXTRACTING, REWRITING_NSO, TRANSLATING_SHADERS, BUILDING_ARM64_HOST, PACKAGING, INSTALLING, COPYING_DATA, READY, FAILED, CANCELLED }

    data class Source(val uri: Uri, val displayName: String, val extension: String, val sizeBytes: Long?) {
        init {
            require(extension.lowercase() in acceptedExtensions) { "Unsupported Nintendo Switch image format: $extension" }
            require(displayName.isNotBlank() && displayName.length <= 256)
            require(sizeBytes == null || sizeBytes >= 0)
        }
    }

    data class BuildRequest(val source: Source, val update: Source? = null, val dlc: List<Source> = emptyList(), val firmwareUri: Uri? = null, val profileId: String? = null, val language: String? = null, val vulkanDriver: String = "system")
    data class BuildOutput(val titleId: String, val packageName: String, val apkPath: String, val dataDirectory: String, val profileStatus: ProfileStatus, val engineRevision: String = UPSTREAM_REVISION)
}
