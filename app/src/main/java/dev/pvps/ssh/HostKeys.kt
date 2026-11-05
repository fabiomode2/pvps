package dev.pvps.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

fun sha256Fingerprint(rawKey: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(rawKey)
    return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
}

fun sha256Fingerprint(base64Key: String): String =
    sha256Fingerprint(Base64.getDecoder().decode(base64Key))

/**
 * Trust-on-first-use repository. If a fingerprint is pinned it must match
 * exactly. If it is not pinned, [check] reports NOT_INCLUDED and captures the
 * fingerprint so the UI can ask the user to trust it explicitly.
 */
class PinnedHostKeyRepository(
    private val pinnedSha256: String?,
    private val autoTrust: Boolean,
    private val onSeen: (String) -> Unit
) : HostKeyRepository {

    override fun check(host: String, key: ByteArray): Int {
        val fingerprint = sha256Fingerprint(key)
        onSeen(fingerprint)
        return when {
            pinnedSha256.isNullOrBlank() && autoTrust -> HostKeyRepository.OK
            pinnedSha256.isNullOrBlank() -> HostKeyRepository.NOT_INCLUDED
            pinnedSha256.equals(fingerprint, ignoreCase = true) -> HostKeyRepository.OK
            else -> HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "pvps"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
