package dev.pvps.data.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.pvps.data.model.SecureData
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the vault (hosts + SSH keys + OpenCode passwords) with a
 * hardware-backed Android Keystore AES-GCM key. The encrypted blob is stored
 * in the app's private files directory and is excluded from backups.
 */
class SecureStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val file = File(context.filesDir, "vault.bin")

    @Synchronized
    fun load(): SecureData {
        if (!file.exists()) return SecureData()
        return runCatching {
            val bytes = file.readBytes()
            require(bytes.size >= 13) { "vault blob is truncated" }
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(ciphertext)
            json.decodeFromString<SecureData>(String(plain, Charsets.UTF_8))
        }.getOrElse {
            // A lost Keystore key (factory reset, restore) makes the old blob
            // unreadable. Never crash the app because of that, but keep the blob
            // so it is not silently overwritten and destroyed by the next save.
            runCatching {
                file.renameTo(File(context.filesDir, "vault.bin.corrupt-${System.currentTimeMillis()}"))
            }
            SecureData()
        }
    }

    @Synchronized
    fun save(data: SecureData) {
        val plain = json.encodeToString(SecureData.serializer(), data).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain)
        val out = iv + ciphertext
        val tmp = File(context.filesDir, "vault.bin.tmp")
        FileOutputStream(tmp).use { stream ->
            stream.write(out)
            stream.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.writeBytes(out)
            tmp.delete()
        }
    }

    fun exportPublicMetadata(): String {
        val data = load()
        return buildString {
            appendLine("hosts=${data.hosts.size} keys=${data.keys.size}")
            data.hosts.forEach { appendLine("host ${it.name} ${it.sshUser}@${it.sshHost}:${it.sshPort}") }
            data.keys.forEach { appendLine("key ${it.name} ${it.algorithm}") }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "pvps.vault.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
