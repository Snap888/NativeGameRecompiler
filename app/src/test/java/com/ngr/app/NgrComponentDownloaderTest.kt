package com.ngr.app

import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgrComponentDownloaderTest {
    private class FakeHttpsConnection(
        url: URL,
        private val status: Int,
        private val payload: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap()
    ) : HttpsURLConnection(url) {
        var disconnected = false

        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun connect() {}
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getInputStream() = ByteArrayInputStream(payload)
        override fun getCipherSuite(): String = "TLS_FAKE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal = Principal { "fake-peer" }
        override fun getLocalPrincipal(): Principal? = null
    }

    private fun component(hash: String, url: String = "https://downloads.example.org/component.zip") =
        NgrComponentCatalog.Component(
            id = "box64",
            type = NgrComponentCatalog.Type.BOX64,
            version = "1",
            targetAbis = setOf(NgrComponentCatalog.TargetAbi.ANDROID_ARM64_V8A),
            archiveFormat = NgrComponentCatalog.ArchiveFormat.ZIP,
            downloadUrl = url,
            sha256 = hash,
            licenseSpdx = "MIT",
            sourceRepository = "https://github.com/ptitSeb/box64",
            installSubdirectory = "runtime/box64"
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
        val result = NgrComponentDownloader.download(
            component("a".repeat(64), "http://example.org/box64.zip"),
            File(System.getProperty("java.io.tmpdir")),
            connectionFactory = { throw AssertionError("network must not be opened") }
        )
        assertFalse(result.success)
        assertTrue(result.error.orEmpty().contains("HTTPS"))
    }

    @Test fun downloadsAndVerifiesPayloadAndDisconnects() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val directory = java.nio.file.Files.createTempDirectory("ngr-download-test-").toFile()
        var connection: FakeHttpsConnection? = null
        try {
            val result = NgrComponentDownloader.download(
                component(sha256(payload)),
                directory,
                connectionFactory = { uri ->
                    FakeHttpsConnection(URL(uri.toString()), 200, payload, mapOf("Content-Length" to payload.size.toString()))
                        .also { connection = it }
                }
            )
            assertTrue(result.error ?: "download failed", result.success)
            assertEquals(payload.toList(), result.file!!.readBytes().toList())
            assertEquals(payload.size.toLong(), result.bytes)
            assertTrue(connection!!.disconnected)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun followsHttpsRedirectAndRejectsDowngrade() {
        val payload = byteArrayOf(9, 8, 7)
        val directory = java.nio.file.Files.createTempDirectory("ngr-download-redirect-").toFile()
        val requested = mutableListOf<String>()
        try {
            val result = NgrComponentDownloader.download(
                component(sha256(payload)),
                directory,
                connectionFactory = { uri ->
                    requested += uri.toString()
                    if (uri.host == "downloads.example.org") {
                        FakeHttpsConnection(URL(uri.toString()), 302, headers = mapOf("Location" to "https://cdn.example.org/component.zip"))
                    } else {
                        FakeHttpsConnection(URL(uri.toString()), 200, payload, mapOf("Content-Length" to payload.size.toString()))
                    }
                }
            )
            assertTrue(result.error ?: "redirect download failed", result.success)
            assertEquals(2, requested.size)
            assertTrue(requested.last().startsWith("https://cdn.example.org/"))

            result.file?.delete()
            val downgrade = NgrComponentDownloader.download(
                component(sha256(payload)),
                directory,
                connectionFactory = { uri ->
                    FakeHttpsConnection(URL(uri.toString()), 302, headers = mapOf("Location" to "http://evil.example.org/payload"))
                }
            )
            assertFalse(downgrade.success)
            assertTrue(downgrade.error.orEmpty().contains("downgrade"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun deletesPartialFileWhenHashOrSizeValidationFails() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val directory = java.nio.file.Files.createTempDirectory("ngr-download-failure-").toFile()
        try {
            val badHash = NgrComponentDownloader.download(
                component("0".repeat(64)),
                directory,
                connectionFactory = { uri -> FakeHttpsConnection(URL(uri.toString()), 200, payload) }
            )
            assertFalse(badHash.success)
            assertTrue(badHash.error.orEmpty().contains("SHA-256 mismatch"))
            assertEquals(0, directory.listFiles()?.size ?: 0)

            val tooLarge = NgrComponentDownloader.download(
                component(sha256(payload)),
                directory,
                NgrComponentDownloader.Limits(maxBytes = 2),
                connectionFactory = { uri -> FakeHttpsConnection(URL(uri.toString()), 200, payload) }
            )
            assertFalse(tooLarge.success)
            assertTrue(tooLarge.error.orEmpty().contains("size limit"))
            assertEquals(0, directory.listFiles()?.size ?: 0)
        } finally {
            directory.deleteRecursively()
        }
    }
}
