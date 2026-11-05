package dev.pvps.update

import android.content.Context
import dev.pvps.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/** Failure while talking to GitHub or interpreting a release. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads the latest GitHub release and decides whether it is newer than this build.
 *
 * Only public, unauthenticated GitHub API calls are made. The unauthenticated quota is
 * small, so non-forced checks are throttled to one network request every [THROTTLE_MS].
 * A successful request refreshes the throttle window even when no update is available.
 */
class UpdateChecker(
    context: Context,
    private val client: OkHttpClient = defaultHttpClient()
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Returns the newer release, or null when the app is current, the release is a
     * prerelease/draft, or a non-forced check is still inside the 6-hour window.
     *
     * @throws UpdateException when GitHub cannot be reached, returns an error, or the
     * release has no usable APK asset. Callers should surface the message to the user.
     */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && !isThrottleExpired()) return@withContext null

        val release = fetchLatestRelease()
        // Start the window only after a successful API response. Failed requests should
        // be retryable immediately, for example after the user restores connectivity.
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

        if (release.draft || release.prerelease) return@withContext null

        val tag = release.tagName.trim()
        if (tag.isEmpty()) return@withContext null
        if (!isNewerVersion(tag, BuildConfig.VERSION_NAME)) return@withContext null

        val asset = selectApkAsset(release.assets)
            ?: throw UpdateException("The latest release does not contain an APK")
        val apkUrl = asset.browserDownloadUrl.trim()
        if (!apkUrl.startsWith("https://")) {
            throw UpdateException("The latest APK is not served over HTTPS")
        }

        UpdateInfo(
            version = normalizeVersion(tag),
            tag = tag,
            notes = release.body.orEmpty(),
            apkUrl = apkUrl,
            size = asset.size,
            sha256 = parseSha256(asset.digest),
            htmlUrl = release.htmlUrl
        )
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateException("Could not reach GitHub. Check your connection.", e)
        }

        response.use { result ->
            // A repo with no releases yet is not an error; it simply has no update.
            if (result.code == 404) return GitHubRelease()
            if (!result.isSuccessful) {
                val detail = result.body?.string()?.trim()?.take(300).orEmpty()
                val message = when (result.code) {
                    403 -> "GitHub API rate limit reached. Try again later."
                    else -> "GitHub API returned HTTP ${result.code}."
                }
                throw UpdateException(if (detail.isEmpty()) message else "$message $detail")
            }

            val text = result.body?.string()
                ?: throw UpdateException("GitHub returned an empty response")
            return try {
                json.decodeFromString(GitHubRelease.serializer(), text)
            } catch (e: Exception) {
                throw UpdateException("Could not parse the GitHub release response", e)
            }
        }
    }

    /** Prefer the release's `pvps-*.apk` asset, then any other `.apk` asset. */
    private fun selectApkAsset(assets: List<GitHubAsset>): GitHubAsset? {
        val apks = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }
        return apks.firstOrNull { it.name.startsWith("pvps-", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.startsWith("pvps.", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    /**
     * Accepts `sha256:<hex>` as returned by the GitHub asset `digest` field. Returns
     * null for missing, unsupported, or malformed digests; in that case the download
     * is still size-checked but cannot be hash-verified.
     */
    private fun parseSha256(digest: String?): String? {
        val raw = digest?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (':' in raw) {
            val algorithm = raw.substringBefore(':').trim().lowercase()
            if (algorithm != "sha256") return null
        }
        val hex = raw.substringAfter(':').trim().lowercase()
        return hex.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    private fun isThrottleExpired(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (last <= 0L) return true
        val elapsed = System.currentTimeMillis() - last
        // Treat a backwards clock jump as expired instead of throttling forever.
        return elapsed >= THROTTLE_MS || elapsed < 0L
    }

    companion object {
        const val THROTTLE_MS: Long = 6 * 60 * 60 * 1000L
        private const val PREFS_NAME = "updates"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val USER_AGENT = "pvps-android"
    }
}

/**
 * Removes a leading `v`/`V` for display and for the cache file name.
 * The original tag is kept in [UpdateInfo.tag].
 */
fun normalizeVersion(version: String): String {
    val trimmed = version.trim()
    return trimmed.removePrefix("v").removePrefix("V").ifBlank { trimmed }
}

/** True when [candidate] is strictly newer than [current]. */
fun isNewerVersion(candidate: String, current: String): Boolean =
    compareVersions(candidate, current) > 0

/**
 * Numeric semantic-version comparison that tolerates tags such as `v1.2.3`,
 * missing components (`1.2` == `1.2.0`), build metadata (`+build`), and prerelease
 * suffixes (`-rc1`, which are ignored). Uses [BigInteger] so oversized components
 * cannot overflow. Returns a negative value, zero, or a positive value like
 * [Comparable.compareTo].
 */
fun compareVersions(left: String, right: String): Int {
    val a = numericVersionParts(left)
    val b = numericVersionParts(right)
    val size = maxOf(a.size, b.size)
    for (index in 0 until size) {
        val x = a.getOrElse(index) { BigInteger.ZERO }
        val y = b.getOrElse(index) { BigInteger.ZERO }
        val result = x.compareTo(y)
        if (result != 0) return result
    }
    return 0
}

private fun numericVersionParts(version: String): List<BigInteger> {
    val core = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-') // semver prerelease suffix: ignored
        .substringBefore('+') // build metadata: ignored
    if (core.isBlank()) return emptyList()
    return core.split('.').map { part ->
        val digits = part.takeWhile { it.isDigit() }
        if (digits.isEmpty()) BigInteger.ZERO else BigInteger(digits)
    }
}

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()
