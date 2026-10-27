package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AiProvider
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AndroidKeyStore key", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key alias not found in AndroidKeyStore")
        return entry.secretKey
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val byteBuffer = ByteBuffer.allocate(4 + iv.size + cipherBytes.size)
            byteBuffer.putInt(iv.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherBytes)

            Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            ""
        }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val data = Base64.decode(cipherText, Base64.NO_WRAP)
            val byteBuffer = ByteBuffer.wrap(data)
            val ivSize = byteBuffer.int
            val iv = ByteArray(ivSize)
            byteBuffer.get(iv)
            val cipherBytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(cipherBytes)

            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            ""
        }
    }

    fun saveEncryptedApiKey(provider: AiProvider, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(getPrefKey(provider)).apply()
        } else {
            val encrypted = encrypt(trimmed)
            prefs.edit().putString(getPrefKey(provider), encrypted).apply()
        }
    }

    fun getApiKey(provider: AiProvider): String {
        val encrypted = prefs.getString(getPrefKey(provider), null)
        if (!encrypted.isNullOrEmpty()) {
            val decrypted = decrypt(encrypted)
            if (decrypted.isNotEmpty()) return decrypted
        }

        // Optional fallback for Gemini from BuildConfig / Secrets
        if (provider == AiProvider.GEMINI) {
            try {
                val buildConfigKey = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
                if (!buildConfigKey.isNullOrBlank() && !buildConfigKey.startsWith("MY_GEMINI")) {
                    return buildConfigKey.trim()
                }
            } catch (_: Exception) {}
        }

        return ""
    }

    fun hasApiKey(provider: AiProvider): Boolean {
        return getApiKey(provider).isNotEmpty()
    }

    fun getMaskedApiKey(provider: AiProvider): String {
        val key = getApiKey(provider)
        return maskKey(key)
    }

    fun maskKey(key: String): String {
        if (key.length <= 8) return if (key.isEmpty()) "Not configured" else "••••••••"
        val prefix = key.take(4)
        val suffix = key.takeLast(4)
        return "$prefix••••••••$suffix"
    }

    private fun getPrefKey(provider: AiProvider): String = "api_key_${provider.name.lowercase()}"

    companion object {
        private const val TAG = "KeystoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_collab_workspace_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFS_NAME = "ai_collab_secure_store"
    }
}
