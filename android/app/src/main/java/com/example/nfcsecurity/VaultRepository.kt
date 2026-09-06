package com.example.nfcsecurity

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class SecureItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val value: String = "",               // text content for password/id/note
    val type: String = "password",        // password | id | note | file
    val fileName: String? = null,         // original name for file items
    val mimeType: String? = null,         // e.g. image/jpeg, video/mp4, text/plain
    val fileSize: Long = 0L,              // bytes
    val cloudUrl: String? = null,         // Cloudinary secure URL (Mongo-backed)
    val cloudFileId: String? = null       // Mongo VaultFile _id
)

data class VaultFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<SecureItem> = emptyList()
)

/**
 * A vault that can be unlocked by one or more NFC card UIDs.
 * [id] is the stable internal identifier.
 * [cardUids] lists every card that can open this vault.
 */
data class CardSpace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cardUids: List<String> = emptyList(),
    val folders: List<VaultFolder> = listOf(VaultFolder(name = "General"))
) {
    val allItems: List<SecureItem> get() = folders.flatMap { it.items }
    val primaryUid: String get() = cardUids.firstOrNull() ?: ""
}

data class AppSettings(
    val biometricEnabled: Boolean = false,
    val mpinEnabled: Boolean = false,
    val autoLockSeconds: Int = 45,
    val hideEntryCount: Boolean = true
)

class VaultRepository(private val context: Context) {

    private val prefs: SharedPreferences
    private val masterKey: MasterKey
    private val filesDir: File

    init {
        masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        filesDir = File(context.filesDir, "vault_files").also { if (!it.exists()) it.mkdirs() }
        migrateIfNeeded(context)
        migrateLegacyUidModel()
    }

    private fun migrateIfNeeded(context: Context) {
        if (prefs.contains(KEY_SPACES)) return
        val old = context.getSharedPreferences("nfc_vault_prefs", Context.MODE_PRIVATE)
        val raw = old.getString("card_spaces", null) ?: return
        prefs.edit().putString(KEY_SPACES, raw).apply()
        old.edit().clear().apply()
    }

