package dev.pvps.opencode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LocationRef(val directory: String = "")

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0,
    val busy: Long? = null,
    val idle: Long? = null,
    val viewed: Long? = null,
    val archived: Long? = null
)

@Serializable
data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: CacheUsage = CacheUsage()
)

@Serializable
data class CacheUsage(val read: Long = 0, val write: Long = 0)

@Serializable
data class ModelRef(
    val id: String = "",
    val providerID: String = "",
    val variant: String? = null
)

@Serializable
data class SessionInfo(
    val id: String = "",
    val projectID: String = "",
    val parentID: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage(),
    val time: SessionTime = SessionTime(),
    val title: String? = null,
    val location: LocationRef? = null,
    val subpath: String? = null,
    val outcome: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "Untitled session"
}

@Serializable
data class SessionsResponse(val data: List<SessionInfo> = emptyList())

@Serializable
data class SessionResponse(val data: SessionInfo = SessionInfo())

@Serializable
data class MessagesResponse(val data: List<MessageDto> = emptyList())

@Serializable
data class PermissionsResponse(val data: List<PermissionRequest> = emptyList())

@Serializable
data class ModelsResponse(val data: List<ModelInfo> = emptyList())

@Serializable
data class AgentsResponse(val data: List<AgentInfo> = emptyList())

@Serializable
data class ServerInfo(
    val version: String = "",
    val pid: Long = 0,
    val hostname: String? = null,
    val urls: List<String> = emptyList()
)

@Serializable
data class MessageTime(
    val created: Long = 0,
    val streamed: Long? = null,
    val completed: Long? = null
)

@Serializable
data class MessageDto(
    val id: String = "",
    val type: String = "",
    val time: MessageTime = MessageTime(),
    val text: String? = null,
    val description: String? = null,
    val files: List<FileAttachment> = emptyList(),
    val agents: List<AgentAttachment> = emptyList(),
    val skills: List<SkillAttachment> = emptyList(),
    val content: List<JsonElement> = emptyList(),
    val agent: String? = null,
    val model: ModelRef? = null,
    val finish: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsage? = null,
    val error: StructuredError? = null,
    val command: String? = null,
    val status: String? = null,
    val output: ShellOutput? = null,
    val outcome: String? = null,
    val summary: String? = null
)

@Serializable
data class FileAttachment(
    val uri: String = "",
    val name: String? = null,
    val description: String? = null,
    val mime: String? = null
)

@Serializable
data class AgentAttachment(val name: String = "")

@Serializable
data class SkillAttachment(val id: String = "")

@Serializable
data class ShellOutput(
    val output: String = "",
    val cursor: Long = 0,
    val size: Long = 0,
    val truncated: Boolean = false
)

@Serializable
data class StructuredError(
    val type: String = "",
    val message: String = "",
    val status: Int? = null
)

@Serializable
data class PermissionRequest(
    val id: String = "",
    val sessionID: String = "",
    val action: String = "",
    val resources: List<String> = emptyList(),
    val save: List<String> = emptyList(),
    val message: String? = null,
    val metadata: JsonObject? = null
)

@Serializable
data class ModelVariant(val id: String = "")

@Serializable
data class ModelLimit(val context: Long = 0, val output: Long = 0)

@Serializable
data class ModelInfo(
    val id: String = "",
    val modelID: String = "",
    val providerID: String = "",
    val name: String = "",
    val variants: List<ModelVariant> = emptyList(),
    val limit: ModelLimit? = null,
    val status: String = "",
    val enabled: Boolean = true
) {
    val label: String get() = if (name.isNotBlank()) name else id
}

@Serializable
data class AgentInfo(
    val id: String = "",
    val name: String = "",
    val mode: String = "",
    val hidden: Boolean = false,
    val description: String? = null
)

data class OpenCodeEvent(
    val id: String,
    val type: String,
    val data: JsonObject,
    val created: Long? = null,
    val directory: String? = null,
    val seq: Long? = null
)

