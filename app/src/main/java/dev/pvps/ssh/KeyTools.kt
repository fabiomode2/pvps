package dev.pvps.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import dev.pvps.data.model.SshKey
import java.io.ByteArrayOutputStream

object KeyTools {

    fun generateEd25519(name: String, passphrase: String): SshKey {
        val jsch = JSch()
        val pair = KeyPair.genKeyPair(jsch, KeyPair.ED25519)
        return export(name, passphrase, pair, "ed25519")
    }

    fun generateRsa(name: String, passphrase: String, bits: Int = 4096): SshKey {
        val jsch = JSch()
        val pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, bits)
        return export(name, passphrase, pair, "rsa-$bits")
    }

    fun validate(privateKey: String, passphrase: String): Result<Unit> = runCatching {
        val jsch = JSch()
        val pass = passphrase.takeIf { it.isNotBlank() }?.toByteArray()
        jsch.addIdentity("validate", privateKey.toByteArray(Charsets.UTF_8), null, pass)
    }

    private fun export(name: String, passphrase: String, pair: KeyPair, algorithm: String): SshKey {
        val privateOut = ByteArrayOutputStream()
        val publicOut = ByteArrayOutputStream()
        val pass = passphrase.takeIf { it.isNotBlank() }?.toByteArray()
        if (pair.keyType == KeyPair.ED25519 || pair.keyType == KeyPair.ED448) {
            pair.writeOpenSSHv1PrivateKey(privateOut, pass, "aes256-ctr", 16)
        } else {
            pair.writePrivateKey(privateOut, pass)
        }
        pair.writePublicKey(publicOut, "pvps:${name.ifBlank { "key" }}")
        return SshKey(
            name = name.ifBlank { "key" },
            privateKey = privateOut.toString(Charsets.UTF_8.name()),
            publicKey = publicOut.toString(Charsets.UTF_8.name()),
            passphrase = passphrase,
            algorithm = algorithm
        )
    }
}
