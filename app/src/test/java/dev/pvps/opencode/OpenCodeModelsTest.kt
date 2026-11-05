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
