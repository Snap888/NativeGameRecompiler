package com.ngr.switchbackend

/** Data-only view of an upstream title profile. Status is never inferred from extension. */
data class SwitchProfile(val titleId: String, val displayName: String, val status: SwitchBackendContract.ProfileStatus, val packageName: String?, val minimumAndroidApi: Int?, val vulkanDriver: String?, val notes: String?) {
    init {
        require(titleId.matches(Regex("[0-9A-Fa-f]{16}")))
        require(displayName.isNotBlank() && displayName.length <= 160)
        require(packageName == null || packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,180}")))
        require(minimumAndroidApi == null || minimumAndroidApi in 26..100)
        require(vulkanDriver == null || vulkanDriver in setOf("system", "turnip"))
        require(notes == null || notes.length <= 4096)
    }
}
