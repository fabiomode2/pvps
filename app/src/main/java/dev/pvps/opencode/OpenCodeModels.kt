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

// Trust boundary: message/tool content comes from the OpenCode server. These
// caps keep one DTO from expanding into an unbounded heap allocation.
private const val MAX_DTO_PARTS = 200
private const val MAX_DTO_PART_TEXT_CHARS = 200_000
private const val MAX_DTO_TOOL_INPUT_CHARS = 200_000
private const val MAX_DTO_TOOL_CONTENT_ITEMS = 50
private const val MAX_DTO_TOOL_CONTENT_TEXT_CHARS = 200_000
private const val MAX_DTO_DISPLAY_TEXT_CHARS = 400_000

private fun boundedTail(value: String, maxChars: Int): String =
    if (value.length <= maxChars) value else value.takeLast(maxChars)

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

    /**
     * `time.busy` and `time.idle` are timestamps, not booleans. The last
     * transition wins: a session is running only while its last busy event is
     * newer than its last idle event. Treating a non-null `busy` as "running"
     * makes every finished session and subagent look active forever.
     */
    val isRunning: Boolean
        get() = (time.busy ?: 0L) > (time.idle ?: 0L) &&
            (outcome == null || outcome == "running" || outcome == "busy")
}

@Serializable
data class SessionsResponse(val data: List<SessionInfo> = emptyList())

@Serializable
data class ProjectInfo(
    val id: String = "",
    val canonical: String = "",
    val name: String? = null,
    val vcs: String? = null,
    val time: ProjectTime = ProjectTime(),
    val sandboxes: List<String> = emptyList()
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: canonical.trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "Global"

    val shortPath: String
        get() = canonical.replace(System.getProperty("user.home").orEmpty(), "~")
}

@Serializable
data class ProjectTime(
    val created: Long = 0,
    val updated: Long = 0,
    val initialized: Long? = null,
    val active: Long? = null
)

@Serializable
data class ProjectsResponse(val data: List<ProjectInfo> = emptyList())

@Serializable
data class SessionResponse(val data: SessionInfo = SessionInfo())

@Serializable
data class MessagesCursor(
    val previous: String? = null,
    val next: String? = null
)

@Serializable
data class MessagesResponse(
    val data: List<MessageDto> = emptyList(),
    val cursor: MessagesCursor? = null
)

@Serializable
data class PermissionsResponse(val data: List<PermissionRequest> = emptyList())

@Serializable
data class PromptPayload(
    val text: String = "",
    val files: List<FileAttachment> = emptyList(),
    val agents: List<AgentAttachment> = emptyList(),
    val skills: List<SkillAttachment> = emptyList()
)

@Serializable
data class PromptInboxUser(
    val id: String = "",
    val sessionID: String = "",
    val time: MessageTime = MessageTime(),
    val type: String = "user",
    val payload: PromptPayload = PromptPayload(),
    val delivery: String = "steer"
)

@Serializable
data class PromptResponse(val data: PromptInboxUser = PromptInboxUser())

@Serializable
data class InboxResponse(val data: List<PromptInboxUser> = emptyList())

@Serializable
data class CommandInfo(
    val name: String = "",
    val description: String? = null
)

@Serializable
data class CommandsResponse(val data: List<CommandInfo> = emptyList())

@Serializable
data class PromptFileInput(
    val uri: String = "",
    val name: String? = null,
    val description: String? = null
)

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
data class PromptFileSource(
    val type: String = "",
    val uri: String? = null
)

@Serializable
data class FileAttachment(
    val uri: String = "",
    val data: String? = null,
    val name: String? = null,
    val description: String? = null,
    val mime: String? = null,
    val source: PromptFileSource? = null,
    val mention: JsonObject? = null
) {
    /** The v2 prompt payload nests inline/URI sources; the UI wants one URI. */
    val resolvedUri: String
        get() = when {
            source?.uri?.isNotBlank() == true -> source?.uri.orEmpty()
            !data.isNullOrBlank() && !mime.isNullOrBlank() -> "data:$mime;base64,$data"
            else -> uri
        }
}

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
data class VariantSettings(
    val reasoningEffort: String? = null
)

