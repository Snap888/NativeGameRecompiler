package com.ngr.app.recompiler

import java.io.IOException

/** Bounds untrusted SAF data-pack trees while they are copied into NGR storage. */
internal class SwitchPortCopyBudget(
    private val maxFiles: Int = 250_000,
    private val maxEntries: Int = 500_000,
    private val maxTotalBytes: Long = 64L * 1024 * 1024 * 1024,
    private val maxSingleFileBytes: Long = 32L * 1024 * 1024 * 1024
) {
    var files: Int = 0
        private set
    var entries: Int = 0
        private set
    var totalBytes: Long = 0
        private set

    fun recordEntry(isFile: Boolean) {
        entries++
        if (entries > maxEntries) throw IOException("Switch data pack contains too many entries")
        if (isFile) {
            files++
            if (files > maxFiles) throw IOException("Switch data pack contains too many files")
        }
    }

    fun recordBytes(fileBytesAfterChunk: Long, chunkBytes: Int) {
        if (chunkBytes < 0 || fileBytesAfterChunk < chunkBytes.toLong()) {
            throw IOException("Invalid Switch data-pack byte count")
        }
        if (fileBytesAfterChunk > maxSingleFileBytes) {
            throw IOException("Switch data-pack file exceeds the per-file size limit")
        }
        if (totalBytes > maxTotalBytes - chunkBytes.toLong()) {
            throw IOException("Switch data pack exceeds the total size limit")
        }
        totalBytes += chunkBytes.toLong()
    }

    fun validateDepth(depth: Int) {
        if (depth > 128) throw IOException("Switch data pack directory nesting is too deep")
    }
}
