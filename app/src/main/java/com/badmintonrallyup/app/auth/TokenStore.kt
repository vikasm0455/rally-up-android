// Keystore-backed storage for the bearer access JWT + rotating refresh token —
// the Android counterpart of the iOS Keychain TokenStore. The AES key lives in
// AndroidKeyStore (non-exportable); ciphertext sits in private SharedPreferences.

package com.badmintonrallyup.app.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.badmintonrallyup.app.RallyUpApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

object TokenStore {
    private const val KEY_ALIAS = "com.badmintonrallyup.app.tokens"
    private const val PREFS = "rallyup.tokens"
    private const val ENTRY = "session"

    private val prefs
        get() = RallyUpApp.instance.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun load(): TokenPair? {
        val stored = prefs.getString(ENTRY, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, 12)
            val ciphertext = blob.copyOfRange(12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            Json.decodeFromString<TokenPair>(String(cipher.doFinal(ciphertext)))
        } catch (e: Exception) {
            null   // key rotated / corrupted blob → treated as signed out
        }
    }

    fun save(pair: TokenPair) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(Json.encodeToString(TokenPair.serializer(), pair).toByteArray())
            val blob = cipher.iv + ciphertext
            prefs.edit().putString(ENTRY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        } catch (e: Exception) {
            // Keystore hiccup — leave previous state; caller keeps in-memory tokens.
        }
    }

    fun clear() {
        prefs.edit().remove(ENTRY).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
