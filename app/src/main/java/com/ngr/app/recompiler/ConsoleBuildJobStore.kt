package com.ngr.app.recompiler

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Atomic app-private job metadata store. Compiler execution is intentionally a separate layer. */
class ConsoleBuildJobStore(private val privateRoot: File) {
    data class StoreResult(val success: Boolean, val error: String? = null)
    data class LoadResult(val jobs: List<ConsoleBuildJob>, val errors: List<String>)
    private val lock = Any()

    fun create(job: ConsoleBuildJob): StoreResult = synchronized(lock) {
        val errors = job.validate()
        if (errors.isNotEmpty()) return@synchronized StoreResult(false, errors.joinToString("; "))
        if (job.state != ConsoleBuildJobState.QUEUED || job.stage != ConsoleBuildStage.VALIDATE_SOURCE ||
            job.progressPercent != 0 || job.completedStages.isNotEmpty()) {
            return@synchronized StoreResult(false, "New jobs must start queued at source validation")
        }
        runCatching {
            val dir = metadataDir()
            val target = File(dir, "${job.jobId}.json")
            require(!target.exists()) { "Job already exists" }
            require(dir.listFiles().orEmpty().count { it.name.endsWith(".json", true) } < MAX_JOBS) { "Job quota exceeded" }
            writeAtomic(target, encode(job))
        }.fold({ StoreResult(true) }, { StoreResult(false, it.message ?: "Unable to create job") })
    }

    fun get(jobId: String): ConsoleBuildJob? = synchronized(lock) {
        if (!SAFE_ID.matches(jobId)) return@synchronized null
        readJob(File(metadataDir(), "$jobId.json")).getOrNull()
    }

    fun loadAll(): LoadResult = synchronized(lock) {
        val dir = runCatching { metadataDir() }.getOrElse {
            return@synchronized LoadResult(emptyList(), listOf(it.message ?: "Unsafe job storage"))
        }
        val files = dir.listFiles().orEmpty().filter { it.name.endsWith(".json", true) }.sortedBy { it.name }
        if (files.size > MAX_JOBS) return@synchronized LoadResult(emptyList(), listOf("Job quota exceeded"))
        val jobs = mutableListOf<ConsoleBuildJob>()
        val errors = mutableListOf<String>()
        files.forEach { file ->
            readJob(file).onSuccess { job ->
                if (file.name == "${job.jobId}.json") jobs += job
                else errors += "${file.name}: filename does not match jobId"
            }.onFailure { errors += "${file.name}: ${it.message ?: "invalid metadata"}" }
        }
        LoadResult(jobs.sortedByDescending { it.updatedAtEpochMillis }, errors)
    }

    /** Mutable execution state may change; source identity and creation fields cannot. */
    fun update(job: ConsoleBuildJob): StoreResult = synchronized(lock) {
        val errors = job.validate()
        if (errors.isNotEmpty()) return@synchronized StoreResult(false, errors.joinToString("; "))
        runCatching {
            val target = File(metadataDir(), "${job.jobId}.json")
            val old = readJob(target).getOrThrow()
            require(!old.state.isTerminal) { "Terminal jobs are immutable" }
            require(old.state == job.state || canTransition(old.state, job.state)) { "Invalid state transition" }
            require(old.targetId == job.targetId && old.sourceUri == job.sourceUri &&
                old.sourceDisplayName == job.sourceDisplayName && old.sourceGrantFlags == job.sourceGrantFlags &&
                old.profileId == job.profileId && old.workspaceId == job.workspaceId &&
                old.createdAtEpochMillis == job.createdAtEpochMillis) { "Job source identity is immutable" }
            require(canSetOnce(old.sourceSha256, job.sourceSha256) &&
                canSetOnce(old.profileFingerprint, job.profileFingerprint) &&
                canSetOnce(old.toolchainFingerprint, job.toolchainFingerprint)) { "Fingerprints are immutable once assigned" }
            require(job.updatedAtEpochMillis >= old.updatedAtEpochMillis) { "Timestamp moved backwards" }
            require(job.progressPercent >= old.progressPercent) { "Progress moved backwards" }
            val stages = ConsoleBuildJob.stagesFor(job.targetId)
            require(stages.indexOf(job.stage) >= stages.indexOf(old.stage)) { "Stage moved backwards" }
            require(job.completedStages.containsAll(old.completedStages)) { "Completed stages cannot be removed" }
            require(job.completedStages.all { stages.indexOf(it) < stages.indexOf(job.stage) ||
                (job.stage == ConsoleBuildStage.COMPLETE && it == ConsoleBuildStage.COMPLETE) }) { "Completed stage list is inconsistent" }
            writeAtomic(target, encode(job))
        }.fold({ StoreResult(true) }, { StoreResult(false, it.message ?: "Unable to update job") })
    }

