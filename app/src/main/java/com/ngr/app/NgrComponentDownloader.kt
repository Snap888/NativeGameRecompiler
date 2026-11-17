package com.ngr.app

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Bounded HTTPS-only component downloader.
 *
 * A successful result is a verified private file. It is not installed, activated or executed.
 */
object NgrComponentDownloader {
    data class Limits(
        val maxBytes: Long = 512L * 1024 * 1024,
        val connectTimeoutMs: Int = 15_000,
        val readTimeoutMs: Int = 30_000,
        val maxRedirects: Int = 5
    )

    data class Result(
        val success: Boolean,
        val file: File? = null,
        val error: String? = null,
        val bytes: Long = 0
    )

    fun download(
        component: NgrComponentCatalog.Component,
        privateDirectory: File,
        limits: Limits = Limits()
    ): Result {
        if (!component.id.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$"))) {
            return Result(false, error = "Component ID is invalid")
        }
        if (!component.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            return Result(false, error = "Component SHA-256 metadata is invalid")
        }
        if (!isAllowedHttpsUrl(component.downloadUrl)) {
            return Result(false, error = "Component download URL must use HTTPS without credentials")
        }
        if (limits.maxBytes <= 0 || limits.connectTimeoutMs <= 0 || limits.readTimeoutMs <= 0 ||
            limits.maxRedirects !in 0..10) {
            return Result(false, error = "Download limits are invalid")
        }
        if (!privateDirectory.isDirectory || !privateDirectory.canWrite()) {
            return Result(false, error = "Download directory is not writable")
        }

        var current = URI(component.downloadUrl)
        val visited = mutableSetOf<String>()
        var temporary: File? = null
        try {
            for (redirectCount in 0..limits.maxRedirects) {
                require(isAllowedHttpsUri(current)) { "Redirect target is not an allowed HTTPS URL" }
                val normalized = current.normalize()
                require(visited.add(normalized.toString())) { "Redirect loop detected" }

                val connection = normalized.toURL().openConnection() as? HttpsURLConnection
                    ?: throw SecurityException("Download did not use HTTPS")
                try {
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "GET"
                    connection.connectTimeout = limits.connectTimeoutMs
                    connection.readTimeout = limits.readTimeoutMs
                    connection.useCaches = false
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("User-Agent", "NativeGameRecompiler/0.1")

                    val status = connection.responseCode
                    if (status in REDIRECT_CODES) {
                        if (redirectCount >= limits.maxRedirects) throw SecurityException("Too many HTTPS redirects")
                        val location = connection.getHeaderField("Location")
                            ?: throw SecurityException("HTTPS redirect is missing Location")
                        current = normalized.resolve(location)
                        require(isAllowedHttpsUri(current)) { "Redirect attempted HTTPS downgrade or unsafe URL" }
                        continue
                    }
                    if (status !in 200..299) throw IllegalStateException("Component server returned HTTP $status")

                    val encoding = connection.getHeaderField("Content-Encoding")
                    if (!encoding.isNullOrBlank() && !encoding.equals("identity", ignoreCase = true)) {
                        throw SecurityException("Encoded HTTP responses are not supported")
                    }
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength > limits.maxBytes) throw SecurityException("Component download exceeds size limit")

                    temporary = File.createTempFile("ngr-${component.id}-", ".verified", privateDirectory)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(temporary, false).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                received += count
                                if (received > limits.maxBytes) {
                                    throw SecurityException("Component download exceeded streaming size limit")
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    if (received <= 0L) throw IllegalStateException("Component download is empty")
                    if (declaredLength >= 0L && received != declaredLength) {
                        throw SecurityException("Component download length does not match Content-Length")
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(component.sha256, ignoreCase = true)) {
                        throw SecurityException("Component download SHA-256 mismatch")
                    }
                    val verified = temporary
                    temporary = null
                    return Result(true, file = verified, bytes = received)
                } finally {
                    connection.disconnect()
                }
            }
            return Result(false, error = "Component redirect processing did not complete")
        } catch (e: Exception) {
            temporary?.delete()
            return Result(false, error = e.message ?: "Component download failed")
        }
    }

    internal fun isAllowedHttpsUrl(value: String): Boolean =
        try { isAllowedHttpsUri(URI(value)) } catch (_: Exception) { false }

    private fun isAllowedHttpsUri(uri: URI): Boolean =
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port in 1..65535) &&
            uri.fragment == null &&
            !uri.toString().contains(0.toChar())

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
