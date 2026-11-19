package com.ngr.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Persistent intake record; a detected source is not necessarily a runnable game. */
data class NgrRecompilerProject(
    val projectId: String,
    val displayName: String,
    val systemId: String,
    val sourceUri: String,
    val sourceExtension: String,
    val status: Status,
    val detail: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    enum class Status { DETECTED, BACKEND_UNAVAILABLE, METADATA_REQUIRED, COMPILING, READY, FAILED }

    fun validate(): List<String> = buildList {
        if (!SAFE_ID.matches(projectId)) add("projectId is invalid")
        if (displayName.isBlank() || displayName.length > 240) add("displayName must contain 1..240 characters")
        if (systemId !in ALLOWED_SYSTEMS) add("systemId is unsupported")
        if (sourceUri.isBlank() || sourceUri.length > 8192 || sourceUri.contains('\u0000')) add("sourceUri is invalid")
        if (sourceExtension.length > 16 || sourceExtension.any { !it.isLetterOrDigit() }) add("sourceExtension is invalid")
        if (detail.length > 4096 || detail.contains('\u0000')) add("detail is invalid")
        if (createdAtEpochMillis < 0) add("createdAtEpochMillis cannot be negative")
    }

    companion object {
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        private val ALLOWED_SYSTEMS = setOf("N64_ROM", "N64_MIPS", "XBOX_360")
    }
}

/** Bounded, atomic project intake store, separate from launch shortcuts. */
class NgrRecompilerProjectStore(private val privateRoot: File) {
    data class LoadResult(val projects: List<NgrRecompilerProject>, val errors: List<String>)

    @Synchronized fun upsert(project: NgrRecompilerProject): kotlin.Result<Unit> {
        val invalid = project.validate()
        if (invalid.isNotEmpty()) return Result.failure(IllegalArgumentException(invalid.joinToString("; ")))
        return runCatching {
            val dir = directory()
            val current = loadAll().projects.toMutableList()
            val index = current.indexOfFirst { it.projectId == project.projectId }
            if (index >= 0) current[index] = project else current.add(project)
            require(current.size <= MAX_PROJECTS) { "Recompiler project limit reached" }
            val json = JSONObject().put("schemaVersion", 1).put("projects", JSONArray().apply {
                current.sortedBy { it.createdAtEpochMillis }.forEach { put(encode(it)) }
            })
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_STORE_BYTES) { "Recompiler project store is too large" }
            val target = File(dir, STORE_NAME)
            require(!Files.isSymbolicLink(target.toPath())) { "Project store path is unsafe" }
            val temp = File.createTempFile("projects-", ".tmp", dir)
            try {
                FileOutputStream(temp, false).use { out -> out.write(bytes); out.fd.sync() }
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
        }
    }

    @Synchronized fun loadAll(): LoadResult = runCatching {
        val target = File(directory(), STORE_NAME)
        if (!target.exists()) return LoadResult(emptyList(), emptyList())
        require(!Files.isSymbolicLink(target.toPath()) && target.isFile) { "Project store is unsafe" }
        require(target.length() in 1..MAX_STORE_BYTES.toLong()) { "Project store size is invalid" }
        val root = JSONObject(target.readText(Charsets.UTF_8))
        require(root.optInt("schemaVersion") == 1) { "Unsupported project store schema" }
        val array = root.optJSONArray("projects") ?: JSONArray()
        require(array.length() <= MAX_PROJECTS) { "Project count exceeds limit" }
        val projects = mutableListOf<NgrRecompilerProject>()
        val errors = mutableListOf<String>()
        for (i in 0 until array.length()) {
            runCatching { decode(array.getJSONObject(i)) }
                .onSuccess { project ->
                    val invalid = project.validate()
                    if (invalid.isEmpty()) projects += project else errors += "record $i: ${invalid.joinToString()}"
                }.onFailure { errors += "record $i: ${it.message}" }
        }
        LoadResult(projects, errors)
    }.getOrElse { LoadResult(emptyList(), listOf(it.message ?: "Unable to read project store")) }

    private fun directory(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Project storage root is unsafe" }
        val dir = File(privateRoot, "recompiler-projects")
        Files.createDirectories(dir.toPath())
        require(!Files.isSymbolicLink(dir.toPath()) && dir.isDirectory) { "Project directory is unsafe" }
        return dir
    }

    private fun encode(p: NgrRecompilerProject) = JSONObject().apply {
        put("projectId", p.projectId); put("displayName", p.displayName); put("systemId", p.systemId)
        put("sourceUri", p.sourceUri); put("sourceExtension", p.sourceExtension)
        put("status", p.status.name); put("detail", p.detail); put("createdAtEpochMillis", p.createdAtEpochMillis)
    }

    private fun decode(o: JSONObject) = NgrRecompilerProject(
        projectId = o.getString("projectId"), displayName = o.getString("displayName"),
        systemId = o.getString("systemId"), sourceUri = o.getString("sourceUri"),
        sourceExtension = o.optString("sourceExtension"), status = NgrRecompilerProject.Status.valueOf(o.getString("status")),
        detail = o.optString("detail"), createdAtEpochMillis = o.optLong("createdAtEpochMillis")
    )

    companion object {
        private const val STORE_NAME = "projects.json"
        private const val MAX_PROJECTS = 200
        private const val MAX_STORE_BYTES = 2 * 1024 * 1024
    }
}
