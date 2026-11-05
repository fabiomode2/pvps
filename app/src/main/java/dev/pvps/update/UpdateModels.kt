package dev.pvps.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset of the GitHub Releases API payload used by the updater.
 *
 * The [kotlinx.serialization.json.Json] instance in [UpdateChecker] ignores unknown
 * keys, so GitHub can add fields without breaking the parser.
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    /** GitHub asset digest, for example `sha256:abc...`. Null on older releases. */
    val digest: String? = null,
    @SerialName("content_type") val contentType: String? = null
)

/**
 * A newer release that can be downloaded.
 *
 * @param version normalized tag (leading `v` removed) used in the cache file name
 * @param tag original release tag, kept for display
 * @param notes release body as written on GitHub; may contain Markdown
 * @param apkUrl direct download URL of the APK asset
 * @param size asset size in bytes, or 0 when GitHub does not report it
 * @param sha256 lower-case hex SHA-256 digest, or null when the release has no digest
 * @param htmlUrl human-readable release page
 */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
    val htmlUrl: String,
    /** Release asset file name, used to look up the SHA256SUMS asset. */
    val apkName: String = ""
)

/** State exposed by [UpdateRepository] to the UI. */
sealed interface UpdateState {
    /** Nothing to show. */
    data object Idle : UpdateState

    /** A GitHub request is in flight. */
    data object Checking : UpdateState

    /** A newer release exists. */
    data class Available(val info: UpdateInfo) : UpdateState

    /** APK download in progress; [progress] is in the 0f..1f range. */
    data class Downloading(val progress: Float) : UpdateState

    /**
     * The APK is downloaded and verified.
     *
     * [apkPath] points into the app cache. The file is passed to the system installer
     * through a FileProvider URI, never through a `file://` URI.
     */
    data class ReadyToInstall(val info: UpdateInfo, val apkPath: String) : UpdateState

    /** User-visible failure. [message] is safe to display. */
    data class Error(val message: String) : UpdateState
}
