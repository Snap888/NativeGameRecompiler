package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class N64RomInputProbeTest {
    @Test fun detectsAllRetailRomByteOrders() {
        assertEquals(N64RomInputProbe.ByteOrder.BIG_ENDIAN_Z64,
            N64RomInputProbe.detect(byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)))
        assertEquals(N64RomInputProbe.ByteOrder.BYTE_SWAPPED_V64,
            N64RomInputProbe.detect(byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12)))
        assertEquals(N64RomInputProbe.ByteOrder.WORD_SWAPPED_N64,
            N64RomInputProbe.detect(byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte())))
    }

    @Test fun rejectsShortAndInvalidHeaders() {
        assertNull(N64RomInputProbe.detect(byteArrayOf(0x80.toByte(), 0x37, 0x12)))
        assertNull(N64RomInputProbe.detect("ELF!".toByteArray(Charsets.US_ASCII)))
        assertNull(N64RomInputProbe.detect(ByteArray(32)))
    }
}
