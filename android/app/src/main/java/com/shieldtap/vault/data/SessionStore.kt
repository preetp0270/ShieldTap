package com.shieldtap.vault.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "shieldtap_prefs")

class SessionStore(private val context: Context) {

    private val TOKEN = stringPreferencesKey("token")
    private val EXPIRES = stringPreferencesKey("expires_at")
    private val USER_JSON = stringPreferencesKey("user_json")
    private val THEME = stringPreferencesKey("theme_mode") // system | light | dark
    private val MPIN_SET = booleanPreferencesKey("mpin_set")

    // Encrypted storage for MPIN (device-only)
    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "shieldtap_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[TOKEN] }
    val themeFlow: Flow<String> = context.dataStore.data.map { it[THEME] ?: "system" }
    val mpinSetFlow: Flow<Boolean> = context.dataStore.data.map { it[MPIN_SET] ?: false }

    suspend fun saveSession(token: String, expiresAt: String, userJson: String) {
        context.dataStore.edit {
            it[TOKEN] = token
            it[EXPIRES] = expiresAt
            it[USER_JSON] = userJson
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(TOKEN)
            it.remove(EXPIRES)
            it.remove(USER_JSON)
        }
        // Keep MPIN – it is device-bound
    }

    suspend fun getToken(): String? = context.dataStore.data.first()[TOKEN]
    suspend fun getExpiresAt(): String? = context.dataStore.data.first()[EXPIRES]
    suspend fun getUserJson(): String? = context.dataStore.data.first()[USER_JSON]

    suspend fun setTheme(mode: String) {
        context.dataStore.edit { it[THEME] = mode }
    }

    // ---- MPIN (never leaves the device) ----
    fun saveMpin(mpin: String) {
        encryptedPrefs.edit().putString("mpin", mpin).apply()
        // also mark flag in normal datastore
    }

    suspend fun markMpinSet(set: Boolean) {
        context.dataStore.edit { it[MPIN_SET] = set }
    }

    fun getMpin(): String? = encryptedPrefs.getString("mpin", null)

    fun verifyMpin(input: String): Boolean {
        val stored = getMpin() ?: return false
        return stored == input
    }

    // Failed attempt lock (client-side)
    private val failCountKey = "fail_count"
    private val lockUntilKey = "lock_until"

    fun recordFailedAttempt(): Boolean {
        // returns true if now locked
        val count = encryptedPrefs.getInt(failCountKey, 0) + 1
        encryptedPrefs.edit().putInt(failCountKey, count).apply()
        if (count >= 3) {
            val until = System.currentTimeMillis() + 5 * 60 * 1000 // 5 min
            encryptedPrefs.edit().putLong(lockUntilKey, until).putInt(failCountKey, 0).apply()
            return true
        }
        return false
    }

    fun resetFailedAttempts() {
        encryptedPrefs.edit().putInt(failCountKey, 0).putLong(lockUntilKey, 0).apply()
    }

    fun isLocked(): Boolean {
        val until = encryptedPrefs.getLong(lockUntilKey, 0)
        return until > System.currentTimeMillis()
    }

    fun lockRemainingMs(): Long {
        val until = encryptedPrefs.getLong(lockUntilKey, 0)
        return (until - System.currentTimeMillis()).coerceAtLeast(0)
    }
}