@Serializable
data class ModelVariant(
    val id: String = "",
    val settings: VariantSettings? = null
) {
    val label: String
        get() = settings?.reasoningEffort?.takeIf { it.isNotBlank() }
            ?: id.takeIf { it.isNotBlank() && it != "default" }
            ?: "default"
}

@Serializable
data class ModelLimit(
    val context: Long = 0,
    val input: Long = 0,
    val output: Long = 0
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: ModelCacheCost = ModelCacheCost()
)

@Serializable
data class ModelCacheCost(
    val read: Double = 0.0,
    val write: Double = 0.0
)

@Serializable
data class ModelInfo(
    val id: String = "",
    val modelID: String = "",
    val providerID: String = "",
    val name: String = "",
    val variants: List<ModelVariant> = emptyList(),
    val limit: ModelLimit? = null,
    val status: String = "",
    val enabled: Boolean = true,
    val type: String = "",
    val family: String = "",
    val cost: List<ModelCost> = emptyList()
) {
    val label: String get() = if (name.isNotBlank()) name else id
    val contextLabel: String
        get() = limit?.context?.takeIf { it > 0 }?.let {
            if (it >= 1_000_000) "${it / 1_000_000}M" else "${it / 1_000}K"
        } ?: ""
    val firstCost: ModelCost? get() = cost.firstOrNull()
}

@Serializable
data class FormOption(
    val value: String = "",
    val label: String = "",
    val description: String? = null
)

@Serializable
data class FormWhen(
    val key: String = "",
    val op: String = "eq",
    val value: JsonElement = JsonPrimitive("")
)

/**
 * The v2 form API uses a discriminated union of field shapes. Keeping every
 * shape in one nullable DTO is simpler than six serializers and tolerant of
 * future fields because unknown keys are ignored.
 */
@Serializable
data class FormField(
    val key: String = "",
    val type: String = "",
    val title: String? = null,
    val description: String? = null,
    val required: Boolean = false,
    val hidden: Boolean = false,
    @SerialName("when") val conditions: List<FormWhen> = emptyList(),
    val format: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val placeholder: String? = null,
    val minimum: JsonElement? = null,
    val maximum: JsonElement? = null,
    val default: JsonElement? = null,
    val options: List<FormOption> = emptyList(),
    val custom: Boolean = false,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val url: String? = null
) {
    val label: String get() = title?.takeIf { it.isNotBlank() } ?: key
    val defaultValue: JsonElement?
        get() = default ?: when (type) {
            "boolean" -> JsonPrimitive(false)
            "multiselect" -> JsonArray(emptyList())
            else -> null
        }
}

@Serializable
data class FormInfo(
    val id: String = "",
    val sessionID: String = "",
    val title: String = "",
    val metadata: JsonObject? = null,
    val fields: List<FormField> = emptyList()
)

@Serializable
data class FormState(
    val status: String = "pending",
    val answer: JsonObject? = null,
    val message: String? = null
)

@Serializable
data class FormDetail(
    val id: String = "",
    val sessionID: String = "",
    val title: String = "",
    val metadata: JsonObject? = null,
    val fields: List<FormField> = emptyList(),
    val state: FormState = FormState()
) {
    val isPending: Boolean get() = state.status == "pending"
}

@Serializable
data class FormsResponse(val data: List<FormInfo> = emptyList())

@Serializable
data class FormResponse(val data: FormDetail = FormDetail())

@Serializable
data class UsageWorkspace(
    val id: String = "",
    val name: String? = null,
    val usageURL: String = ""
)

@Serializable
data class UsageMeter(
    val percent: Double = 0.0,
    val limited: Boolean = false,
    val resetsAt: Long = 0
)

@Serializable
data class UsageGo(
    val rolling: UsageMeter = UsageMeter(),
    val weekly: UsageMeter = UsageMeter(),
    val monthly: UsageMeter = UsageMeter()
)

