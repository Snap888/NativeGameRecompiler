package com.ngr.app.recompiler

import java.net.URI

enum class ConsoleBuildJobState {
    QUEUED, RUNNING, CANCEL_REQUESTED, CANCELLED, FAILED, SUCCEEDED;
    val isTerminal: Boolean get() = this == CANCELLED || this == FAILED || this == SUCCEEDED
}

enum class ConsoleBuildStage {
    VALIDATE_SOURCE, EXTRACT, RECOMPILE_PPC, REWRITE_NSO, BUILD_SHADERS,
    COMPILE_ARM64, BUILD_RUNTIME, PACKAGE_OUTPUT, VERIFY_OUTPUT, COMPLETE
}

/** Persistable job metadata only. Game contents, firmware and user keys are never stored here. */
data class ConsoleBuildJob(
    val jobId: String,
    val targetId: ConsoleTargetId,
    val sourceUri: String,
    val sourceDisplayName: String,
    val sourceGrantFlags: Int,
    val profileId: String,
    val workspaceId: String,
    val sourceSha256: String? = null,
    val profileFingerprint: String? = null,
    val toolchainFingerprint: String? = null,
    val stage: ConsoleBuildStage = ConsoleBuildStage.VALIDATE_SOURCE,
    val state: ConsoleBuildJobState = ConsoleBuildJobState.QUEUED,
    val progressPercent: Int = 0,
    val completedStages: List<ConsoleBuildStage> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val errorCode: String? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (!SAFE_ID.matches(jobId)) errors += "jobId is invalid"
        if (!isSafeContentUri(sourceUri)) errors += "sourceUri must be a content URI"
        if (sourceDisplayName.isBlank() || sourceDisplayName.length > 255 ||
            sourceDisplayName.any { it == '\u0000' || it == '/' || it == '\\' }) errors += "sourceDisplayName is invalid"
        if (sourceGrantFlags < 0 || sourceGrantFlags and ALLOWED_GRANT_FLAGS.inv() != 0) errors += "sourceGrantFlags is invalid"
        if (!SAFE_ID.matches(profileId) || !SAFE_ID.matches(workspaceId)) errors += "profileId/workspaceId is invalid"
        if (sourceSha256 != null && !SHA256.matches(sourceSha256)) errors += "sourceSha256 is invalid"
        if (profileFingerprint != null && !SHA256.matches(profileFingerprint)) errors += "profileFingerprint is invalid"
        if (toolchainFingerprint != null && !SHA256.matches(toolchainFingerprint)) errors += "toolchainFingerprint is invalid"
        if (progressPercent !in 0..100) errors += "progressPercent is out of range"
        if (createdAtEpochMillis < 0 || updatedAtEpochMillis < createdAtEpochMillis) errors += "timestamps are invalid"
        val stages = stagesFor(targetId)
        if (stage !in stages || completedStages.any { it !in stages } ||
            completedStages.distinct().size != completedStages.size) errors += "stage list is invalid for target"
        if (completedStages.size > ConsoleBuildStage.values().size) errors += "too many completed stages"
        if (state == ConsoleBuildJobState.SUCCEEDED && (stage != ConsoleBuildStage.COMPLETE || progressPercent != 100)) {
            errors += "successful job must be complete at 100 percent"
        }
        if (errorCode != null && !ERROR_CODE.matches(errorCode)) errors += "errorCode is invalid"
        return errors
    }

    companion object {
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val SHA256 = Regex("^[a-fA-F0-9]{64}$")
        private val ERROR_CODE = Regex("^[A-Z0-9_]{1,64}$")
        private const val ALLOWED_GRANT_FLAGS = 0x43

        fun stagesFor(target: ConsoleTargetId): List<ConsoleBuildStage> = when (target) {
            ConsoleTargetId.XBOX_360 -> listOf(
                ConsoleBuildStage.VALIDATE_SOURCE, ConsoleBuildStage.EXTRACT,
                ConsoleBuildStage.RECOMPILE_PPC, ConsoleBuildStage.COMPILE_ARM64,
                ConsoleBuildStage.BUILD_RUNTIME, ConsoleBuildStage.PACKAGE_OUTPUT,
                ConsoleBuildStage.VERIFY_OUTPUT, ConsoleBuildStage.COMPLETE
            )
            ConsoleTargetId.NINTENDO_SWITCH -> listOf(
                ConsoleBuildStage.VALIDATE_SOURCE, ConsoleBuildStage.EXTRACT,
                ConsoleBuildStage.REWRITE_NSO, ConsoleBuildStage.BUILD_SHADERS,
                ConsoleBuildStage.BUILD_RUNTIME, ConsoleBuildStage.PACKAGE_OUTPUT,
                ConsoleBuildStage.VERIFY_OUTPUT, ConsoleBuildStage.COMPLETE
            )
        }

        private fun isSafeContentUri(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "content" && !uri.authority.isNullOrBlank() &&
                uri.userInfo == null && value.length <= 4096 && !value.contains('\u0000')
        }.getOrDefault(false)
    }
}
