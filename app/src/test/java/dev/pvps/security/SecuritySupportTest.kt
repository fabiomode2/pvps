package dev.pvps.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class SecuritySupportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun safeLeafNameAcceptsSimpleName() {
        assertEquals("report.txt", SecuritySupport.safeLeafName("report.txt"))
    }

    @Test
    fun safeLeafNameTrimsOuterWhitespace() {
        assertEquals("report.txt", SecuritySupport.safeLeafName("  report.txt  "))
    }

    @Test
    fun safeLeafNameAccepts255ByteName() {
        val name = "a".repeat(255)
        assertEquals(name, SecuritySupport.safeLeafName(name))
    }

    @Test
    fun safeLeafNameRejectsEmptyName() {
        assertUnsafeName("")
        assertUnsafeName("   ")
    }

    @Test
    fun safeLeafNameRejectsDotNames() {
        assertUnsafeName(".")
        assertUnsafeName("..")
    }

    @Test
    fun safeLeafNameRejectsPathSeparators() {
        assertUnsafeName("../outside.txt")
        assertUnsafeName("nested/file.txt")
        assertUnsafeName("nested\\file.txt")
    }

    @Test
    fun safeLeafNameRejectsNulAndOversizedNames() {
        assertUnsafeName("report\u0000.txt")
        assertUnsafeName("a".repeat(256))
    }

    @Test
    fun containedFileResolvesBelowRoot() {
        val root = tempFolder.newFolder("cache")
        val child = SecuritySupport.containedFile(root, "report.txt")

        assertEquals(File(root.canonicalFile, "report.txt"), child)
        assertTrue(child.toPath().startsWith(root.canonicalFile.toPath()))
    }

    @Test
    fun containedFileRejectsTraversalNames() {
        val root = tempFolder.newFolder("cache")

        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.containedFile(root, "../outside.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.containedFile(root, "nested/file.txt")
        }
    }

    @Test
    fun containedFileRejectsSymlinkEscape() {
        val root = tempFolder.newFolder("cache")
        val outside = tempFolder.newFile("outside.txt")
        val link = File(root, "link.txt")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        assumeTrue("Symbolic links are not supported on this platform", created)

        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.containedFile(root, "link.txt")
        }
    }

    @Test
    fun safeRemoteChildHandlesRootAndTrailingSlash() {
        assertEquals("/report.txt", SecuritySupport.safeRemoteChild("/", "report.txt"))
        assertEquals("/report.txt", SecuritySupport.safeRemoteChild("", "report.txt"))
        assertEquals("/home/user/report.txt", SecuritySupport.safeRemoteChild("/home/user", "report.txt"))
        assertEquals("/home/user/report.txt", SecuritySupport.safeRemoteChild("/home/user/", "report.txt"))
    }

    @Test
    fun safeRemoteChildKeepsLeadingDashOperand() {
        assertEquals("/home/user/-rf", SecuritySupport.safeRemoteChild("/home/user", "-rf"))
    }

    @Test
    fun safeRemoteChildRejectsTraversalName() {
        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.safeRemoteChild("/home/user", "../outside.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.safeRemoteChild("/home/user", "..")
        }
    }

    @Test
    fun readLimitedBytesReadsExactLimit() {
        val payload = ByteArray(64 * 1024 + 17) { index -> (index % 251).toByte() }

        val result = ByteArrayInputStream(payload).use { input ->
            SecuritySupport.readLimitedBytes(input, payload.size)
        }

        assertArrayEquals(payload, result)
    }

    @Test
    fun readLimitedBytesRejectsOversizedStream() {
        val payload = ByteArray(1024) { 7 }

        val error = assertThrows(IOException::class.java) {
            ByteArrayInputStream(payload).use { input ->
                SecuritySupport.readLimitedBytes(input, payload.size - 1)
            }
        }

        assertTrue(error.message.orEmpty().contains("limit"))
    }

    @Test
    fun copyLimitedCopiesExactLimit() {
        val payload = ByteArray(100_000) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(payload).use { input ->
            SecuritySupport.copyLimited(input, output, payload.size.toLong())
        }

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test
    fun copyLimitedRejectsOversizedStream() {
        val payload = ByteArray(1024) { 7 }
        val output = ByteArrayOutputStream()

        val error = assertThrows(IOException::class.java) {
            ByteArrayInputStream(payload).use { input ->
                SecuritySupport.copyLimited(input, output, payload.size - 1L)
            }
        }

        assertTrue(error.message.orEmpty().contains("limit"))
        assertTrue(output.size() <= payload.size - 1)
    }

    @Test
    fun copyLimitedHandlesEmptyStream() {
        val output = ByteArrayOutputStream()

        val copied = ByteArrayInputStream(ByteArray(0)).use { input ->
            SecuritySupport.copyLimited(input, output, 1024L)
        }

        assertEquals(0L, copied)
        assertEquals(0L, output.size().toLong())
    }

    private fun assertUnsafeName(raw: String) {
        assertThrows(IllegalArgumentException::class.java) {
            SecuritySupport.safeLeafName(raw)
        }
    }
}
