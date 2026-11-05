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
                get("/api/session", listOf("limit" to "100", "order" to "desc"))
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

    suspend fun messages(sessionId: String): List<MessageDto> = withContext(Dispatchers.IO) {
        withTimeout(30_000) {
            json.decodeFromJsonElement(
                MessagesResponse.serializer(),
                get(
                    "/api/session/$sessionId/message",
                    listOf("limit" to "200", "order" to "asc")
                )
            ).data
        }
    }

    suspend fun prompt(sessionId: String, text: String) = withContext(Dispatchers.IO) {
        withTimeout(60_000) {
            post(
                "/api/session/$sessionId/prompt",
                buildJsonObject { put("text", text) }
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
