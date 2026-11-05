package dev.pvps.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SetupCodeTest {
    @Test
    fun parsesInstallerSetupCode() {
        val json = """{"v":1,"name":"vps","host":"100.64.0.10","port":22,"user":"ubuntu","ocPort":49374,"ocUser":"opencode","ocPassword":"secret","mode":"tunnel","directory":"/home/ubuntu"}"""
        val code = "pvps-setup:" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        val payload = SetupCode.parse(code).getOrThrow()
        assertEquals("100.64.0.10", payload.host)
        assertEquals("ubuntu", payload.user)
        assertEquals(49374, payload.ocPort)
        val host = SetupCode.toHost(payload)
        assertEquals("vps", host.name)
        assertEquals(OpenCodeMode.SSH_TUNNEL, host.openCodeMode)
        assertEquals("secret", host.openCodePassword)
        assertTrue(host.sshHost.isNotBlank())
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(SetupCode.parse("not-a-code").isFailure)
    }
}