    fun requestCancel(jobId: String, now: Long = System.currentTimeMillis()): StoreResult = synchronized(lock) {
        val job = get(jobId) ?: return@synchronized StoreResult(false, "Job not found")
        when (job.state) {
            ConsoleBuildJobState.QUEUED -> update(job.copy(state = ConsoleBuildJobState.CANCELLED, updatedAtEpochMillis = now))
            ConsoleBuildJobState.RUNNING -> update(job.copy(state = ConsoleBuildJobState.CANCEL_REQUESTED, updatedAtEpochMillis = now))
            ConsoleBuildJobState.CANCEL_REQUESTED, ConsoleBuildJobState.CANCELLED -> StoreResult(true)
            else -> StoreResult(false, "Terminal job cannot be cancelled")
        }
    }

    /** On process restart, interrupted workers return to the queue; pending cancellation is finalized. */
    fun recoverInterruptedJobs(now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        var count = 0
        loadAll().jobs.forEach { old ->
            val replacement = when (old.state) {
                ConsoleBuildJobState.RUNNING -> old.copy(state = ConsoleBuildJobState.QUEUED, updatedAtEpochMillis = now)
                ConsoleBuildJobState.CANCEL_REQUESTED -> old.copy(state = ConsoleBuildJobState.CANCELLED, updatedAtEpochMillis = now)
                else -> null
            }
            if (replacement != null && update(replacement).success) count++
        }
        count
    }

    fun appendLog(jobId: String, level: String, message: String, now: Long = System.currentTimeMillis()): StoreResult =
        synchronized(lock) {
            if (get(jobId) == null) return@synchronized StoreResult(false, "Job not found")
            if (!SAFE_LEVEL.matches(level)) return@synchronized StoreResult(false, "Invalid log level")
            runCatching {
                val file = logFile(jobId)
                val safe = redact(message).replace(Regex("[\\r\\n\\u0000-\\u001F]"), " ").take(MAX_LOG_LINE)
                val combined = (if (file.exists()) file.readText(Charsets.UTF_8) else "") + "$now $level $safe\n"
                val bounded = if (combined.toByteArray(Charsets.UTF_8).size <= MAX_LOG_BYTES) combined else combined.takeLast(MAX_LOG_CHARS)
                writeAtomic(file, bounded)
            }.fold({ StoreResult(true) }, { StoreResult(false, it.message ?: "Unable to write job log") })
        }

    fun readLog(jobId: String, maxChars: Int = MAX_LOG_CHARS): String? = synchronized(lock) {
        if (!SAFE_ID.matches(jobId) || maxChars !in 1..MAX_LOG_CHARS) return@synchronized null
        runCatching {
            val file = logFile(jobId)
            if (file.isFile) file.readText(Charsets.UTF_8).takeLast(maxChars) else ""
        }.getOrNull()
    }

    private fun readJob(file: File): Result<ConsoleBuildJob> = runCatching {
        require(file.exists() && !Files.isSymbolicLink(file.toPath()) && file.isFile) { "Job file is not regular" }
        require(file.length() in 1..MAX_JOB_BYTES.toLong()) { "Job file is empty or too large" }
        decode(file.readText(Charsets.UTF_8))
    }

