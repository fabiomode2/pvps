package dev.pvps.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Owns update state, downloads release APKs into the app cache, verifies them, and
 * hands them to the system package installer.
 *
 * Checks and downloads are serialized with a [Mutex] so two UI actions cannot race on
 * the same state. The suspend functions run on [Dispatchers.IO]; callers only need to
 * launch them from a lifecycle-aware scope.
 */
class UpdateRepository(
    context: Context,
    private val checker: UpdateChecker = UpdateChecker(context),
    private val client: OkHttpClient = defaultDownloadClient()
) {
    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * True when Android will reject the install intent until the user grants
     * "install unknown apps" to PVPS. Always false below API 26.
     */
    private val _needsUnknownSources = MutableStateFlow(requiresUnknownSources())
    val needsUnknownSources: StateFlow<Boolean> = _needsUnknownSources.asStateFlow()

    private val operationMutex = Mutex()

    /**
     * Checks GitHub for a newer release.
     *
     * Non-forced calls are throttled to one request per six hours by [UpdateChecker].
     * They also leave an already-visible prompt alone, so a screen resume cannot replace
     * "update available" with "idle". Use `force = true` for a manual refresh.
     */
    suspend fun check(force: Boolean = false) {
        operationMutex.withLock {
            val current = _state.value
            if (current is UpdateState.Downloading) return
            if (!force && (current is UpdateState.Available || current is UpdateState.ReadyToInstall)) return

            _state.value = UpdateState.Checking
            try {
                val info = checker.check(force)
                _state.value = if (info == null) UpdateState.Idle else UpdateState.Available(info)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Idle
                throw e
            } catch (e: Exception) {
                // Automatic checks stay silent; only a manual check surfaces the error.
                _state.value = if (force) {
                    UpdateState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Could not check for updates"
                    )
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    /**
     * Downloads the APK for [info], verifies it when a digest is available, then opens
     * the system installer.
     *
     * On Android 8+ the system installer is gated by the per-app "install unknown apps"
     * permission. If it is missing, this method keeps the verified APK in the cache,
     * exposes [needsUnknownSources], and opens the corresponding Settings screen instead
     * of failing. Once the user grants the permission, call [installReady] (or call this
     * method again) to launch the installer without re-downloading.
     */
    suspend fun downloadAndInstall(info: UpdateInfo) {
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                if (_state.value is UpdateState.Downloading) return@withContext

                val directory = File(appContext.cacheDir, "updates")
                if (!directory.exists() && !directory.mkdirs()) {
                    _state.value = UpdateState.Error("Could not create the update cache directory")
                    return@withContext
                }

                val target = File(directory, "pvps-${sanitizeVersion(info.version)}.apk")
                val part = File(directory, "${target.name}.part")

                try {
                    if (!isUsableCachedApk(target, info)) {
                        val expectedHash = info.sha256
                            ?: throw UpdateException("No SHA-256 checksum is available; refusing to install")
                        _state.value = UpdateState.Downloading(0f)
                        downloadTo(info, part)

                        if (info.size > 0 && part.length() != info.size) {
                            throw UpdateException("Downloaded APK size does not match the release metadata")
                        }
                        if (!sha256Matches(part, expectedHash)) {
                            throw UpdateException("Downloaded APK failed SHA-256 verification")
                        }

                        if (target.exists() && !target.delete()) {
                            throw UpdateException("Could not replace the cached APK")
                        }
                        if (!part.renameTo(target)) {
                            part.copyTo(target, overwrite = true)
                            part.delete()
                        }
                    }
                } catch (e: CancellationException) {
                    part.delete()
                    _state.value = UpdateState.Idle
                    throw e
                } catch (e: Exception) {
                    part.delete()
                    _state.value = UpdateState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Update download failed"
                    )
                    return@withContext
                }

                _state.value = UpdateState.ReadyToInstall(info, target.absolutePath)
                if (requiresUnknownSources()) {
                    _needsUnknownSources.value = true
                    openUnknownSourcesSettings()
                    return@withContext
                }
                _needsUnknownSources.value = false
                launchInstaller(target)
            }
        }
    }

    /**
     * Launches the installer for the already-downloaded APK, after the user returns
     * from granting "install unknown apps". Does nothing unless the state is
     * [UpdateState.ReadyToInstall].
     */
    fun installReady() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        val file = File(ready.apkPath)
        if (!file.isFile) {
            _state.value = UpdateState.Error("The downloaded update is missing. Download it again.")
            return
        }

        val needsPermission = requiresUnknownSources()
        _needsUnknownSources.value = needsPermission
        if (needsPermission) {
            openUnknownSourcesSettings()
            return
        }
        launchInstaller(file)
    }

    /** Re-reads the "install unknown apps" permission, for use when the app resumes. */
    fun refreshUnknownSources() {
        _needsUnknownSources.value = requiresUnknownSources()
    }

    /**
     * Opens the per-app "install unknown apps" screen. Falls back to the app details
     * page on devices where the dedicated screen is unavailable.
     */
    fun openUnknownSourcesSettings() {
        val packageUri = Uri.parse("package:${appContext.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        )

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ContextCompat.startActivity(appContext, intent, null)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next Settings screen.
            } catch (_: SecurityException) {
                // Some OEMs restrict the dedicated screen; keep falling back.
            }
        }

        _state.value = UpdateState.Error(
            "Open Android Settings and allow installing unknown apps for PVPS"
        )
    }

    /** Clears a prompt or error. An active download must be cancelled by its caller. */
    fun dismiss() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }

    private suspend fun downloadTo(info: UpdateInfo, part: File) {
        if (!info.apkUrl.startsWith("https://")) {
            throw UpdateException("Refusing to download an update over an insecure connection")
        }
        if (info.size > MAX_UPDATE_BYTES) {
            throw UpdateException("The update APK is larger than the supported limit")
        }

        val cacheDir = part.parentFile ?: appContext.cacheDir
        ensureCacheSpace(cacheDir, info.size)

        val request = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "pvps-android")
            .header("Accept", "application/octet-stream")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateException("Could not download the update. Check your connection.", e)
        }

        response.use { result ->
            if (!result.isSuccessful) {
                throw UpdateException("Update download failed with HTTP ${result.code}")
            }
            val body = result.body ?: throw UpdateException("Update download returned an empty response")
            val total = body.contentLength().takeIf { it > 0L }
                ?: info.size.takeIf { it > 0L }
                ?: -1L
            if (total > MAX_UPDATE_BYTES) {
                throw UpdateException("The update APK is larger than the supported limit")
            }
            ensureCacheSpace(cacheDir, total.takeIf { it > 0L } ?: 0L)

            var lastPercent = -1
            var nextSpaceCheck = SPACE_CHECK_INTERVAL_BYTES
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        // InputStream.read is blocking, so check cancellation each chunk.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break

                        val nextTotal = downloaded + read
                        if (nextTotal > MAX_UPDATE_BYTES) {
                            throw UpdateException("Update download exceeded the maximum allowed size")
                        }
                        if (nextTotal >= nextSpaceCheck) {
                            ensureCacheSpace(cacheDir)
                            nextSpaceCheck = nextTotal + SPACE_CHECK_INTERVAL_BYTES
                        }

                        output.write(buffer, 0, read)
                        downloaded = nextTotal

                        if (total > 0L) {
                            val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = UpdateState.Downloading(
                                    (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (total > 0L && part.length() != total) {
                throw UpdateException("Update download was truncated")
            }
            _state.value = UpdateState.Downloading(1f)
        }
    }

    private fun ensureCacheSpace(directory: File, additionalBytes: Long = 0L) {
        val usable = directory.usableSpace
        // getUsableSpace() reports 0 when the value is unknown; do not turn an
        // unknown filesystem into a hard failure for normal users.
        if (usable <= 0L) return
        val required = FREE_SPACE_RESERVE_BYTES + additionalBytes.coerceAtLeast(0L)
        if (usable < required) {
            throw UpdateException("Not enough free space to download the update")
        }
    }

    /**
     * Reuses a previous download only when it can be trusted: matching size and a
     * matching SHA-256. Without a checksum the cache is never trusted.
     */
    private fun isUsableCachedApk(file: File, info: UpdateInfo): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (file.length() > MAX_UPDATE_BYTES) {
            file.delete()
            return false
        }
        if (info.size > 0L && file.length() != info.size) {
            file.delete()
            return false
        }

        // Without a published checksum the cached file cannot be trusted; force a
        // fresh download so the mandatory verification step runs.
        val expectedHash = info.sha256 ?: run {
            file.delete()
            return false
        }
        // A cache read failure should fall through to a fresh download, not abort the update.
        val matches = runCatching { sha256Matches(file, expectedHash) }.getOrDefault(false)
        if (!matches) {
            file.delete()
            return false
        }
        return true
    }

    private fun sha256Matches(file: File, expectedHex: String): Boolean {
        val expected = expectedHex.trim().substringAfter(':').trim().lowercase(Locale.US)
        if (expected.length != 64) return false

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
        return actual == expected
    }

    private fun launchInstaller(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.files",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // End this update attempt before handing over to the system installer.
            // Otherwise onResume() would relaunch the installer every time the
            // user cancels it and returns to PVPS.
            _state.value = UpdateState.Idle
            ContextCompat.startActivity(appContext, intent, null)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Error("No package installer is available on this device")
        } catch (e: Exception) {
            _state.value = UpdateState.Error(
                "Could not open the package installer: ${e.message ?: "unknown error"}"
            )
        }
    }

    /**
     * True when Android will reject the install intent until the user grants
     * "install unknown apps" to PVPS. Always false below API 26.
     *
     * The app manifest must also declare `android.permission.REQUEST_INSTALL_PACKAGES`.
     * Without that declaration `canRequestPackageInstalls()` stays false and the
     * per-app toggle is not offered by Settings.
     */
    private fun requiresUnknownSources(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        // Some OEM package managers have thrown here; treat an error as "not required"
        // so repository construction can never crash the app.
        return runCatching {
            !appContext.packageManager.canRequestPackageInstalls()
        }.getOrDefault(false)
    }

    private fun sanitizeVersion(version: String): String =
        version.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.')
            .ifBlank { "latest" }
            .take(64)

    companion object {
        private const val MAX_UPDATE_BYTES = 256L * 1024L * 1024L
        private const val FREE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L
        private const val SPACE_CHECK_INTERVAL_BYTES = 4L * 1024L * 1024L

        private fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .build()
    }
}
