package com.ngr.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentDownloaderTest {
    @Test fun acceptsOnlyCredentialFreeHttpsUrls() {
        assertTrue(NgrComponentDownloader.isAllowedHttpsUrl("https://github.com/example/release.zip"))
        assertTrue(NgrComponentDownloader.isAllowedHttpsUrl("https://downloads.example.org:8443/file.tar.zst"))
        assertFalse(NgrComponentDownloader.isAllowedHttpsUrl("http://example.org/file.zip"))
        assertFalse(NgrComponentDownloader.isAllowedHttpsUrl("https://user:password@example.org/file.zip"))
        assertFalse(NgrComponentDownloader.isAllowedHttpsUrl("https://example.org/file.zip#fragment"))
        assertFalse(NgrComponentDownloader.isAllowedHttpsUrl("https:///missing-host.zip"))
        assertFalse(NgrComponentDownloader.isAllowedHttpsUrl("not a URL"))
    }

    @Test fun rejectsInvalidDownloadMetadataBeforeNetworkAccess() {
        val component = NgrComponentCatalog.Component(
            id = "box64",
            type = NgrComponentCatalog.Type.BOX64,
            version = "1",
            targetAbis = setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A),
            archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
            downloadUrl = "http://example.org/box64.zip",
            sha256 = "a".repeat(64),
            licenseSpdx = "MIT",
            sourceRepository = "https://github.com/ptitSeb/box64",
            installSubdirectory = "runtime/box64"
        )
        val result = NgrComponentDownloader.download(
            component,
            java.io.File(System.getProperty("java.io.tmpdir"))
        )
        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("HTTPS"))
    }
}
