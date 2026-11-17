package com.ngr.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Bounded persistent diagnostic log. Logs contain technical metadata, not file contents. */
object NgrLogger {
    private const val TAG = "NGR"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val MAX_LINE = 12_000
    private val lock = Any()
    @Volatile private var logFile: File? = null
    private var sessionId: String = "uninitialized"

    fun initialize(context: Context) {
        synchronized(lock) {
            val directory = File(context.filesDir, "logs")
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e(TAG, "Unable to create private log directory")
                return
            }
            logFile = File(directory, "ngr-runtime.log")
            sessionId = UUID.randomUUID().toString().take(8)
            writeLocked("INFO", "app", "Diagnostic logging initialized; file=${logFile?.name}")
        }
    }

    fun info(category: String, message: String) = write("INFO", category, message)
    fun warn(category: String, message: String) = write("WARN", category, message)
    fun error(category: String, message: String, error: Throwable? = null) {
        val details = if (error == null) message else "$message: ${error.javaClass.simpleName}: ${error.message}\n" +
            error.stackTraceToString().take(8_000)
        write("ERROR", category, details)
    }

    fun recentText(maxChars: Int = 250_000): String = synchronized(lock) {
        val file = logFile ?: return@synchronized "NGR logger is not initialized"
        if (!file.isFile) return@synchronized "No log file yet"
        runCatching {
            val content = file.readText(Charsets.UTF_8)
            if (content.length <= maxChars) content else content.takeLast(maxChars)
        }.getOrElse { "Unable to read log: ${it.message}" }
    }

    fun copyTo(destination: File) = synchronized(lock) {
        val source = logFile ?: throw IllegalStateException("Logger is not initialized")
        if (!source.isFile) throw IllegalStateException("Log file does not exist")
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
    }

    fun filePath(): String = logFile?.absolutePath ?: "unavailable"

    private fun write(level: String, category: String, message: String) {
        synchronized(lock) { writeLocked(level, category, message) }
    }

    private fun writeLocked(level: String, category: String, message: String) {
        if (logFile == null) return
        val safeCategory = category.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(40)
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val line = "$timestamp $level/$safeCategory session=$sessionId [${Thread.currentThread().name}] ${message.replace("\r", "").take(MAX_LINE)}\n"
        when (level) {
            "ERROR" -> Log.e(TAG, line.trimEnd())
            "WARN" -> Log.w(TAG, line.trimEnd())
            else -> Log.i(TAG, line.trimEnd())
        }
        val file = logFile ?: return
        runCatching {
            if (file.exists() && file.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                val previous = File(file.parentFile, "ngr-runtime.previous.log")
                if (previous.exists()) previous.delete()
                if (!file.renameTo(previous)) file.delete()
            }
            file.appendText(line, Charsets.UTF_8)
        }.onFailure { Log.e(TAG, "Persistent log write failed", it) }
    }
}
