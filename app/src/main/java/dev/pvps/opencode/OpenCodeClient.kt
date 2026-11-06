package dev.pvps.opencode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import dev.pvps.diagnostics.Diagnostics
import dev.pvps.security.SecuritySupport
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
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.EOFException
import java.io.IOException
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
    // Console requests carry an OAuth bearer token to a server-named origin.
    // Never follow redirects away from the validated OpenCode Console origin.
    private val consoleClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
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

    suspend fun projects(): List<ProjectInfo> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            val request = Request.Builder()
                .url(url("/api/project"))
                .header("Authorization", auth)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OpenCodeException(response.code, response.body?.readLimitedErrorText().orEmpty())
                }
                val text = response.body?.readLimitedText(MAX_JSON_BODY_BYTES).orEmpty()
                val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
                when (element) {
                    is kotlinx.serialization.json.JsonArray ->
                        json.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(ProjectInfo.serializer()),
                            element
                        )
                    is JsonObject -> json.decodeFromJsonElement(
                        ProjectsResponse.serializer(),
                        element
                    ).data
                    else -> emptyList()
                }
            }
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

    /**
     * Active context is the message set after the last compaction. This is the
     * authoritative view for the context-window meter; using the full message
     * list overcounts after /compact and makes the percentage drift.
     */
    suspend fun context(sessionId: String): List<MessageDto> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                MessagesResponse.serializer(),
                get("/api/session/$sessionId/context")
            ).data
        }
    }

    /** Sessions with a foreground drain owned by this server process. */
    suspend fun activeSessions(): Set<String> = withContext(Dispatchers.IO) {
        withTimeout(20_000) {
            val element = get("/api/session/active")
            val data = (element as? JsonObject)?.get("data") as? JsonObject ?: return@withTimeout emptySet()
            data.keys
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
                    throw OpenCodeException(response.code, response.body?.readLimitedErrorText().orEmpty())
                }
                response.body?.readLimitedBytes(MAX_MEDIA_BODY_BYTES) ?: ByteArray(0)
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
                throw OpenCodeException(response.code, response.body?.readLimitedErrorText().orEmpty())
            }
            val source = response.body?.source() ?: return@flow
            var eventCount = 0
            while (currentCoroutineContext().isActive) {
                // Trust boundary: SSE lines are server-controlled. Strict reads
                // bound one line and end the connection on EOF or oversize input.
                val line = try {
                    source.readUtf8LineStrict(MAX_SSE_LINE_BYTES)
                } catch (_: EOFException) {
                    break
                } catch (_: IOException) {
                    break
                }
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
                if (eventCount >= MAX_SSE_EVENTS_PER_CONNECTION) break
                eventCount++
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
                // Trust boundary: metadata.server comes from the connected server.
                // Only the public HTTPS Console origin may receive the bearer token.
                val origin = consoleOrigin(metadata?.server) ?: return@withTimeout null
                var cursor: String? = null
                var total = 0.0
                var pages = 0
                var sawItems = false
                do {
                    val url = origin.newBuilder()
                        .addPathSegment("console")
                        .addPathSegment("api")
                        .addPathSegment("request-logs")
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
                    consoleClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw OpenCodeException(
                                response.code,
                                response.body?.readLimitedErrorText(MAX_CONSOLE_BODY_BYTES).orEmpty()
                            )
                        }
                        val body = response.body?.readLimitedText(MAX_CONSOLE_BODY_BYTES).orEmpty()
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
        val started = System.nanoTime()
        try {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OpenCodeException(response.code, response.body?.readLimitedErrorText().orEmpty())
                }
                val text = response.body?.readLimitedText(MAX_JSON_BODY_BYTES).orEmpty()
                if (text.isBlank()) return JsonObject(emptyMap())
                return runCatching { json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw OpenCodeException(response.code, "Invalid JSON: ${text.take(300)}") }
            }
        } finally {
            val ms = (System.nanoTime() - started) / 1_000_000
            Diagnostics.event("http", "$method $path ${ms}ms")
        }
    }

    /**
     * Trust boundary: [rawServer] comes from the connected OpenCode server.
     * Accept only the OpenCode Console origin over HTTPS, with no credentials,
     * query, fragment, path prefix, or non-standard port. Anything else returns
     * null so the caller falls back to the local cost estimate.
     */
    private fun consoleOrigin(rawServer: String?): HttpUrl? {
        val raw = rawServer?.trim().orEmpty().ifBlank { DEFAULT_CONSOLE_ORIGIN }
        val url = runCatching { raw.toHttpUrl() }.getOrNull() ?: return null
        if (!url.isHttps) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        if (url.port != 443) return null
        if (url.encodedPath != "/") return null
        // The Console origin is fixed: a connected OpenCode server may point at
        // the official Console, but not at an arbitrary *.opencode.ai host.
        if (url.host.lowercase() != CONSOLE_HOST) return null
        return url
    }

    /** Read at most [maxBytes] of a server-controlled body; never buffer unbounded. */
    private fun ResponseBody.readLimitedText(maxBytes: Int): String =
        SecuritySupport.readLimitedBytes(byteStream(), maxBytes).toString(Charsets.UTF_8)

    private fun ResponseBody.readLimitedBytes(maxBytes: Int): ByteArray =
        SecuritySupport.readLimitedBytes(byteStream(), maxBytes)

    /** Error bodies are also bounded; truncate to a short diagnostic instead of failing. */
    private fun ResponseBody.readLimitedErrorText(maxBytes: Int = MAX_JSON_BODY_BYTES): String =
        runCatching { readLimitedText(maxBytes) }.getOrElse {
            "Response body exceeds ${maxBytes / (1024 * 1024)} MiB"
        }

    private fun url(path: String, query: List<Pair<String, String>> = emptyList()): HttpUrl {
        val builder = base.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val DEFAULT_CONSOLE_ORIGIN = "https://opencode.ai"
        private const val CONSOLE_HOST = "opencode.ai"

        // Server-controlled response bodies are bounded before allocation.
        private const val MAX_JSON_BODY_BYTES = 8 * 1024 * 1024
        private const val MAX_CONSOLE_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_MEDIA_BODY_BYTES = 64 * 1024 * 1024

        // SSE lines and event count per connection are bounded as well.
        private const val MAX_SSE_LINE_BYTES = 1L * 1024 * 1024
        private const val MAX_SSE_EVENTS_PER_CONNECTION = 10_000
    }
}
