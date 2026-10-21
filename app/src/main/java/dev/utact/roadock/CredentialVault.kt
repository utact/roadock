package dev.utact.roadock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)

    fun contains(id: String): Boolean = preferences.contains(id)

    fun write(id: String, token: String) {
        if (token.isBlank()) {
            preferences.edit().remove(id).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoded = listOf(cipher.iv, encrypted).joinToString(":") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
        preferences.edit().putString(id, encoded).apply()
    }

    fun read(id: String): String {
        val encoded = preferences.getString(id, null) ?: return ""
        try {
            val parts = encoded.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("토큰을 읽을 수 없습니다. 프로젝트 설정에서 다시 저장해 주세요")
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("roadock-github", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("roadock-github", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }
}