@Serializable
data class UsageBudget(
    val id: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0,
    val scope: String = "",
    val per: String = "",
    val coverage: String = "",
    val resetsAt: Long = 0
)

@Serializable
data class UsageLimits(
    val workspace: UsageWorkspace = UsageWorkspace(),
    val go: UsageGo? = null,
    val budgets: List<UsageBudget> = emptyList()
)

@Serializable
data class UsageLimitsResponse(val data: UsageLimits? = null)
@Serializable
data class CredentialMetadata(
    val server: String = "",
    val orgID: String = ""
)

@Serializable
data class CredentialValue(
    val type: String = "",
    val access: String? = null,
    val expires: Long = 0,
    val metadata: CredentialMetadata? = null
)

@Serializable
data class CredentialInfo(
    val id: String = "",
    val integrationID: String = "",
    val label: String = "",
    val active: Boolean = false,
    val value: CredentialValue? = null
)

@Serializable
data class CredentialsResponse(val data: List<CredentialInfo> = emptyList())

@Serializable
data class ConsoleRequestLog(val cost: Double = 0.0)

@Serializable
data class ConsoleRequestLogs(
    val items: List<ConsoleRequestLog> = emptyList(),
    val nextCursor: String? = null
)


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
    data class Text(val text: String, val id: String = "") : UiPart
    data class Reasoning(val text: String, val id: String = "") : UiPart
    data class Tool(val tool: ToolPart) : UiPart
}

data class ToolPart(
    val id: String,
    val name: String,
    val status: String,
    val input: String,
    val content: List<ToolContent>,
    val error: String? = null,
    val metadata: JsonObject? = null
)

sealed interface ToolContent {
    data class Text(val text: String) : ToolContent
    data class File(val uri: String, val mime: String, val name: String?) : ToolContent
}

data class UiMessage(
    val id: String,
    val type: String,
    val created: Long,
    val streamed: Long? = null,
    val completed: Long? = null,
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
    val parsed = content.takeLast(MAX_DTO_PARTS).mapNotNull { parsePart(it) }
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
    }.let { boundedTail(it, MAX_DTO_DISPLAY_TEXT_CHARS) }
    return UiMessage(
        id = id,
        type = type,
        created = time.created,
        streamed = time.streamed,
        completed = time.completed,
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
        "text" -> UiPart.Text(boundedTail(obj.string("text").orEmpty(), MAX_DTO_PART_TEXT_CHARS))
        "reasoning" ->
            UiPart.Reasoning(boundedTail(obj.string("text").orEmpty(), MAX_DTO_PART_TEXT_CHARS))
        "tool" -> {
            val state = obj["state"] as? JsonObject
            val status = state?.string("status") ?: "unknown"
            val inputElement = state?.get("input")
            val input = when (inputElement) {
                is JsonObject -> inputElement.toString()
                is JsonPrimitive -> inputElement.contentOrNull.orEmpty()
                else -> ""
            }.let { boundedTail(it, MAX_DTO_TOOL_INPUT_CHARS) }
            val content = state?.get("content")?.let { parseToolContent(it) } ?: emptyList()
            val error = (state?.get("error") as? JsonObject)?.string("message")
            UiPart.Tool(
                ToolPart(
                    id = obj.string("id").orEmpty(),
                    name = obj.string("name").orEmpty(),
                    status = status,
                    input = input,
                    content = content,
                    error = error,
                    metadata = state?.get("metadata") as? JsonObject
                )
            )
        }
        else -> null
    }
}

fun parseToolContent(element: JsonElement?): List<ToolContent> {
    if (element == null) return emptyList()
    val array = element as? JsonArray ?: return emptyList()
    return array.takeLast(MAX_DTO_TOOL_CONTENT_ITEMS).mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        when (obj.string("type")) {
            "text" ->
                ToolContent.Text(
                    boundedTail(
                        obj.string("text").orEmpty(),
                        MAX_DTO_TOOL_CONTENT_TEXT_CHARS
                    )
                )
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
fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
