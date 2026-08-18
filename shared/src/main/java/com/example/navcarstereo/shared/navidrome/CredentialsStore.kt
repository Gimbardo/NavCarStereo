package com.example.navcarstereo.shared.navidrome

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(config: NavidromeConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, encrypt(config.password))
            .apply()
    }

    fun load(): NavidromeConfig? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val encryptedPassword = prefs.getString(KEY_PASSWORD, null) ?: return null
        return NavidromeConfig(serverUrl, username, decrypt(encryptedPassword))
    }

    fun clear() = prefs.edit().clear().apply()

    // Elenco di server salvati, distinto dal singolo slot "attivo" sopra (letto da PlaybackService).
    fun listServers(): List<NavidromeConfig> {
        if (prefs.getString(KEY_SERVERS, null) == null) {
            // Migrazione una tantum: chi aveva già salvato un server prima che esistesse la lista
            // lo ritrova qui invece che vederla vuota. KEY_SERVERS assente = mai inizializzata;
            // dopo la prima scrittura vale "[]" anche a lista svuotata, quindi non si ripete.
            writeServers(load()?.let { listOf(it) } ?: emptyList())
        }
        val array = JSONArray(prefs.getString(KEY_SERVERS, "[]"))
        return (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            NavidromeConfig(
                serverUrl = entry.getString("serverUrl"),
                username = entry.getString("username"),
                password = decrypt(entry.getString("password")),
            )
        }
    }

    fun saveServer(config: NavidromeConfig) {
        val servers = listServers().filterNot { it.serverUrl == config.serverUrl && it.username == config.username }
        writeServers(servers + config)
    }

    fun removeServer(config: NavidromeConfig) {
        val servers = listServers().filterNot { it.serverUrl == config.serverUrl && it.username == config.username }
        writeServers(servers)
    }

    private fun writeServers(servers: List<NavidromeConfig>) {
        val array = JSONArray()
        servers.forEach { config ->
            array.put(
                JSONObject()
                    .put("serverUrl", config.serverUrl)
                    .put("username", config.username)
                    .put("password", encrypt(config.password)),
            )
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$iv:$body"
    }

    private fun decrypt(stored: String): String {
        val (iv, body) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "navidrome_credentials"
        const val KEY_ALIAS = "navidrome_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_SERVERS = "saved_servers"
    }
}
