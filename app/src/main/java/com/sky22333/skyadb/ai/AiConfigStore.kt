package com.sky22333.skyadb.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class AiConfig(val baseUrl: String = "", val apiKey: String = "", val model: String = "", val responses: Boolean = false, val autoExecute: Boolean = false, val documentTree: String = "") {
    override fun toString(): String = "AiConfig(baseUrl=$baseUrl, apiKey=[redacted], model=$model, responses=$responses)"
}

/** Credentials are encrypted by Android Keystore and stored outside Android backup. */
class AiConfigStore(context: Context) {
    private val file = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "ai-config"))
    private val alias = "skyadb.ai.config.v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun read(source: android.util.AtomicFile): String? {
        if (!source.baseFile.exists()) return null
        val bytes = source.readFully()
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    private fun write(destination: android.util.AtomicFile, text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val out = destination.startWrite()
        try { out.write(encrypted); destination.finishWrite(out) } catch (error: Exception) { destination.failWrite(out); throw error }
    }
    fun load(): AiConfig = read(file)?.let { Json.decodeFromString(it) } ?: AiConfig()
    fun save(config: AiConfig) = write(file, Json.encodeToString(config))
    internal fun loadHistory(): AiHistory = read(android.util.AtomicFile(java.io.File(file.baseFile.parentFile, "ai-history")))?.let { Json.decodeFromString(it) } ?: AiHistory()
    internal fun saveHistory(history: AiHistory) = write(android.util.AtomicFile(java.io.File(file.baseFile.parentFile, "ai-history")), Json.encodeToString(history))
    internal fun loadDraft(): String = read(android.util.AtomicFile(java.io.File(file.baseFile.parentFile, "ai-draft"))).orEmpty()
    @Synchronized internal fun saveDraft(draft: String) = write(android.util.AtomicFile(java.io.File(file.baseFile.parentFile, "ai-draft")), draft)
}
