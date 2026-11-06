package dev.pvps.security

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Small, dependency-free guards for values that cross a trust boundary.
 *
 * These helpers are deliberately strict: callers should surface the resulting
 * IllegalArgumentException to the user instead of silently falling back to an
 * unsafe path or URI.
 */
object SecuritySupport {

    const val MAX_UPLOAD_BYTES: Long = 512L * 1024L * 1024L
    const val MAX_DOWNLOAD_BYTES: Long = 512L * 1024L * 1024L

    /** Reduce a remote/provider-supplied display name to one safe path component. */
    fun safeLeafName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "File name is empty" }
        require(name != "." && name != "..") { "Unsafe file name" }
        require(!name.contains('/') && !name.contains('\\') && !name.contains('\u0000')) {
            "File name contains a path separator"
        }
        require(name.toByteArray(Charsets.UTF_8).size <= 255) { "File name is too long" }
        return name
    }

    /**
     * Build a file directly below [root] and verify containment after resolving
     * symlinks and `..` components. The child is always a single leaf name.
     */
    fun containedFile(root: File, rawName: String): File {
        val canonicalRoot = root.canonicalFile
        val child = File(canonicalRoot, safeLeafName(rawName)).canonicalFile
        require(child.path.startsWith(canonicalRoot.path + File.separator)) {
            "File escapes the app cache"
        }
        return child
    }

    /** Join a safe single-component name below a remote directory. */
    fun safeRemoteChild(base: String, rawName: String): String {
        val name = safeLeafName(rawName)
        val normalizedBase = when {
            base.isBlank() -> "/"
            base.length > 1 && base.endsWith('/') -> base.dropLast(1)
            else -> base
        }
        return if (normalizedBase == "/") "/$name" else "$normalizedBase/$name"
    }

    /** Reject file:// and the app's own unexported FileProvider on picker results. */
    fun requirePickerReadUri(context: Context, uri: Uri) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Only content:// documents can be selected"
        }
        require(!isOwnFileProvider(context, uri)) {
            "Refusing to read an app-private file"
        }
    }

    fun requirePickerWriteUri(context: Context, uri: Uri) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Only content:// destinations can be selected"
        }
        require(!isOwnFileProvider(context, uri)) {
            "Refusing to write an app-private file"
        }
    }

    private fun isOwnFileProvider(context: Context, uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        val packageName = context.packageName
        return authority == "$packageName.files" ||
            authority == packageName ||
            authority.startsWith("$packageName.")
    }

    /** Read at most [maxBytes] and fail instead of silently truncating. */
    fun readLimitedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("File exceeds the ${maxBytes / (1024 * 1024)} MB limit")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Copy at most [maxBytes] and fail instead of silently truncating. */
    fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("File exceeds the ${maxBytes / (1024 * 1024)} MB limit")
            output.write(buffer, 0, read)
        }
        return total
    }
}