    private fun rootDir(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath()) && privateRoot.isDirectory) { "Storage root is unsafe" }
        val dir = File(privateRoot, "console-build-jobs")
        Files.createDirectories(dir.toPath())
        require(!Files.isSymbolicLink(dir.toPath()) && dir.isDirectory) { "Job root is unsafe" }
        return dir
    }

    private fun metadataDir(): File {
        val dir = File(rootDir(), "metadata")
        Files.createDirectories(dir.toPath())
        require(!Files.isSymbolicLink(dir.toPath()) && dir.isDirectory) { "Metadata directory is unsafe" }
        return dir
    }

    private fun logFile(id: String): File {
        require(SAFE_ID.matches(id)) { "Invalid job ID" }
        val dir = File(rootDir(), "logs")
        Files.createDirectories(dir.toPath())
        require(!Files.isSymbolicLink(dir.toPath()) && dir.isDirectory) { "Log directory is unsafe" }
        val file = File(dir, "$id.log")
        require(!Files.isSymbolicLink(file.toPath()) && (!file.exists() || file.isFile)) { "Log file is unsafe" }
        return file
    }

    private fun writeAtomic(target: File, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (target.name.endsWith(".json")) require(bytes.size <= MAX_JOB_BYTES) { "Job metadata too large" }
        val temp = File.createTempFile("job-", ".tmp", target.parentFile)
        try {
            FileOutputStream(temp).use { out -> out.write(bytes); out.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    private fun encode(j: ConsoleBuildJob): String = JSONObject().apply {
        put("schemaVersion", SCHEMA)
        put("jobId", j.jobId); put("targetId", j.targetId.stableId)
        put("sourceUri", j.sourceUri); put("sourceDisplayName", j.sourceDisplayName)
        put("sourceGrantFlags", j.sourceGrantFlags); put("profileId", j.profileId)
        put("workspaceId", j.workspaceId)
        put("sourceSha256", j.sourceSha256 ?: JSONObject.NULL)
        put("profileFingerprint", j.profileFingerprint ?: JSONObject.NULL)
        put("toolchainFingerprint", j.toolchainFingerprint ?: JSONObject.NULL)
        put("stage", j.stage.name); put("state", j.state.name)
        put("progressPercent", j.progressPercent)
        put("completedStages", JSONArray(j.completedStages.map { it.name }))
        put("createdAtEpochMillis", j.createdAtEpochMillis); put("updatedAtEpochMillis", j.updatedAtEpochMillis)
        put("errorCode", j.errorCode ?: JSONObject.NULL)
    }.toString()

    private fun decode(raw: String): ConsoleBuildJob {
        val o = JSONObject(raw)
        val allowed = setOf("schemaVersion", "jobId", "targetId", "sourceUri", "sourceDisplayName",
            "sourceGrantFlags", "profileId", "workspaceId", "sourceSha256", "profileFingerprint",
            "toolchainFingerprint", "stage", "state", "progressPercent", "completedStages",
            "createdAtEpochMillis", "updatedAtEpochMillis", "errorCode")
        val keys = o.keys()
        while (keys.hasNext()) require(keys.next() in allowed) { "Unknown metadata field" }
        require(o.getInt("schemaVersion") == SCHEMA) { "Unsupported schema version" }
        val target = ConsoleTargetId.values().firstOrNull { it.stableId == o.getString("targetId") }
            ?: throw IllegalArgumentException("Unknown console target")
        val stages = o.getJSONArray("completedStages")
        require(stages.length() <= ConsoleBuildStage.values().size) { "Too many completed stages" }
        fun optional(key: String): String? = if (o.isNull(key)) null else o.getString(key)
        val job = ConsoleBuildJob(
            jobId = o.getString("jobId"), targetId = target, sourceUri = o.getString("sourceUri"),
            sourceDisplayName = o.getString("sourceDisplayName"), sourceGrantFlags = o.getInt("sourceGrantFlags"),
            profileId = o.getString("profileId"), workspaceId = o.getString("workspaceId"),
            sourceSha256 = optional("sourceSha256"), profileFingerprint = optional("profileFingerprint"),
            toolchainFingerprint = optional("toolchainFingerprint"),
            stage = ConsoleBuildStage.valueOf(o.getString("stage")),
            state = ConsoleBuildJobState.valueOf(o.getString("state")),
            progressPercent = o.getInt("progressPercent"),
            completedStages = (0 until stages.length()).map { ConsoleBuildStage.valueOf(stages.getString(it)) },
            createdAtEpochMillis = o.getLong("createdAtEpochMillis"),
            updatedAtEpochMillis = o.getLong("updatedAtEpochMillis"), errorCode = optional("errorCode")
        )
        require(job.validate().isEmpty()) { job.validate().joinToString("; ") }
        return job
    }

    private fun canSetOnce(old: String?, new: String?): Boolean = old == new || (old == null && new != null)

    private fun redact(value: String): String = value.replace(
        Regex("(?i)(prod\\.keys|title[_ -]?key|device[_ -]?key|password|token)\\s*[:=]\\s*[^\\s,;]+"),
        "$1=[REDACTED]"
    )

    private fun canTransition(from: ConsoleBuildJobState, to: ConsoleBuildJobState): Boolean = when (from) {
        ConsoleBuildJobState.QUEUED -> to in setOf(ConsoleBuildJobState.RUNNING, ConsoleBuildJobState.CANCELLED, ConsoleBuildJobState.FAILED)
        ConsoleBuildJobState.RUNNING -> to in setOf(ConsoleBuildJobState.CANCEL_REQUESTED, ConsoleBuildJobState.CANCELLED,
            ConsoleBuildJobState.FAILED, ConsoleBuildJobState.SUCCEEDED, ConsoleBuildJobState.QUEUED)
        ConsoleBuildJobState.CANCEL_REQUESTED -> to in setOf(ConsoleBuildJobState.CANCELLED, ConsoleBuildJobState.FAILED)
        else -> false
    }

    companion object {
        private const val SCHEMA = 1
        private const val MAX_JOBS = 256
        private const val MAX_JOB_BYTES = 64 * 1024
        private const val MAX_LOG_BYTES = 256 * 1024
        private const val MAX_LOG_CHARS = 128 * 1024
        private const val MAX_LOG_LINE = 8 * 1024
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val SAFE_LEVEL = Regex("^(INFO|WARN|ERROR|DEBUG)$")
    }
}
