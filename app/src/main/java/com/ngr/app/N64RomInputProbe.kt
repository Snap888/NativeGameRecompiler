package com.ngr.app

/** Header-only Nintendo 64 ROM byte-order probe. Does not imply ROM decompilation support. */
object N64RomInputProbe {
    enum class ByteOrder { BIG_ENDIAN_Z64, BYTE_SWAPPED_V64, WORD_SWAPPED_N64 }

    fun detect(header: ByteArray): ByteOrder? {
        if (header.size < 4) return null
        val a = header[0].toInt() and 0xff
        val b = header[1].toInt() and 0xff
        val c = header[2].toInt() and 0xff
        val d = header[3].toInt() and 0xff
        return when {
            a == 0x80 && b == 0x37 && c == 0x12 && d == 0x40 -> ByteOrder.BIG_ENDIAN_Z64
            a == 0x37 && b == 0x80 && c == 0x40 && d == 0x12 -> ByteOrder.BYTE_SWAPPED_V64
            a == 0x40 && b == 0x12 && c == 0x37 && d == 0x80 -> ByteOrder.WORD_SWAPPED_N64
            else -> null
        }
    }
}
