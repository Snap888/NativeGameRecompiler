package com.ngr.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Xbox360InputProbeTest {
    private fun bytes(value: String) = value.toByteArray(Charsets.US_ASCII)

    @Test fun detectsXex2Executable() {
        assertEquals(Xbox360InputProbe.Kind.XEX2, Xbox360InputProbe.detect(bytes("XEX2")))
    }

    @Test fun detectsAllStfsPackageTypes() {
        assertEquals(Xbox360InputProbe.Kind.STFS_CON, Xbox360InputProbe.detect(bytes("CON ")))
        assertEquals(Xbox360InputProbe.Kind.STFS_LIVE, Xbox360InputProbe.detect(bytes("LIVE")))
        assertEquals(Xbox360InputProbe.Kind.STFS_PIRS, Xbox360InputProbe.detect(bytes("PIRS")))
    }

    @Test fun rejectsShortAndUnknownHeaders() {
        assertNull(Xbox360InputProbe.detect(byteArrayOf(0x58, 0x45, 0x58)))
        assertNull(Xbox360InputProbe.detect(bytes("ELF!")))
    }
}
