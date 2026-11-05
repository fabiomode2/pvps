package dev.pvps.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConnectionTest {
    @Test
    fun detectsTailscaleCgnatRange() {
        assertTrue(isTailscaleAddress("100.64.0.1"))
        assertTrue(isTailscaleAddress("100.89.120.38"))
        assertTrue(isTailscaleAddress("100.127.255.254"))
        assertFalse(isTailscaleAddress("100.63.255.255"))
        assertFalse(isTailscaleAddress("100.128.0.1"))
        assertFalse(isTailscaleAddress("10.0.0.240"))
        assertFalse(isTailscaleAddress("130.110.235.61"))
        assertFalse(isTailscaleAddress("vps.example.com"))
    }
}
