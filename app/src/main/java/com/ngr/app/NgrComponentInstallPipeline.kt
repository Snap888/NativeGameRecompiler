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
        val downloaded = NgrComponentDownloader.download(component, store.downloadDirectory(), downloadLimits)
        if (!downloaded.success) return Result(false, error = downloaded.error ?: "Component download failed")
        val archive = downloaded.file ?: return Result(false, error = "Downloader returned no verified file")
        return try {
            installVerifiedArchive(component, archive, archiveLimits)
        } finally {
            archive.delete()
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
        val extracted = NgrComponentArchiveInstaller.extractZip(
            archive,
            component,
            store.stagingDirectory(),
            archiveLimits
        )
        if (!extracted.success) return Result(false, error = extracted.error ?: "Component extraction failed")
        val staging = extracted.stagingDirectory
            ?: return Result(false, error = "Component extractor returned no staging directory")

        val promoted = store.installAndActivate(component, staging)
        if (!promoted.success) {
            store.discardStagingDirectory(staging)
            return Result(false, error = promoted.error ?: "Component activation failed")
        }
        return Result(
            success = true,
            activeDirectory = promoted.installedDirectory,
            version = promoted.activeVersion
        )
    }
}