    /** Convert old single-uid CardSpace objects to the new multi-card model. */
    private fun migrateLegacyUidModel() {
        val json = prefs.getString(KEY_SPACES, null) ?: return
        try {
            val array = JSONArray(json)
            var changed = false
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (!obj.has("id") || !obj.has("cardUids")) {
                    changed = true
                    val uid = obj.optString("uid", "")
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val cardUids = JSONArray().apply {
                        if (uid.isNotBlank()) put(uid)
                    }
                    obj.put("id", id)
                    obj.put("cardUids", cardUids)
                    if (obj.has("uid")) obj.remove("uid")
                }
                newArray.put(obj)
            }
            if (changed) {
                prefs.edit().putString(KEY_SPACES, newArray.toString()).apply()
            }
        } catch (_: Exception) {
        }
    }

    // ───────────────────────────── Settings & MPIN ─────────────────────────────

    fun getSettings(): AppSettings {
        return AppSettings(
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false),
            mpinEnabled = prefs.getBoolean(KEY_MPIN_ENABLED, false) && hasMpinHash(),
            autoLockSeconds = prefs.getInt(KEY_AUTOLOCK, 45),
            hideEntryCount = prefs.getBoolean(KEY_HIDE_COUNT, true)
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC, settings.biometricEnabled)
            .putBoolean(KEY_MPIN_ENABLED, settings.mpinEnabled)
            .putInt(KEY_AUTOLOCK, settings.autoLockSeconds)
            .putBoolean(KEY_HIDE_COUNT, settings.hideEntryCount)
            .apply()
    }

    fun hasMpinHash(): Boolean = !prefs.getString(KEY_MPIN_HASH, null).isNullOrBlank()

    fun setMpin(pin: String): Boolean {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) return false
        val salt = prefs.getString(KEY_MPIN_SALT, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_MPIN_SALT, it).apply()
        }
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_MPIN_HASH, hash)
            .putBoolean(KEY_MPIN_ENABLED, true)
            .apply()
        return true
    }

    fun verifyMpin(pin: String): Boolean {
        val stored = prefs.getString(KEY_MPIN_HASH, null) ?: return false
        val salt = prefs.getString(KEY_MPIN_SALT, null) ?: return false
        return hashPin(pin, salt) == stored
    }

    fun clearMpin() {
        prefs.edit()
            .remove(KEY_MPIN_HASH)
            .remove(KEY_MPIN_SALT)
            .putBoolean(KEY_MPIN_ENABLED, false)
            .apply()
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ───────────────────────────── Spaces ─────────────────────────────

    fun getAllSpaces(): List<CardSpace> {
        val json = prefs.getString(KEY_SPACES, null) ?: return emptyList()
        return parseSpaces(json)
    }

    fun getSpaceById(id: String): CardSpace? =
        getAllSpaces().find { it.id == id }

    /** All vaults that can be unlocked by this card UID. */
    fun getSpacesForUid(uid: String): List<CardSpace> =
        getAllSpaces().filter { space ->
            space.cardUids.any { it.equals(uid, ignoreCase = true) }
        }

    fun saveSpace(space: CardSpace) {
        val spaces = getAllSpaces().toMutableList()
        val index = spaces.indexOfFirst { it.id == space.id }
        if (index >= 0) spaces[index] = space else spaces.add(space)
        writeSpaces(spaces)
    }

    fun deleteSpace(id: String) {
        val space = getSpaceById(id)
        space?.allItems?.filter { it.type == "file" }?.forEach { deleteEncryptedFile(space.id, it.id) }
        writeSpaces(getAllSpaces().filterNot { it.id == id })
    }

    /**
     * Create a brand-new vault bound to the given card UID.
     */
    fun createSpace(uid: String, name: String): CardSpace {
        val space = CardSpace(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Card ${uid.takeLast(4)}" },
            cardUids = listOf(uid.uppercase()),
            folders = listOf(VaultFolder(name = "General"))
        )
        saveSpace(space)
        return space
    }

    /**
     * Add an extra NFC card to an existing vault.
     * Returns false if the UID is already linked to this vault.
     */
    fun addCardToSpace(spaceId: String, uid: String): Boolean {
        val space = getSpaceById(spaceId) ?: return false
        val upper = uid.uppercase()
        if (space.cardUids.any { it.equals(upper, ignoreCase = true) }) return false
        saveSpace(space.copy(cardUids = space.cardUids + upper))
        return true
    }

    /**
     * Remove a card from a vault. Keeps at least one card if possible.
     */
    fun removeCardFromSpace(spaceId: String, uid: String): Boolean {
        val space = getSpaceById(spaceId) ?: return false
        if (space.cardUids.size <= 1) return false
        val remaining = space.cardUids.filterNot { it.equals(uid, ignoreCase = true) }
        if (remaining.isEmpty()) return false
        saveSpace(space.copy(cardUids = remaining))
        return true
    }

    // ───────────────────────────── Folders & Items ─────────────────────────────

    fun addFolder(spaceId: String, folderName: String): VaultFolder? {
        val space = getSpaceById(spaceId) ?: return null
        val folder = VaultFolder(name = folderName.trim().ifBlank { "Folder" })
        saveSpace(space.copy(folders = space.folders + folder))
        return folder
    }

    fun renameFolder(spaceId: String, folderId: String, newName: String) {
        val space = getSpaceById(spaceId) ?: return
        saveSpace(
            space.copy(
                folders = space.folders.map {
                    if (it.id == folderId) it.copy(name = newName.trim()) else it
                }
            )
        )
    }

    fun deleteFolder(spaceId: String, folderId: String) {
        val space = getSpaceById(spaceId) ?: return
        if (space.folders.size <= 1) return
        space.folders.find { it.id == folderId }?.items
            ?.filter { it.type == "file" }
            ?.forEach { deleteEncryptedFile(spaceId, it.id) }
        saveSpace(space.copy(folders = space.folders.filterNot { it.id == folderId }))
    }

    fun addOrUpdateItem(spaceId: String, folderId: String, item: SecureItem) {
        val space = getSpaceById(spaceId) ?: return
        val folders = space.folders.map { folder ->
            if (folder.id != folderId) return@map folder
            val items = folder.items.toMutableList()
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) items[idx] = item else items.add(item)
            folder.copy(items = items)
        }
        saveSpace(space.copy(folders = folders))
    }

    fun deleteItem(spaceId: String, folderId: String, itemId: String) {
        val space = getSpaceById(spaceId) ?: return
        val item = space.folders.find { it.id == folderId }?.items?.find { it.id == itemId }
        if (item?.type == "file") deleteEncryptedFile(spaceId, itemId)
        saveSpace(
            space.copy(
                folders = space.folders.map { folder ->
                    if (folder.id != folderId) folder
                    else folder.copy(items = folder.items.filterNot { it.id == itemId })
                }
            )
        )
    }

    // ───────────────────────────── Encrypted file storage ─────────────────────────────

    private fun fileFor(spaceId: String, itemId: String): File {
        val dir = File(filesDir, spaceId).also { if (!it.exists()) it.mkdirs() }
        return File(dir, itemId)
    }

    fun saveEncryptedFile(spaceId: String, itemId: String, uri: Uri, mimeType: String?): Boolean {
        return try {
            val dest = fileFor(spaceId, itemId)
            val encrypted = EncryptedFile.Builder(
                context,
                dest,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            context.contentResolver.openInputStream(uri)?.use { input ->
                encrypted.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openEncryptedFile(spaceId: String, itemId: String): EncryptedFile? {
        val f = fileFor(spaceId, itemId)
        if (!f.exists()) return null
        return try {
            EncryptedFile.Builder(
                context,
                f,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteEncryptedFile(spaceId: String, itemId: String) {
        try {
            fileFor(spaceId, itemId).delete()
        } catch (_: Exception) {
        }
    }

    fun getFileSize(spaceId: String, itemId: String): Long {
        return try {
            fileFor(spaceId, itemId).length()
        } catch (_: Exception) {
            0L
        }
    }

    // ───────────────────────────── Backup ─────────────────────────────

    fun exportBackup(): String {
        val root = JSONObject()
        root.put("version", 3)
        root.put("spaces", JSONArray(prefs.getString(KEY_SPACES, "[]")))
        root.put("settings", JSONObject().apply {
            val s = getSettings()
            put("biometricEnabled", s.biometricEnabled)
            put("mpinEnabled", s.mpinEnabled)
            put("autoLockSeconds", s.autoLockSeconds)
            put("hideEntryCount", s.hideEntryCount)
        })
        return root.toString()
    }

    fun importBackup(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val spaces = root.opt("spaces") ?: return false
            val spacesStr = if (spaces is String) spaces else spaces.toString()
            parseSpaces(spacesStr)
            prefs.edit().putString(KEY_SPACES, spacesStr).apply()
            root.optJSONObject("settings")?.let { st ->
                saveSettings(
                    AppSettings(
                        biometricEnabled = st.optBoolean("biometricEnabled", false),
                        mpinEnabled = st.optBoolean("mpinEnabled", false),
                        autoLockSeconds = st.optInt("autoLockSeconds", 45),
                        hideEntryCount = st.optBoolean("hideEntryCount", true)
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ───────────────────────────── Serialization ─────────────────────────────

    private fun writeSpaces(spaces: List<CardSpace>) {
        val array = JSONArray()
        spaces.forEach { space ->
            val obj = JSONObject()
            obj.put("id", space.id)
            obj.put("name", space.name)
            val uidsArr = JSONArray()
            space.cardUids.forEach { uidsArr.put(it) }
            obj.put("cardUids", uidsArr)
            val foldersArr = JSONArray()
            space.folders.forEach { folder ->
                val fObj = JSONObject()
                fObj.put("id", folder.id)
                fObj.put("name", folder.name)
                val itemsArr = JSONArray()
                folder.items.forEach { item ->
                    itemsArr.put(
                        JSONObject()
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("value", item.value)
                            .put("type", item.type)
                            .put("fileName", item.fileName ?: JSONObject.NULL)
                            .put("mimeType", item.mimeType ?: JSONObject.NULL)
                            .put("fileSize", item.fileSize)
                            .put("cloudUrl", item.cloudUrl ?: JSONObject.NULL)
                            .put("cloudFileId", item.cloudFileId ?: JSONObject.NULL)
                    )
                }
                fObj.put("items", itemsArr)
                foldersArr.put(fObj)
            }
            obj.put("folders", foldersArr)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SPACES, array.toString()).apply()
    }

    private fun parseSpaces(json: String): List<CardSpace> {
        val result = mutableListOf<CardSpace>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Card")

                val cardUids = mutableListOf<String>()
                val uidsArr = obj.optJSONArray("cardUids")
                if (uidsArr != null) {
                    for (u in 0 until uidsArr.length()) {
                        cardUids.add(uidsArr.getString(u))
                    }
                } else {
                    val legacyUid = obj.optString("uid", "")
                    if (legacyUid.isNotBlank()) cardUids.add(legacyUid)
                }

                val foldersArr = obj.optJSONArray("folders")
                val folders = mutableListOf<VaultFolder>()
                if (foldersArr != null && foldersArr.length() > 0) {
                    for (j in 0 until foldersArr.length()) {
                        val f = foldersArr.getJSONObject(j)
                        folders.add(
                            VaultFolder(
                                id = f.optString("id", UUID.randomUUID().toString()),
                                name = f.optString("name", "General"),
                                items = parseItems(f.optJSONArray("items"))
                            )
                        )
                    }
                } else {
                    val items = parseItems(obj.optJSONArray("items"))
                    folders.add(VaultFolder(name = "General", items = items))
                }

                result.add(
                    CardSpace(
                        id = id,
                        name = name,
                        cardUids = cardUids,
                        folders = folders
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun parseItems(arr: JSONArray?): List<SecureItem> {
        if (arr == null) return emptyList()
        val items = mutableListOf<SecureItem>()
        for (k in 0 until arr.length()) {
            val it = arr.getJSONObject(k)
            items.add(
                SecureItem(
                    id = it.optString("id", UUID.randomUUID().toString()),
                    title = it.optString("title", ""),
                    value = it.optString("value", ""),
                    type = it.optString("type", "password"),
                    fileName = it.optString("fileName").takeIf { s -> s.isNotBlank() && s != "null" },
                    mimeType = it.optString("mimeType").takeIf { s -> s.isNotBlank() && s != "null" },
                    fileSize = it.optLong("fileSize", 0L),
                    cloudUrl = it.optString("cloudUrl").takeIf { s -> s.isNotBlank() && s != "null" },
                    cloudFileId = it.optString("cloudFileId").takeIf { s -> s.isNotBlank() && s != "null" }
                )
            )
        }
        return items
    }

    companion object {
        private const val PREFS_NAME = "nfc_vault_encrypted"
        private const val KEY_SPACES = "card_spaces"
        private const val KEY_BIOMETRIC = "setting_biometric"
        private const val KEY_MPIN_ENABLED = "setting_mpin_enabled"
        private const val KEY_MPIN_HASH = "mpin_hash"
        private const val KEY_MPIN_SALT = "mpin_salt"
        private const val KEY_AUTOLOCK = "setting_autolock"
        private const val KEY_HIDE_COUNT = "setting_hide_count"
    }
}
