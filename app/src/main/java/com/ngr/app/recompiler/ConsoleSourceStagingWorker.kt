package com.ngr.app.recompiler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Durable SAF intake worker. It validates and stages the user-owned source image only.
 * It deliberately does NOT claim to extract, recompile, translate shaders or build a runtime.
 */
class ConsoleSourceStagingWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    private val store = ConsoleBuildJobStore(File(applicationContext.filesDir, "ngr-runtime"))

    override fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        var job = store.get(jobId) ?: return Result.failure()
        if (job.state != ConsoleBuildJobState.QUEUED) return Result.success()

        val now = System.currentTimeMillis()
        val running = job.copy(state = ConsoleBuildJobState.RUNNING, updatedAtEpochMillis = now, errorCode = null)
        if (!store.update(running).success) return Result.failure()
        job = running
        store.appendLog(jobId, "INFO", "Source staging started for ${job.targetId.stableId}")
        var temporary: File? = null
        try {
            setForegroundAsync(foregroundInfo(job.sourceDisplayName, 0)).get(15, TimeUnit.SECONDS)
            checkCancelled(jobId)
            val uri = Uri.parse(job.sourceUri)
            val persisted = applicationContext.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (!persisted) throw StageFailure("SOURCE_PERMISSION_LOST", "Persisted SAF read permission is unavailable")

            val extension = job.sourceDisplayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in ConsoleRecompilerTargets.find(job.targetId)?.acceptedExtensions.orEmpty()) {
                throw StageFailure("UNSUPPORTED_SOURCE_FORMAT", "File extension is not supported by this target")
            }

            val expectedSize = querySize(uri)
            if (expectedSize != null && expectedSize <= 0L) throw StageFailure("EMPTY_SOURCE", "Selected source is empty")
            if (expectedSize != null && expectedSize > MAX_SOURCE_BYTES) throw StageFailure("SOURCE_TOO_LARGE", "Source exceeds the 64 GiB safety limit")
            val free = StatFs(applicationContext.filesDir.absolutePath).availableBytes
            if (expectedSize != null && free < expectedSize + MIN_FREE_SPACE) {
                throw StageFailure("INSUFFICIENT_STORAGE", "Not enough private storage to stage the selected source")
            }
            if (free < MIN_FREE_SPACE) throw StageFailure("INSUFFICIENT_STORAGE", "Less than 128 MiB free private storage")

            val workspaceRoot = File(applicationContext.filesDir, "console-workspaces")
            if (!workspaceRoot.exists() && !workspaceRoot.mkdirs()) throw StageFailure("WORKSPACE_CREATE_FAILED", "Unable to create workspace root")
            if (workspaceRoot.canonicalFile != workspaceRoot.absoluteFile || workspaceRoot.isSymbolicLink()) {
                throw StageFailure("UNSAFE_WORKSPACE", "Workspace root is not a safe directory")
            }
            val workspace = File(workspaceRoot, job.workspaceId)
            if (!workspace.exists() && !workspace.mkdirs()) throw StageFailure("WORKSPACE_CREATE_FAILED", "Unable to create job workspace")
            if (workspace.canonicalFile.parentFile != workspaceRoot.canonicalFile || workspace.isSymbolicLink() || !workspace.isDirectory) {
                throw StageFailure("UNSAFE_WORKSPACE", "Job workspace escaped the private workspace root")
            }

            val destination = File(workspace, "source.$extension")
            if (destination.exists()) throw StageFailure("WORKSPACE_COLLISION", "A staged source already exists for this job")
            temporary = File.createTempFile("source-", ".part", workspace)
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastUpdate = 0L
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temporary!!).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        checkCancelled(jobId)
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > MAX_SOURCE_BYTES) throw StageFailure("SOURCE_TOO_LARGE", "Source exceeds the 64 GiB safety limit")
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        if (expectedSize != null && copied > expectedSize + SIZE_TOLERANCE) {
                            throw StageFailure("SOURCE_SIZE_MISMATCH", "Provider returned more bytes than reported")
                        }
                        if (copied - lastUpdate >= PROGRESS_STEP_BYTES) {
                            lastUpdate = copied
                            val progress = if (expectedSize != null) (copied * 4L / expectedSize.coerceAtLeast(1L)).toInt().coerceIn(0, 4) else 0
                            job = job.copy(progressPercent = maxOf(job.progressPercent, progress), updatedAtEpochMillis = System.currentTimeMillis())
                            store.update(job)
                            setProgressAsync(Data.Builder().putLong(KEY_BYTES_COPIED, copied).putLong(KEY_TOTAL_BYTES, expectedSize ?: -1L).build())
                            setForegroundAsync(foregroundInfo(job.sourceDisplayName, progress))
                        }
                    }
                    output.fd.sync()
                }
            } ?: throw StageFailure("SOURCE_OPEN_FAILED", "Unable to open selected source URI")

            if (copied <= 0L) throw StageFailure("EMPTY_SOURCE", "Selected source is empty")
            if (expectedSize != null && copied != expectedSize) {
                throw StageFailure("SOURCE_SIZE_MISMATCH", "Copied byte count differs from provider metadata")
            }
            validateContainer(job.targetId, extension, temporary!!, copied)
            checkCancelled(jobId)

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val moved = try {
                java.nio.file.Files.move(
                    temporary!!.toPath(), destination.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                true
            } catch (_: Exception) { false }
            if (!moved) throw StageFailure("STAGING_COMMIT_FAILED", "Unable to atomically commit staged source")
            temporary = null

            val current = store.get(jobId) ?: throw StageFailure("JOB_MISSING", "Build job disappeared")
            if (current.state == ConsoleBuildJobState.CANCEL_REQUESTED || isStopped) {
                destination.delete()
                finishCancelled(current)
                return Result.success()
            }
            val staged = current.copy(
                sourceSha256 = sha256,
                stage = ConsoleBuildStage.SOURCE_STAGED,
                state = ConsoleBuildJobState.QUEUED,
                progressPercent = 5,
                completedStages = listOf(ConsoleBuildStage.VALIDATE_SOURCE),
                updatedAtEpochMillis = System.currentTimeMillis(),
                errorCode = null
            )
            val saved = store.update(staged)
            if (!saved.success) {
                destination.delete()
                throw StageFailure("JOB_UPDATE_FAILED", saved.error ?: "Unable to save staged source metadata")
            }
            store.appendLog(jobId, "INFO", "Source staged and SHA-256 verified; bytes=$copied sha256=$sha256")
            store.appendLog(jobId, "INFO", "Compiler pipeline is not integrated yet; job remains queued at SOURCE_STAGED")
            setForegroundAsync(foregroundInfo(job.sourceDisplayName, 100, completed = true))
            return Result.success()
        } catch (cancelled: StageCancelled) {
            temporary?.delete()
            val current = store.get(jobId)
            if (current != null) finishCancelled(current)
            return Result.success()
        } catch (failure: StageFailure) {
            temporary?.delete()
            fail(jobId, failure.code, failure.message ?: failure.code)
            return Result.failure(Data.Builder().putString(KEY_ERROR, failure.code).build())
        } catch (failure: Exception) {
            temporary?.delete()
            fail(jobId, "SOURCE_IO_ERROR", failure.message ?: failure.javaClass.simpleName)
            return Result.failure(Data.Builder().putString(KEY_ERROR, "SOURCE_IO_ERROR").build())
        }
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()

    private fun validateContainer(target: ConsoleTargetId, extension: String, file: File, size: Long) {
        when (target) {
            ConsoleTargetId.XBOX_360 -> {
                if (size < MIN_XBOX_SOURCE_BYTES) throw StageFailure("SOURCE_TOO_SMALL", "Xbox 360 source is unexpectedly small")
                if (extension == "zip") {
                    var isoFound = false
                    var entries = 0
                    try {
                        ZipFile(file).use { zip ->
                            val enumeration = zip.entries()
                            while (enumeration.hasMoreElements()) {
                                val entry = enumeration.nextElement()
                                entries++
                                if (entries > MAX_ZIP_ENTRIES) throw StageFailure("ARCHIVE_TOO_MANY_ENTRIES", "ZIP contains too many entries")
                                val name = entry.name
                                val parts = name.replace('\\', '/').split('/')
                                if (name.startsWith('/') || name.contains('\\') || name.contains('\u0000') ||
                                    parts.any { it == ".." } || parts.any { it == "." }) {
                                    throw StageFailure("UNSAFE_ARCHIVE_PATH", "ZIP contains an unsafe entry path")
                                }
                                if (!entry.isDirectory && name.substringAfterLast('.', "").equals("iso", true)) isoFound = true
                            }
                        }
                    } catch (e: StageFailure) { throw e }
                    catch (_: Exception) { throw StageFailure("INVALID_XBOX_ARCHIVE", "Selected ZIP is not a valid readable archive") }
                    if (!isoFound) throw StageFailure("XBOX_ISO_NOT_FOUND", "Xbox 360 ZIP must contain an ISO image")
                }
            }
            ConsoleTargetId.NINTENDO_SWITCH -> {
                if (size < MIN_SWITCH_SOURCE_BYTES) throw StageFailure("SOURCE_TOO_SMALL", "Nintendo Switch package is unexpectedly small")
                // NSP/XCI/NSZ/XCZ headers are validated by the pinned nx2android extraction toolchain,
                // not guessed here. This intake stage only confirms non-empty, bounded source bytes.
            }
        }
    }

    private fun checkCancelled(jobId: String) {
        if (isStopped) throw StageCancelled()
        val state = store.get(jobId)?.state
        if (state == ConsoleBuildJobState.CANCEL_REQUESTED || state == ConsoleBuildJobState.CANCELLED) throw StageCancelled()
    }

    private fun finishCancelled(job: ConsoleBuildJob) {
        if (job.state == ConsoleBuildJobState.CANCELLED) return
        if (job.state == ConsoleBuildJobState.RUNNING || job.state == ConsoleBuildJobState.CANCEL_REQUESTED) {
            store.update(job.copy(state = ConsoleBuildJobState.CANCELLED, updatedAtEpochMillis = System.currentTimeMillis()))
            store.appendLog(job.jobId, "WARN", "Source staging cancelled")
        }
    }

    private fun fail(jobId: String, code: String, message: String) {
        val current = store.get(jobId) ?: return
        if (!current.state.isTerminal && current.state != ConsoleBuildJobState.CANCEL_REQUESTED) {
            store.update(current.copy(state = ConsoleBuildJobState.FAILED, errorCode = code, updatedAtEpochMillis = System.currentTimeMillis()))
        } else if (current.state == ConsoleBuildJobState.CANCEL_REQUESTED) {
            finishCancelled(current)
        }
        store.appendLog(jobId, "ERROR", "$code: $message")
    }

    private fun foregroundInfo(name: String, progress: Int, completed: Boolean = false): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Console source staging", NotificationManager.IMPORTANCE_LOW))
        }
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (completed) "NGR source staged" else "NGR: preparing source")
            .setContentText(if (completed) "$name · waiting for compiler integration" else "$name · copying and verifying")
            .setOnlyAlertOnce(true)
            .setOngoing(!completed)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
        val id = (inputData.getString(KEY_JOB_ID) ?: "ngr").hashCode().and(0x7fffffff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id, notification)
    }

    private class StageFailure(val code: String, message: String) : Exception(message)
    private class StageCancelled : Exception()

    companion object {
        const val KEY_JOB_ID = "console_build_job_id"
        const val KEY_BYTES_COPIED = "bytes_copied"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error_code"
        const val WORK_TAG = "console-source-staging"
        private const val CHANNEL_ID = "ngr_console_source_staging"
        private const val MAX_SOURCE_BYTES = 64L * 1024L * 1024L * 1024L
        private const val MIN_FREE_SPACE = 128L * 1024L * 1024L
        private const val MIN_XBOX_SOURCE_BYTES = 1024L * 1024L
        private const val MIN_SWITCH_SOURCE_BYTES = 4096L
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_STEP_BYTES = 16L * 1024L * 1024L
        private const val SIZE_TOLERANCE = 0L
        private const val MAX_ZIP_ENTRIES = 100_000

        fun uniqueWorkName(jobId: String) = "console-source-stage-$jobId"
    }
}

private fun File.isSymbolicLink(): Boolean =
    runCatching { java.nio.file.Files.isSymbolicLink(toPath()) }.getOrDefault(false)
