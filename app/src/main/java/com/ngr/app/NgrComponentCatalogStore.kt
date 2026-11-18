package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stores strictly parsed, untrusted component catalog metadata in app-private storage.
 * A catalog entry is not a trust signature and does not make its payload executable.
 */
class NgrComponentCatalogStore(private val privateRoot: File) {
    data class Entry(val catalog: NgrComponentCatalog, val file: File)
    data class Result(val success: Boolean, val entry: Entry? = null, val error: String? = null)
    data class LoadResult(val entries: List<Entry>, val errors: List<String>)

    fun save(json: String): Result {
        return try {
            require(json.toByteArray(Charsets.UTF_8).size in 1..NgrComponentCatalogParser.MAX_CATALOG_BYTES) {
                "Catalog is empty or exceeds the size limit"
            }
            val catalog = NgrComponentCatalogParser.parse(json)
            val directory = catalogDirectory()
            val target = File(directory, "${catalog.catalogId}.json")
            if (target.exists()) {
                require(!Files.isSymbolicLink(target.toPath()) && target.isFile) { "Catalog target is not a regular file" }
            } else {
                val count = directory.listFiles().orEmpty().count { it.name.endsWith(".json", true) }
                require(count < MAX_CATALOGS) { "Catalog count exceeds $MAX_CATALOGS" }
            }
            val temporary = File.createTempFile("catalog-", ".tmp", directory)
            try {
                FileOutputStream(temporary, false).use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            Result(true, Entry(catalog, target))
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Unable to save component catalog")
        }
    }

    fun loadAll(): LoadResult {
        val directory = try { catalogDirectory() }
        catch (e: Exception) { return LoadResult(emptyList(), listOf(e.message ?: "Catalog directory is unsafe")) }
        val files = directory.listFiles().orEmpty().filter { it.name.endsWith(".json", true) }.sortedBy { it.name }
        if (files.size > MAX_CATALOGS) return LoadResult(emptyList(), listOf("Catalog count exceeds $MAX_CATALOGS"))
        val entries = mutableListOf<Entry>()
        val errors = mutableListOf<String>()
        files.forEach { file ->
            try {
                require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Catalog file is not regular" }
                require(file.length() in 1..NgrComponentCatalogParser.MAX_CATALOG_BYTES.toLong()) {
                    "Catalog file is empty or exceeds size limit"
                }
                val catalog = NgrComponentCatalogParser.parse(file.readText(Charsets.UTF_8))
                require(file.name == "${catalog.catalogId}.json") { "Catalog filename does not match catalogId" }
                entries += Entry(catalog, file)
            } catch (e: Exception) {
                errors += "${file.name}: ${e.message ?: "invalid catalog"}"
            }
        }
        return LoadResult(entries, errors)
    }

    fun get(catalogId: String): Entry? {
        if (!SAFE_ID.matches(catalogId)) return null
        return loadAll().entries.firstOrNull { it.catalog.catalogId == catalogId }
    }

    fun delete(catalogId: String): Result {
        if (!SAFE_ID.matches(catalogId)) return Result(false, error = "Catalog ID is invalid")
        return try {
            val file = File(catalogDirectory(), "$catalogId.json")
            if (!file.exists()) return Result(true)
            require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "Catalog path is not a regular file" }
            require(file.delete()) { "Unable to delete catalog" }
            Result(true)
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Unable to delete catalog")
        }
    }

    private fun catalogDirectory(): File {
        Files.createDirectories(privateRoot.toPath())
        require(!Files.isSymbolicLink(privateRoot.toPath())) { "Catalog storage root is a symbolic link" }
        val directory = File(privateRoot, CATALOG_DIRECTORY)
        Files.createDirectories(directory.toPath())
        require(!Files.isSymbolicLink(directory.toPath()) && directory.isDirectory) { "Catalog directory is unsafe" }
        return directory
    }

    companion object {
        private const val CATALOG_DIRECTORY = "component-catalogs"
        private const val MAX_CATALOGS = 64
        private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    }
}
