package dev.pvps.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellQuoteTest {
    @Test
    fun quotesSingleQuotes() {
        assertEquals("'a'\\''b'", ShellQuote.of("a'b"))
    }

    @Test
    fun fingerprintIsSha256() {
        val fp = sha256Fingerprint(byteArrayOf(1, 2, 3))
        assertTrue(fp.startsWith("SHA256:"))
        assertTrue(fp.length > 12)
    }
}
