package dev.pvps.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

class OpenCodeException(val code: Int, val body: String) :
    RuntimeException("OpenCode HTTP $code: ${body.take(500)}")

class OpenCodeClient(
    baseUrl: String,
    username: String,
    password: String
) {
    private val base = baseUrl.trimEnd('/').toHttpUrl()
    private val auth = Credentials.basic(username.ifBlank { "opencode" }, password)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val sseClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun info(): ServerInfo = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(ServerInfo.serializer(), get("/api/info"))
        }
    }

    suspend fun listSessions(): List<SessionInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                SessionsResponse.serializer(),
                get("/api/session", listOf("limit" to "200", "order" to "desc"))
            ).data
        }
    }

    suspend fun createSession(directory: String?): SessionInfo = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val body = buildJsonObject {
                if (!directory.isNullOrBlank()) {
                    put("location", buildJsonObject { put("directory", directory) })
                }
            }
            json.decodeFromJsonElement(
                SessionResponse.serializer(),
                post("/api/session", body)
            ).data
        }
    }

    /**
     * OpenCode v2 pages messages in chronological order. Asking for `asc`
     * returns the OLDEST page, which hides every new user message in a long
     * session. Request the newest page and put it back in display order.
     */
    suspend fun messages(sessionId: String, limit: Int = 200): List<MessageDto> =
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                json.decodeFromJsonElement(
                    MessagesResponse.serializer(),
                    get(
                        "/api/session/$sessionId/message",
                        listOf("limit" to "$limit", "order" to "desc")
                    )
                ).data.asReversed()
            }
        }

    suspend fun prompt(
        sessionId: String,
        text: String,
        delivery: String? = null,
        files: List<PromptFileInput> = emptyList()
    ): PromptInboxUser = withContext(Dispatchers.IO) {
        withTimeout(60_000) {
            val body = buildJsonObject {
                put("text", text)
                put("resume", true)
                if (delivery != null) put("delivery", delivery)
                filesJson(files)?.let { put("files", it) }
            }
            json.decodeFromJsonElement(
                PromptResponse.serializer(),
                post("/api/session/$sessionId/prompt", body)
            ).data
        }
    }

    suspend fun commands(directory: String?): List<CommandInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val query = if (directory.isNullOrBlank()) emptyList() else {
                listOf("location[directory]" to directory)
            }
            json.decodeFromJsonElement(
                CommandsResponse.serializer(),
                get("/api/command", query)
            ).data
        }
    }

    suspend fun runCommand(
        sessionId: String,
        name: String,
        text: String,
        delivery: String? = "queue",
        files: List<PromptFileInput> = emptyList()
    ) = withContext(Dispatchers.IO) {
        withTimeout(60_000) {
            val body = buildJsonObject {
                put("name", name)
                put("text", text)
                if (delivery != null) put("delivery", delivery)
                filesJson(files)?.let { put("files", it) }
            }
            post("/api/session/$sessionId/command", body)
        }
        Unit
    }

    suspend fun compact(sessionId: String) = withContext(Dispatchers.IO) {
        withTimeout(60_000) {
            post("/api/session/$sessionId/compact", buildJsonObject { })
        }
        Unit
    }

    private fun filesJson(files: List<PromptFileInput>) =
        if (files.isEmpty()) null else buildJsonArray {
            files.forEach { file ->
                add(buildJsonObject {
                    put("uri", file.uri)
                    file.name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
                    file.description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
                })
            }
        }

    suspend fun inbox(sessionId: String): List<PromptInboxUser> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                InboxResponse.serializer(),
                get("/api/session/$sessionId/inbox")
            ).data
        }
    }

    suspend fun cancelInbox(sessionId: String, inboxId: String) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            request(
                "DELETE",
                "/api/session/$sessionId/inbox/$inboxId",
                emptyList(),
                null
            )
        }
        Unit
    }

    suspend fun updateInboxDelivery(
        sessionId: String,
        inboxId: String,
        delivery: String
    ) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            request(
                "PATCH",
                "/api/session/$sessionId/inbox/$inboxId",
                emptyList(),
                buildJsonObject { put("delivery", delivery) }
            )
        }
        Unit
    }

    suspend fun interrupt(sessionId: String) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            post("/api/session/$sessionId/interrupt", buildJsonObject { })
        }
        Unit
    }

    suspend fun forms(sessionId: String): List<FormInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                FormsResponse.serializer(),
                get("/api/session/$sessionId/form")
            ).data
        }
    }

    suspend fun form(sessionId: String, formId: String): FormDetail = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                FormResponse.serializer(),
                get("/api/session/$sessionId/form/$formId")
            ).data
        }
    }

    suspend fun replyForm(sessionId: String, formId: String, answer: JsonObject) =
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                post(
                    "/api/session/$sessionId/form/$formId/reply",
                    buildJsonObject { put("answer", answer) }
                )
            }
            Unit
        }

    suspend fun cancelForm(sessionId: String, formId: String, message: String? = null) =
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                request(
                    "DELETE",
                    "/api/session/$sessionId/form/$formId",
                    if (message.isNullOrBlank()) emptyList() else listOf("message" to message),
                    null
                )
            }
            Unit
        }

    suspend fun usageLimits(directory: String?): UsageLimits? = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val query = if (directory.isNullOrBlank()) emptyList() else {
                listOf("location[directory]" to directory)
            }
            json.decodeFromJsonElement(
                UsageLimitsResponse.serializer(),
                get("/api/experimental/usage/limits", query)
            ).data
        }
    }

    suspend fun permissions(sessionId: String): List<PermissionRequest> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                PermissionsResponse.serializer(),
                get("/api/session/$sessionId/permission")
            ).data
        }
    }

    suspend fun replyPermission(sessionId: String, requestId: String, decision: String) =
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                post(
                    "/api/session/$sessionId/permission/$requestId/reply",
                    buildJsonObject { put("decision", decision) }
                )
            }
            Unit
        }

    suspend fun models(directory: String?): List<ModelInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val query = if (directory.isNullOrBlank()) emptyList() else {
                listOf("location[directory]" to directory)
            }
            json.decodeFromJsonElement(
                ModelsResponse.serializer(),
                get("/api/model", query)
            ).data
        }
    }

    suspend fun agents(directory: String?): List<AgentInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val query = if (directory.isNullOrBlank()) emptyList() else {
                listOf("location[directory]" to directory)
            }
            json.decodeFromJsonElement(
                AgentsResponse.serializer(),
                get("/api/agent", query)
            ).data
        }
    }

    suspend fun switchModel(sessionId: String, model: ModelRef) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            post(
                "/api/session/$sessionId/model",
                buildJsonObject {
                    put("model", buildJsonObject {
                        put("id", model.id)
                        put("providerID", model.providerID)
                        model.variant?.let { put("variant", it) }
                    })
                }
            )
        }
        Unit
    }

    suspend fun switchAgent(sessionId: String, agentId: String) = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            post(
                "/api/session/$sessionId/agent",
                buildJsonObject { put("agent", agentId) }
            )
        }
        Unit
    }

    suspend fun media(hash: String): ByteArray = withContext(Dispatchers.IO) {
        withTimeout(60_000) {
            val request = Request.Builder()
                .url(url("/api/experimental/media/$hash"))
                .header("Authorization", auth)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OpenCodeException(response.code, response.body?.string().orEmpty())
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    fun events(types: List<String> = emptyList()): Flow<OpenCodeEvent> = flow {
        val query = types.map { "types" to it }
        val request = Request.Builder()
            .url(url("/api/event", query))
            .header("Authorization", auth)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        sseClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OpenCodeException(response.code, response.body?.string().orEmpty())
            }
            val source = response.body?.source() ?: return@flow
            while (currentCoroutineContext().isActive) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload.isEmpty()) continue
                val objectValue = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                    ?: continue
                val type = (objectValue["type"] as? JsonPrimitive)?.content ?: continue
                val data = objectValue["data"] as? JsonObject ?: JsonObject(emptyMap())
                val location = (objectValue["location"] as? JsonObject)
                    ?.let { (it["directory"] as? JsonPrimitive)?.content }
                val durable = objectValue["durable"] as? JsonObject
                val seq = (durable?.get("seq") as? JsonPrimitive)?.content?.toLongOrNull()
                emit(
                    OpenCodeEvent(
                        id = (objectValue["id"] as? JsonPrimitive)?.content.orEmpty(),
                        type = type,
                        data = data,
                        created = (objectValue["created"] as? JsonPrimitive)?.content?.toLongOrNull(),
                        directory = location,
                        seq = seq
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        runCatching { client.connectionPool.evictAll() }
        runCatching { client.dispatcher.executorService.shutdown() }
        // The SSE call uses its own client with an infinite read timeout.
        // Evicting its pool closes the socket so events() can unblock.
        runCatching { sseClient.connectionPool.evictAll() }
        runCatching { sseClient.dispatcher.executorService.shutdown() }
    }

    /**
     * Real per-session spend lives in the OpenCode Console request log, not in
     * the local session object (Go-covered requests report cost=0 locally).
     * The local server exposes the Console OAuth credential, so query the
     * Console with it and sum the billed requests. Returns null when the
     * credential is missing/expired, leaving the UI on its estimate.
     */
    suspend fun consoleSessionCost(sessionId: String, since: Long, until: Long): Double? =
        withContext(Dispatchers.IO) {
            withTimeout(45_000) {
                val credentials = json.decodeFromJsonElement(
                    CredentialsResponse.serializer(),
                    get("/api/credential")
                ).data
                val oauth = credentials.firstOrNull { credential ->
                    val candidate = credential.value
                    credential.active &&
                        candidate != null &&
                        candidate.type == "oauth" &&
                        !candidate.access.isNullOrBlank()
                } ?: return@withTimeout null
                val value = oauth.value ?: return@withTimeout null
                if (value.expires > 0 && value.expires < System.currentTimeMillis()) {
                    return@withTimeout null
                }
                val metadata = value.metadata
                val orgId = metadata?.orgID.orEmpty()
                if (orgId.isBlank()) return@withTimeout null
                val root = metadata?.server?.takeIf { it.isNotBlank() }
                    ?.trimEnd('/')
                    ?: "https://opencode.ai"
                val endpoint = "$root/console/api/request-logs".toHttpUrl()
                var cursor: String? = null
                var total = 0.0
                var pages = 0
                var sawItems = false
                do {
                    val url = endpoint.newBuilder()
                        .addQueryParameter("category", "inference")
                        .addQueryParameter("sessionID", sessionId)
                        .addQueryParameter("since", since.toString())
                        .addQueryParameter("until", until.toString())
                        .addQueryParameter("limit", "100")
                        .apply { cursor?.let { addQueryParameter("cursor", it) } }
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer ${value.access}")
                        .header("x-org-id", orgId)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw OpenCodeException(response.code, body)
                        val page = json.decodeFromJsonElement(
                            ConsoleRequestLogs.serializer(),
                            json.parseToJsonElement(body)
                        )
                        if (page.items.isNotEmpty()) sawItems = true
                        page.items.forEach { total += it.cost }
                        cursor = page.nextCursor
                    }
                    pages++
                } while (!cursor.isNullOrBlank() && pages < 20)
                if (sawItems) total else null
            }
        }

    private suspend fun get(path: String, query: List<Pair<String, String>> = emptyList()): JsonObject =
        request("GET", path, query, null)

    private suspend fun post(path: String, body: JsonObject): JsonObject =
        request("POST", path, emptyList(), body)

    private suspend fun request(
        method: String,
        path: String,
        query: List<Pair<String, String>>,
        body: JsonObject?
    ): JsonObject {
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url(url(path, query))
            .header("Authorization", auth)
            .header("Accept", "application/json")
        if (requestBody != null) {
            builder.method(method, requestBody)
        } else if (method == "GET") {
            builder.get()
        } else {
            builder.method(method, ByteArray(0).toRequestBody(null))
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw OpenCodeException(response.code, text)
            if (text.isBlank()) return JsonObject(emptyMap())
            return runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw OpenCodeException(response.code, "Invalid JSON: ${text.take(300)}") }
        }
    }

    private fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val builder = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
