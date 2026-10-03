package com.shieldtap.vault.data

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-only NFC card binding (UID). Never sent to server.
 * Used as alternative to MPIN for unlock / sensitive actions / quick login.
 */
class NfcStore(private val context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "shieldtap_nfc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isNfcAvailable(): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    fun hasHardware(): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun isCardRegistered(): Boolean = !prefs.getString(KEY_UID, null).isNullOrBlank()

    fun getRegisteredUid(): String? = prefs.getString(KEY_UID, null)

    fun registerCard(uid: String) {
        prefs.edit().putString(KEY_UID, uid).apply()
    }

    fun clearCard() {
        prefs.edit().remove(KEY_UID).apply()
    }

    fun matches(uid: String): Boolean {
        val stored = getRegisteredUid() ?: return false
        return stored.equals(uid, ignoreCase = true)
    }

    companion object {
        private const val KEY_UID = "nfc_uid"

        fun tagIdHex(tag: Tag): String {
            return tag.id.joinToString("") { b -> "%02X".format(b) }
        }
    }
}
