package dev.pvps.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRetryTest {
    @Test
    fun backsOffExponentiallyThenCaps() {
        assertEquals(2_000L, SshManager.reconnectDelayMs(0))
        assertEquals(4_000L, SshManager.reconnectDelayMs(1))
        assertEquals(8_000L, SshManager.reconnectDelayMs(2))
        assertEquals(16_000L, SshManager.reconnectDelayMs(3))
        assertEquals(32_000L, SshManager.reconnectDelayMs(4))
        assertEquals(45_000L, SshManager.reconnectDelayMs(5))
        assertEquals(45_000L, SshManager.reconnectDelayMs(64))
    }

    @Test
    fun jitterStaysWithinTheAttemptWindow() {
        val base = SshManager.reconnectDelayMs(0)
        val jittered = SshManager.reconnectDelayMs(0, jitter = 500)
        assertTrue(jittered in base..(base + 500))
        // A caller cannot push the delay past the cap.
        assertEquals(45_000L, SshManager.reconnectDelayMs(6, jitter = 1_000_000))
    }

    @Test
    fun negativeAttemptsAndJitterAreClamped() {
        assertEquals(2_000L, SshManager.reconnectDelayMs(-3))
        assertEquals(2_000L, SshManager.reconnectDelayMs(0, jitter = -100))
    }
}
