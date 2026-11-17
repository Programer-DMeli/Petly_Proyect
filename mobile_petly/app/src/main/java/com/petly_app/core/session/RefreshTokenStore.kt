package com.petly_app.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RefreshCredential(val token: String, val requestId: String? = null)

interface RefreshTokenStore {
    fun read(): RefreshCredential?
    fun write(credential: RefreshCredential?)
}

internal class KeystoreRefreshTokenStore(context: Context, name: String = "petly_session") : RefreshTokenStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val alias = "$name.refresh.v1"

    override fun read(): RefreshCredential? {
        val encrypted = preferences.getString("refresh", null) ?: return null
        val (iv, body) = encrypted.split('.', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
        val payload = JSONObject(cipher.doFinal(decode(body)).decodeToString())
        return RefreshCredential(payload.getString("token"), payload.optString("requestId").takeIf { it.isNotEmpty() })
    }

    override fun write(credential: RefreshCredential?) {
        val edit = preferences.edit()
        if (credential == null) {
            edit.remove("refresh")
        } else {
            val payload = JSONObject().put("token", credential.token).put("requestId", credential.requestId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val body = cipher.doFinal(payload.toString().encodeToByteArray())
            edit.putString("refresh", "${encode(cipher.iv)}.${encode(body)}")
        }
        if (!edit.commit()) throw IOException("No se pudo guardar la sesión de forma segura.")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}
