package dev.pvps.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

@Serializable
data class SetupPayload(
    val v: Int = 1,
    val name: String = "vps",
    val host: String = "",
    val port: Int = 22,
    val user: String = "ubuntu",
    val ocPort: Int = 49374,
    val ocUser: String = "opencode",
    val ocPassword: String = "",
    val mode: String = "tunnel",
    val directory: String = ""
)

object SetupCode {
    private const val PREFIX = "pvps-setup:"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): Result<SetupPayload> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Setup code is empty" }
        val payload = if (trimmed.startsWith(PREFIX)) trimmed.removePrefix(PREFIX) else trimmed
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val bytes = Base64.getUrlDecoder().decode(padded)
        val decoded = json.decodeFromString(SetupPayload.serializer(), String(bytes, Charsets.UTF_8))
        require(decoded.host.isNotBlank()) { "Setup code has no host" }
        require(decoded.user.isNotBlank()) { "Setup code has no user" }
        require(decoded.port in 1..65535) { "Setup code has an invalid SSH port" }
        require(decoded.ocPort in 1..65535) { "Setup code has an invalid OpenCode port" }
        require(decoded.ocUser.isNotBlank()) { "Setup code has no OpenCode user" }
        require(decoded.ocPassword.isNotBlank()) { "Setup code has no OpenCode password" }
        decoded
    }

    fun toHost(payload: SetupPayload): Host = Host(
        id = UUID.randomUUID().toString(),
        name = payload.name.ifBlank { "vps" },
        sshHost = payload.host.trim(),
        sshPort = payload.port,
        sshUser = payload.user.trim(),
        authType = AuthType.KEY,
        keyId = null,
        openCodeMode = if (payload.mode.equals("direct", ignoreCase = true)) {
            OpenCodeMode.DIRECT
        } else {
            OpenCodeMode.SSH_TUNNEL
        },
        openCodePort = payload.ocPort,
        openCodeUsername = payload.ocUser.ifBlank { "opencode" },
        openCodePassword = payload.ocPassword,
        openCodeDirectory = payload.directory
    )
}
