package dev.pvps.opencode

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun assistantMessageParsesTextReasoningAndTool() {
        val raw = """
            {
              "id": "msg_1",
              "type": "assistant",
              "time": {"created": 1, "completed": 2},
              "agent": "build",
              "model": {"id": "deepseek-v4.1-flash", "providerID": "opencode-go"},
              "content": [
                {"type": "reasoning", "text": "think"},
                {"type": "text", "text": "hello"},
                {"type": "tool", "id": "call_1", "name": "shell",
                 "state": {"status": "completed", "input": {"command": "ls -la"},
                           "content": [{"type": "text", "text": "file"}]},
                 "time": {"created": 1}}
              ]
            }
        """.trimIndent()
        val dto = json.decodeFromString(MessageDto.serializer(), raw)
        val ui = dto.toUi()
        assertEquals(3, ui.parts.size)
        assertEquals("hello", (ui.parts[1] as UiPart.Text).text)
        val tool = (ui.parts[2] as UiPart.Tool).tool
        assertEquals("shell", tool.name)
        assertEquals("completed", tool.status)
        assertTrue(tool.input.contains("ls -la"))
        assertEquals("file", (tool.content.first() as ToolContent.Text).text)
    }

    @Test
    fun userMessageWithFilesAndUnknownFieldsParses() {
        val raw = """
            {
              "id": "msg_2",
              "type": "user",
              "time": {"created": 1},
              "text": "look",
              "files": [{"uri": "data:image/png;base64,AA==", "name": "x.png"}],
              "futureField": true
            }
        """.trimIndent()
        val dto = json.decodeFromString(MessageDto.serializer(), raw)
        val ui = dto.toUi()
        assertTrue(ui.isUser)
        assertEquals("look", ui.text)
        assertEquals("x.png", ui.files.first().name)
    }

    @Test
    fun sessionRunningUsesLastTransitionNotBusyPresence() {
        val finished = SessionInfo(time = SessionTime(busy = 10, idle = 20))
        val running = SessionInfo(time = SessionTime(busy = 30, idle = 20))
        val neverIdled = SessionInfo(time = SessionTime(busy = 30, idle = null))
        assertEquals(false, finished.isRunning)
        assertEquals(true, running.isRunning)
        assertEquals(true, neverIdled.isRunning)
    }

    @Test
    fun latestMessagePageKeepsCursorAndStreamingTiming() {
        val raw = """
            {
              "data": [{
                "id": "msg_3",
                "type": "assistant",
                "time": {"created": 10, "streamed": 20, "completed": 40},
                "tokens": {"input": 100, "output": 20, "reasoning": 5,
                           "cache": {"read": 30, "write": 0}},
                "content": [{"type": "text", "text": "latest"}]
              }],
              "cursor": {"previous": "page_0", "next": null}
            }
        """.trimIndent()
        val response = json.decodeFromString(MessagesResponse.serializer(), raw)
        assertEquals("page_0", response.cursor?.previous)
        val ui = response.data.single().toUi()
        assertEquals(20L, ui.streamed)
        assertEquals(40L, ui.completed)
        assertEquals("latest", ui.text)
    }

    @Test
    fun nestedPromptAttachmentResolvesInlineUri() {
        val raw = """
            {
              "id": "msg_4",
              "type": "user",
              "time": {"created": 1},
              "text": "image",
              "files": [{
                "data": "AA==",
                "mime": "image/png",
                "source": {"type": "uri", "uri": "media:abc"},
                "name": "shot.png"
              }]
            }
        """.trimIndent()
        val ui = json.decodeFromString(MessageDto.serializer(), raw).toUi()
        assertEquals("media:abc", ui.files.single().resolvedUri)
    }

    @Test
    fun toolMetadataKeepsSubagentChildSession() {
        val raw = """
            {
              "id": "msg_5",
              "type": "assistant",
              "time": {"created": 1},
              "content": [{
                "type": "tool", "id": "call_5", "name": "subagent",
                "state": {
                  "status": "running",
                  "input": {"agent": "explore", "description": "audit"},
                  "metadata": {"sessionID": "ses_child"}
                }
              }]
            }
        """.trimIndent()
        val tool = (json.decodeFromString(MessageDto.serializer(), raw).toUi()
            .parts.single() as UiPart.Tool).tool
        assertEquals("ses_child", tool.metadata?.stringOrNull("sessionID"))
    }

    @Test
    fun promptAckParsesQueueDelivery() {
        val raw = """
            {"data": {
              "id": "msg_6", "sessionID": "ses_1",
              "time": {"created": 99}, "type": "user",
              "payload": {"text": "next", "files": [], "agents": [], "skills": []},
              "delivery": "queue"
            }}
        """.trimIndent()
        val ack = json.decodeFromString(PromptResponse.serializer(), raw).data
        assertEquals("queue", ack.delivery)
        assertEquals("next", ack.payload.text)
        assertEquals(99L, ack.time.created)
    }

    @Test
    fun permissionDecisionShape() {
        val raw = """
            {"id":"per_1","sessionID":"ses_1","action":"bash","resources":["rm -rf build"],
             "save":["bash"],"message":"Allow?"}
        """.trimIndent()
        val request = json.decodeFromString(PermissionRequest.serializer(), raw)
        assertEquals("bash", request.action)
        assertEquals("rm -rf build", request.resources.first())
    }
}