sealed interface UiPart {
    data class Text(val text: String) : UiPart
    data class Reasoning(val text: String) : UiPart
    data class Tool(val tool: ToolPart) : UiPart
}

data class ToolPart(
    val id: String,
    val name: String,
    val status: String,
    val input: String,
    val content: List<ToolContent>,
    val error: String? = null
)

sealed interface ToolContent {
    data class Text(val text: String) : ToolContent
    data class File(val uri: String, val mime: String, val name: String?) : ToolContent
}

data class UiMessage(
    val id: String,
    val type: String,
    val created: Long,
    val text: String,
    val files: List<FileAttachment>,
    val parts: List<UiPart>,
    val agent: String?,
    val model: String?,
    val cost: Double,
    val tokens: TokenUsage?,
    val finish: String?,
    val error: String?,
    val command: String?,
    val status: String?,
    val output: String?,
    val outcome: String?
) {
    val isUser: Boolean get() = type == "user"
    val isAssistant: Boolean get() = type == "assistant"
}

fun MessageDto.toUi(): UiMessage {
    val parsed = content.mapNotNull { parsePart(it) }
    val textParts = parsed.filterIsInstance<UiPart.Text>().joinToString("\n") { it.text }
    val reasoning = parsed.filterIsInstance<UiPart.Reasoning>()
    val toolText = parsed.filterIsInstance<UiPart.Tool>()
        .joinToString("\n") { it.tool.name }
    val fallback = text?.takeIf { it.isNotBlank() }
        ?: description?.takeIf { it.isNotBlank() }
        ?: command?.takeIf { it.isNotBlank() }
        ?: summary?.takeIf { it.isNotBlank() }
        ?: outcome?.takeIf { it.isNotBlank() }
        ?: ""
    val displayText = when {
        textParts.isNotBlank() -> textParts
        fallback.isNotBlank() -> fallback
        reasoning.isNotEmpty() -> reasoning.joinToString("\n") { it.text }
        toolText.isNotBlank() -> toolText
        else -> ""
    }
    return UiMessage(
        id = id,
        type = type,
        created = time.created,
        text = displayText,
        files = files,
        parts = parsed,
        agent = agent,
        model = model?.id,
        cost = cost ?: 0.0,
        tokens = tokens,
        finish = finish,
        error = error?.message,
        command = command,
        status = status,
        output = output?.output,
        outcome = outcome
    )
}

private fun parsePart(element: JsonElement): UiPart? {
    val obj = element as? JsonObject ?: return null
    return when (obj.string("type")) {
        "text" -> UiPart.Text(obj.string("text").orEmpty())
        "reasoning" -> UiPart.Reasoning(obj.string("text").orEmpty())
        "tool" -> {
            val state = obj["state"] as? JsonObject
            val status = state?.string("status") ?: "unknown"
            val inputElement = state?.get("input")
            val input = when (inputElement) {
                is JsonObject -> inputElement.toString()
                is JsonPrimitive -> inputElement.contentOrNull.orEmpty()
                else -> ""
            }
            val content = state?.get("content")?.let { parseToolContent(it) } ?: emptyList()
            val error = (state?.get("error") as? JsonObject)?.string("message")
            UiPart.Tool(
                ToolPart(
                    id = obj.string("id").orEmpty(),
                    name = obj.string("name").orEmpty(),
                    status = status,
                    input = input,
                    content = content,
                    error = error
                )
            )
        }
        else -> null
    }
}

private fun parseToolContent(element: JsonElement): List<ToolContent> {
    val array = element as? JsonArray ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        when (obj.string("type")) {
            "text" -> ToolContent.Text(obj.string("text").orEmpty())
            "file" -> ToolContent.File(
                uri = obj.string("uri").orEmpty(),
                mime = obj.string("mime").orEmpty(),
                name = obj.string("name")
            )
            else -> null
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray
fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray
fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
