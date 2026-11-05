package dev.pvps.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun readsChecksumForTheSelectedApk() {
        val hash = "a".repeat(64)
        val other = "b".repeat(64)
        val sums = """
            $other  pvps-1.0.1.apk
            $hash *pvps-1.0.2.apk
        """.trimIndent()
        assertEquals(hash, checksumForAsset(sums, "pvps-1.0.2.apk"))
        assertEquals(other, checksumForAsset(sums, "pvps-1.0.1.apk"))
        assertNull(checksumForAsset(sums, "pvps-9.9.9.apk"))
        assertNull(checksumForAsset("not a checksum", "pvps-1.0.2.apk"))
    }

    @Test
    fun normalizesTags() {
        assertEquals("1.2.3", normalizeVersion("v1.2.3"))
        assertEquals("1.2.3", normalizeVersion("V1.2.3"))
    }
}
