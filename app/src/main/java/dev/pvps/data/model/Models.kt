package dev.pvps.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AuthType { KEY, PASSWORD }

@Serializable
enum class OpenCodeMode { SSH_TUNNEL, DIRECT }

@Serializable
data class Host(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sshHost: String,
    val sshPort: Int = 22,
    val sshUser: String = "ubuntu",
    val authType: AuthType = AuthType.KEY,
    val keyId: String? = null,
    val password: String = "",
    val hostKeySha256: String? = null,
    val openCodeMode: OpenCodeMode = OpenCodeMode.SSH_TUNNEL,
    val openCodeHost: String = "",
    val openCodePort: Int = 49374,
    val openCodeUsername: String = "opencode",
    val openCodePassword: String = "",
    val openCodeDirectory: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
) {
    val effectiveOpenCodeHost: String
        get() = openCodeHost.ifBlank { sshHost }
}

@Serializable
data class SshKey(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val privateKey: String,
    val publicKey: String = "",
    val passphrase: String = "",
    val algorithm: String = "ed25519",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class SecureData(
    val hosts: List<Host> = emptyList(),
    val keys: List<SshKey> = emptyList()
)

@Serializable
data class AppSettings(
    val biometricLock: Boolean = false,
    val blockScreenshots: Boolean = false,
    val terminalFontSize: Float = 13f,
    val terminalTheme: String = "tokyo-night",
    val keepScreenOnTerminal: Boolean = true,
    val showReasoning: Boolean = true,
    val compactToolCards: Boolean = true,
    val strictHostKeys: Boolean = true,
    val uiTheme: String = "graphite",
    val fontScale: Float = 1f,
    val uiScale: Float = 1f,
    val fileGridView: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val notifyResponses: Boolean = true,
    val lastModelProvider: String = "",
    val lastModelId: String = "",
    val lastModelVariant: String = "",
    val lastAgentId: String = ""
)

@Serializable
data class AuditEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val hostName: String,
    val action: String,
    val detail: String = "",
    val success: Boolean = true
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
    val combined: String get() = buildString {
        if (stdout.isNotBlank()) append(stdout)
        if (stderr.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(stderr)
        }
    }.trim()
}

data class ConnectionState(
    val hostId: String,
    val status: Status,
    val message: String = "",
    val hostKeySha256: String? = null
) {
    enum class Status { DISCONNECTED, CONNECTING, CONNECTED, NEEDS_HOST_KEY, ERROR }
}
