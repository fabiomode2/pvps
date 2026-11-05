package dev.pvps.ssh

import org.junit.Assert.assertTrue
import org.junit.Test

class KeyToolsTest {

    @Test
    fun ed25519_generate_export_and_validate() {
        val key = KeyTools.generateEd25519("phone", "correct horse battery staple")
        assertTrue(key.privateKey.contains("BEGIN OPENSSH PRIVATE KEY"))
        assertTrue(key.publicKey.startsWith("ssh-ed25519 "))
        assertTrue(KeyTools.validate(key.privateKey, "correct horse battery staple").isSuccess)
        assertTrue(KeyTools.validate(key.privateKey, "wrong passphrase").isFailure)
    }

    @Test
    fun rsa_generate_export_and_validate() {
        val key = KeyTools.generateRsa("phone-rsa", "passphrase", 2048)
        assertTrue(key.privateKey.contains("BEGIN RSA PRIVATE KEY"))
        assertTrue(key.publicKey.startsWith("ssh-rsa "))
        assertTrue(KeyTools.validate(key.privateKey, "passphrase").isSuccess)
    }
}
