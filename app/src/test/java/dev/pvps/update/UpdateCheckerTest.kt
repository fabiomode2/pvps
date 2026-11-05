package dev.pvps.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0-rc.1", "1.0.0"))
    }

    @Test
    fun normalizesTags() {
        assertEquals("1.2.3", normalizeVersion("v1.2.3"))
        assertEquals("1.2.3", normalizeVersion("V1.2.3"))
    }
}
