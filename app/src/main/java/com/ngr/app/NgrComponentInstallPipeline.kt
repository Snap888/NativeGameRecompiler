package com.ngr.app

import java.io.File

/**
 * Coordinates verified download, ZIP staging and atomic component activation.
 * Non-ZIP archive formats remain explicitly unsupported.
 */
class NgrComponentInstallPipeline(private val store: NgrComponentStore) {
    data class Result(
        val success: Boolean,
        val activeDirectory: File? = null,
        val version: String? = null,
        val error: String? = null
    )

    fun downloadAndInstall(
        component: NgrComponentCatalog.Component,
        downloadLimits: NgrComponentDownloader.Limits = NgrComponentDownloader.Limits(),
        archiveLimits: NgrComponentArchiveInstaller.Limits = NgrComponentArchiveInstaller.Limits()
    ): Result {
        return try {
            val downloaded = NgrComponentDownloader.download(component, store.downloadDirectory(), downloadLimits)
            if (!downloaded.success) return Result(false, error = downloaded.error ?: "Component download failed")
            val archive = downloaded.file ?: return Result(false, error = "Downloader returned no verified file")
            try {
                installVerifiedArchive(component, archive, archiveLimits)
            } finally {
                archive.delete()
            }
        } catch (e: Exception) {
            Result(false, error = e.message ?: "Component download/install failed")
        }
    }

    /**
     * Installs a local archive only after the archive installer independently verifies its SHA-256.
     * Useful for importing a component archive from a trusted local source or for deterministic tests.
     */
    fun installVerifiedArchive(
        component: NgrComponentCatalog.Component,
        archive: File,
        archiveLimits: NgrComponentArchiveInstaller.Limits = NgrComponentArchiveInstaller.Limits()
    ): Result {
        var staging: File? = null
        return try {
            val extracted = NgrComponentArchiveInstaller.extractZip(
                archive,
                component,
                store.stagingDirectory(),
                archiveLimits
            )
            if (!extracted.success) return Result(false, error = extracted.error ?: "Component extraction failed")
            val stagingDirectory = extracted.stagingDirectory
                ?: return Result(false, error = "Component extractor returned no staging directory")
            staging = stagingDirectory

            val promoted = store.installAndActivate(component, stagingDirectory)
            if (!promoted.success) {
                store.discardStagingDirectory(stagingDirectory)
                staging = null
                return Result(false, error = promoted.error ?: "Component activation failed")
            }
            staging = null
            val payloadDirectory = store.activeComponentPath(component)
                ?: throw IllegalStateException("Activated component payload directory is missing")
            Result(
                success = true,
                activeDirectory = payloadDirectory,
                version = promoted.activeVersion
            )
        } catch (e: Exception) {
            staging?.let(store::discardStagingDirectory)
            Result(false, error = e.message ?: "Component install failed")
        }
    }
}
