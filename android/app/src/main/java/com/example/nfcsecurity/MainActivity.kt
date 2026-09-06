package com.example.nfcsecurity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.nfcsecurity.ui.theme.NFCSecurityTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

private enum class NfcAction { OpenOrLearn, ConfirmDelete }

private enum class LearnMode { None, CreateNew, AddToExisting }

sealed class Screen {
    data object AppLock : Screen()
    data object Home : Screen()
    data class Vault(val spaceId: String) : Screen()
    data class EditItem(val spaceId: String, val folderId: String, val item: SecureItem?) : Screen()
    data object Settings : Screen()
}

class MainActivity : FragmentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var repo: VaultRepository
    private lateinit var sessionStore: SessionStore
    private lateinit var apiClient: ApiClient

    private var currentScreen by mutableStateOf<Screen>(Screen.Home)
    private var appUnlocked by mutableStateOf(false)
    private var learnMode by mutableStateOf(LearnMode.None)
    private var pendingLearnName by mutableStateOf("")
    private var pendingAddToSpaceId by mutableStateOf<String?>(null)
    private var showNameDialog by mutableStateOf(false)
    private var showLearnChoiceDialog by mutableStateOf(false)
    private var nfcAction by mutableStateOf(NfcAction.OpenOrLearn)
    private var pendingDeleteSpaceId by mutableStateOf<String?>(null)
    private var statusMessage by mutableStateOf("")
    private var spacesVersion by mutableStateOf(0)
    private var lastInteraction by mutableLongStateOf(System.currentTimeMillis())
    private var triggerImport by mutableStateOf(false)

    private var vaultChooserSpaces by mutableStateOf<List<CardSpace>>(emptyList())
    private var showVaultChooser by mutableStateOf(false)

    private var showMpinDialog by mutableStateOf(false)
    private var pendingOpenSpaceId by mutableStateOf<String?>(null)
    private var mpinError by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = VaultRepository(this)
        sessionStore = SessionStore(this)
        apiClient = ApiClient(this, sessionStore)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) statusMessage = getString(R.string.no_nfc)

        // App-lock style MPIN: required on cold start before any vault UI
        val bootSettings = repo.getSettings()
        if (bootSettings.mpinEnabled && repo.hasMpinHash()) {
            currentScreen = Screen.AppLock
            appUnlocked = false
        } else {
            appUnlocked = true
            currentScreen = Screen.Home
        }

        setContent {
            NFCSecurityTheme(darkTheme = true) {
                val version = spacesVersion
                val spaces = remember(version) { repo.getAllSpaces() }
                val settings = remember(version) { repo.getSettings() }

                LaunchedEffect(currentScreen, settings.autoLockSeconds, lastInteraction) {
                    if (currentScreen is Screen.Vault || currentScreen is Screen.EditItem) {
                        val timeout = settings.autoLockSeconds.coerceIn(15, 300) * 1000L
                        while (true) {
                            delay(1000)
                            if (System.currentTimeMillis() - lastInteraction > timeout) {
                                currentScreen = Screen.Home
                                statusMessage = "Auto-locked after idle"
                                break
                            }
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val text = stream.readBytes().toString(Charset.forName("UTF-8"))
                            if (repo.importBackup(text)) {
                                spacesVersion++
                                Toast.makeText(this@MainActivity, "Backup imported", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Invalid backup file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(triggerImport) {
                    if (triggerImport) {
                        triggerImport = false
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }

                if (showLearnChoiceDialog) {
                    LearnChoiceDialog(
                        spaces = spaces,
                        onCreateNew = {
                            showLearnChoiceDialog = false
                            pendingLearnName = ""
                            showNameDialog = true
                            learnMode = LearnMode.CreateNew
                        },
                        onAddToExisting = { spaceId ->
                            showLearnChoiceDialog = false
                            pendingAddToSpaceId = spaceId
                            learnMode = LearnMode.AddToExisting
                            statusMessage = "Tap the new card to link it"
                            Toast.makeText(this@MainActivity, "Now tap the extra card", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showLearnChoiceDialog = false }
                    )
                }

                if (showNameDialog) {
                    NameSpaceDialog(
                        initial = pendingLearnName,
                        onConfirm = { name ->
                            showNameDialog = false
                            pendingLearnName = name
                            learnMode = LearnMode.CreateNew
                            statusMessage = getString(R.string.learning_mode)
                            Toast.makeText(this@MainActivity, "Now tap the bank card", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = {
                            showNameDialog = false
                            learnMode = LearnMode.None
                        }
                    )
                }

                if (showVaultChooser && vaultChooserSpaces.isNotEmpty()) {
                    VaultChooserDialog(
                        spaces = vaultChooserSpaces,
                        onSelect = { spaceId ->
                            showVaultChooser = false
                            vaultChooserSpaces = emptyList()
                            beginAuthAndOpen(spaceId)
                        },
                        onDismiss = {
                            showVaultChooser = false
                            vaultChooserSpaces = emptyList()
                            statusMessage = "Cancelled"
                        }
                    )
                }

                if (showMpinDialog && pendingOpenSpaceId != null) {
                    MpinDialog(
                        isError = mpinError,
                        onSubmit = { pin ->
                            if (repo.verifyMpin(pin)) {
                                showMpinDialog = false
                                mpinError = false
                                val id = pendingOpenSpaceId!!
                                pendingOpenSpaceId = null
                                openAfterMpin(id)
                            } else {
                                mpinError = true
                            }
                        },
                        onDismiss = {
                            showMpinDialog = false
                            mpinError = false
                            pendingOpenSpaceId = null
                            statusMessage = "MPIN cancelled"
                        }
                    )
                }

                when (val screen = currentScreen) {
                    is Screen.AppLock -> AppLockScreen(
                        onUnlock = { pin ->
                            if (repo.verifyMpin(pin)) {
                                appUnlocked = true
                                currentScreen = Screen.Home
                                statusMessage = "Unlocked"
                                true
                            } else {
                                false
                            }
                        }
                    )

                    is Screen.Home -> HomeScreen(
                        spaces = spaces,
                        status = statusMessage,
                        hasNfc = nfcAdapter != null,
                        learnMode = learnMode != LearnMode.None,
                        deletePending = nfcAction == NfcAction.ConfirmDelete,
                        pendingDeleteName = pendingDeleteSpaceId?.let { repo.getSpaceById(it)?.name },
                        hideEntryCount = settings.hideEntryCount,
                        onLearnClick = {
                            cancelPendingDelete()
                            if (spaces.isEmpty()) {
                                pendingLearnName = ""
                                showNameDialog = true
                                learnMode = LearnMode.CreateNew
                            } else {
                                showLearnChoiceDialog = true
                            }
                        },
                        onRequestDelete = { spaceId ->
                            learnMode = LearnMode.None
                            pendingDeleteSpaceId = spaceId
                            nfcAction = NfcAction.ConfirmDelete
                            statusMessage = "Tap a linked card for \"${repo.getSpaceById(spaceId)?.name}\" to delete"
                        },
                        onCancelDelete = {
                            cancelPendingDelete()
                            statusMessage = getString(R.string.delete_cancelled)
                        },
                        onOpenSettings = { currentScreen = Screen.Settings }
                    )

                    is Screen.Vault -> {
                        val space = repo.getSpaceById(screen.spaceId)
                        if (space == null) currentScreen = Screen.Home
                        else VaultScreen(
                            space = space,
                            onUserAction = { lastInteraction = System.currentTimeMillis() },
                            onBack = {
                                currentScreen = Screen.Home
                                statusMessage = getString(R.string.vault_locked)
                            },
                            onAdd = { folderId ->
                                lastInteraction = System.currentTimeMillis()
                                currentScreen = Screen.EditItem(screen.spaceId, folderId, null)
                            },
                            onEdit = { folderId, item ->
                                lastInteraction = System.currentTimeMillis()
                                currentScreen = Screen.EditItem(screen.spaceId, folderId, item)
                            },
                            onDeleteItem = { folderId, itemId ->
                                repo.deleteItem(screen.spaceId, folderId, itemId)
                                spacesVersion++
                                lastInteraction = System.currentTimeMillis()
                            },
                            onRenameSpace = { name ->
                                repo.saveSpace(space.copy(name = name))
                                spacesVersion++
                            },
                            onAddFolder = { name ->
                                repo.addFolder(screen.spaceId, name)
                                spacesVersion++
                            },
                            onDeleteFolder = { folderId ->
                                repo.deleteFolder(screen.spaceId, folderId)
                                spacesVersion++
                                lastInteraction = System.currentTimeMillis()
                            },
                            onRequestDeleteSpace = {
                                currentScreen = Screen.Home
                                pendingDeleteSpaceId = screen.spaceId
                                nfcAction = NfcAction.ConfirmDelete
                                statusMessage = "Tap a linked card to confirm delete"
                            },
                            onCopy = {
                                copyToClipboard(it)
                                Toast.makeText(this@MainActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                            },
                            onOpenFile = { item -> openStoredFile(screen.spaceId, item) }
                        )
                    }

                    is Screen.EditItem -> EditItemScreen(
                        existing = screen.item,
                        onSaveText = { item ->
                            repo.addOrUpdateItem(screen.spaceId, screen.folderId, item)
                            spacesVersion++
                            lastInteraction = System.currentTimeMillis()
                            currentScreen = Screen.Vault(screen.spaceId)
                        },
                        onSaveFile = { title, uri, fileName, mime ->
                            try {
                                // Keep access to the picked URI across restarts when possible
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: SecurityException) {
                                // Some providers do not support persistable permission – still try copy
                            }
                            val resolvedMime = mime
                                ?: contentResolver.getType(uri)
                                ?: "*/*"
                            val resolvedName = if (fileName.isNotBlank() && fileName != "file") fileName
                            else {
                                var name = "file"
                                contentResolver.query(uri, null, null, null, null)?.use { c ->
                                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
                                }
                                name
                            }
                            val itemId = screen.item?.id ?: UUID.randomUUID().toString()
                            val ok = repo.saveEncryptedFile(screen.spaceId, itemId, uri, resolvedMime)
                            if (ok) {
                                val size = repo.getFileSize(screen.spaceId, itemId)
                                val item = SecureItem(
                                    id = itemId,
                                    title = title.ifBlank { resolvedName },
                                    type = "file",
                                    fileName = resolvedName,
                                    mimeType = resolvedMime,
                                    fileSize = size
                                )
                                repo.addOrUpdateItem(screen.spaceId, screen.folderId, item)
                                spacesVersion++
                                lastInteraction = System.currentTimeMillis()
                                currentScreen = Screen.Vault(screen.spaceId)
                                Toast.makeText(this@MainActivity, "File saved", Toast.LENGTH_SHORT).show()
                                // Local + cloud backup: upload to Cloudinary when logged in
                                if (sessionStore.isLoggedIn && sessionStore.cloudBackupEnabled) {
                                    val uploadUri = uri
                                    val uploadTitle = title.ifBlank { resolvedName }
                                    val uploadName = resolvedName
                                    val uploadMime = resolvedMime
                                    val sid = screen.spaceId
                                    val fid = screen.folderId
                                    val iid = itemId
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val result = apiClient.uploadFile(
                                            uploadUri, uploadTitle, uploadName, uploadMime, sid, fid, iid
                                        )
                                        result.onSuccess { cloud ->
                                            val updated = item.copy(
                                                cloudUrl = cloud.url,
                                                cloudFileId = cloud.id,
                                                fileSize = cloud.size
                                            )
                                            repo.addOrUpdateItem(sid, fid, updated)
                                            runOnUiThread {
                                                spacesVersion++
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Cloud backup OK",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }.onFailure { e ->
                                            runOnUiThread {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Local save OK · cloud: ${e.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Failed to save file – try another file", Toast.LENGTH_LONG).show()
                            }
                        },
                        onCancel = {
                            lastInteraction = System.currentTimeMillis()
                            currentScreen = Screen.Vault(screen.spaceId)
                        }
                    )

                    is Screen.Settings -> SettingsScreen(
                        settings = settings,
                        hasMpin = repo.hasMpinHash(),
                        onSave = {
                            repo.saveSettings(it)
                            spacesVersion++
                            Toast.makeText(this@MainActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                        },
                        onSetMpin = { pin ->
                            if (repo.setMpin(pin)) {
                                spacesVersion++
                                Toast.makeText(this@MainActivity, "MPIN set", Toast.LENGTH_SHORT).show()
                                true
                            } else {
                                Toast.makeText(this@MainActivity, "MPIN must be 4–8 digits", Toast.LENGTH_SHORT).show()
                                false
                            }
                        },
                        onClearMpin = {
                            repo.clearMpin()
                            spacesVersion++
                            Toast.makeText(this@MainActivity, "MPIN removed", Toast.LENGTH_SHORT).show()
                        },
                        onExport = { exportBackup() },
                        onImport = { triggerImport = true },
                        onBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }

        handleNfcIntent(intent)
    }

    private fun exportBackup() {
        try {
            val json = repo.exportBackup()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "NFC Security Backup")
                putExtra(Intent.EXTRA_TEXT, json)
            }
            startActivity(Intent.createChooser(send, "Export vault backup"))
        } catch (_: Exception) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelPendingDelete() {
        nfcAction = NfcAction.OpenOrLearn
        pendingDeleteSpaceId = null
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("secure", text))
    }

    private fun beginAuthAndOpen(spaceId: String) {
        // MPIN is app-lock only (shown on launch). After unlock, open vault with optional biometric.
        val settings = repo.getSettings()
        if (settings.biometricEnabled && canUseBiometric()) {
            showBiometricPrompt(spaceId)
        } else {
            finishOpenVault(spaceId)
        }
    }

    private fun openAfterMpin(spaceId: String) {
        val settings = repo.getSettings()
        if (settings.biometricEnabled && canUseBiometric()) {
            showBiometricPrompt(spaceId)
        } else {
            finishOpenVault(spaceId)
        }
    }

    private fun finishOpenVault(spaceId: String) {
        statusMessage = "${getString(R.string.opened)}: ${repo.getSpaceById(spaceId)?.name}"
        lastInteraction = System.currentTimeMillis()
        currentScreen = Screen.Vault(spaceId)
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt(spaceId: String) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    finishOpenVault(spaceId)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(this@MainActivity, "Biometric: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(this@MainActivity, "Not recognized", Toast.LENGTH_SHORT).show()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText("Cancel")
                .build()
        )
    }

    private fun openStoredFile(spaceId: String, item: SecureItem) {
        try {
            // Prefer local encrypted copy; fall back to Cloudinary link from Mongo
            val enc = repo.openEncryptedFile(spaceId, item.id)
            if (enc == null) {
                val remote = item.cloudUrl
                if (!remote.isNullOrBlank()) {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(remote)))
                    return
                }
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            val cacheDir = File(cacheDir, "share").also { it.mkdirs() }
            val outFile = File(cacheDir, item.fileName ?: "file")
            enc.openFileInput().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open ${item.fileName}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_TECH_DISCOVERED) return
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            ?: @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let { onTagDiscovered(it) }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        if (currentScreen is Screen.Vault || currentScreen is Screen.EditItem) {
            currentScreen = Screen.Home
            statusMessage = "Vault locked"
        }
        // Re-lock whole app when leaving (app-lock style)
        val s = repo.getSettings()
        if (s.mpinEnabled && repo.hasMpinHash() && appUnlocked) {
            appUnlocked = false
            currentScreen = Screen.AppLock
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag ?: return
        val uid = tag.id.joinToString("") { "%02X".format(it) }

        runOnUiThread {
            if (!appUnlocked && currentScreen is Screen.AppLock) {
                statusMessage = "Unlock app with MPIN first"
                Toast.makeText(this, "Enter MPIN first", Toast.LENGTH_SHORT).show()
                return@runOnUiThread
            }
            if (nfcAction == NfcAction.ConfirmDelete) {
                val targetId = pendingDeleteSpaceId
                if (targetId != null) {
                    val space = repo.getSpaceById(targetId)
                    val matches = space?.cardUids?.any { it.equals(uid, ignoreCase = true) } == true
                    if (matches) {
                        val name = space?.name ?: "space"
                        repo.deleteSpace(targetId)
                        spacesVersion++
                        cancelPendingDelete()
                        statusMessage = "\"$name\" deleted"
                        Toast.makeText(this, getString(R.string.space_deleted), Toast.LENGTH_SHORT).show()
                        currentScreen = Screen.Home
                    } else {
                        statusMessage = "Wrong card – delete cancelled"
                        cancelPendingDelete()
                    }
                }
                return@runOnUiThread
            }

            when (learnMode) {
                LearnMode.CreateNew -> {
                    // Always create a NEW vault for this registration flow.
                    // If the card is already linked elsewhere, it will simply unlock
                    // multiple vaults later (chooser). Never hijack into an old vault.
                    val name = pendingLearnName.ifBlank { "Card ${uid.takeLast(4)}" }
                    val created = repo.createSpace(uid, name)
                    learnMode = LearnMode.None
                    pendingLearnName = ""
                    spacesVersion++
                    statusMessage = getString(R.string.card_learned)
                    val alsoOn = repo.getSpacesForUid(uid).filter { it.id != created.id }
                    if (alsoOn.isNotEmpty()) {
                        Toast.makeText(
                            this,
                            "Vault \"$name\" created. This card also unlocks: ${alsoOn.joinToString { it.name }}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "Vault \"$name\" created", Toast.LENGTH_LONG).show()
                    }
                    beginAuthAndOpen(created.id)
                    return@runOnUiThread
                }
                LearnMode.AddToExisting -> {
                    val spaceId = pendingAddToSpaceId
                    if (spaceId != null) {
                        val ok = repo.addCardToSpace(spaceId, uid)
                        learnMode = LearnMode.None
                        pendingAddToSpaceId = null
                        spacesVersion++
                        if (ok) {
                            statusMessage = "Card linked to vault"
                            Toast.makeText(this, "Extra card linked", Toast.LENGTH_SHORT).show()
                        } else {
                            statusMessage = "Card already linked or failed"
                            Toast.makeText(this, "Card already linked", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@runOnUiThread
                }
                LearnMode.None -> { }
            }

            handleKnownCard(uid)
        }
    }

    private fun handleKnownCard(uid: String) {
        val matches = repo.getSpacesForUid(uid)
        when {
            matches.isEmpty() -> {
                statusMessage = getString(R.string.unknown_card)
                Toast.makeText(this, getString(R.string.unknown_card), Toast.LENGTH_SHORT).show()
            }
            matches.size == 1 -> beginAuthAndOpen(matches.first().id)
            else -> {
                vaultChooserSpaces = matches
                showVaultChooser = true
                statusMessage = "Choose which vault to open"
            }
        }
    }
}

@Composable
private fun NameSpaceDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this vault") },
        text = {
            Column {
                Text("e.g. Work, Personal, Banking")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Space name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifBlank { "My Vault" }) }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LearnChoiceDialog(
    spaces: List<CardSpace>,
    onCreateNew: () -> Unit,
    onAddToExisting: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register card") },
        text = {
            Column {
                Text("Create a new vault or link this card as an extra key to an existing vault.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) {
                    Text("Create new vault")
                }
                Spacer(Modifier.height(12.dp))
                Text("Or add to existing:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                spaces.forEach { space ->
                    OutlinedButton(
                        onClick = { onAddToExisting(space.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Text(space.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VaultChooserDialog(
    spaces: List<CardSpace>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which vault?") },
        text = {
            Column {
                Text("This card is linked to multiple vaults. Choose one to open:")
                Spacer(Modifier.height(12.dp))
                spaces.forEach { space ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(space.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            space.name,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MpinDialog(
    isError: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter MPIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4–8 digit MPIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        "Incorrect MPIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (pin.length in 4..8) onSubmit(pin) },
                enabled = pin.length in 4..8
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AppLockScreen(onUnlock: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text("NFC Security", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Enter your MPIN to continue",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = false
                    }
                },
                label = { Text("MPIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            if (error) {
                Text("Incorrect MPIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (pin.length in 4..8) {
                        val ok = onUnlock(pin)
                        if (!ok) {
                            error = true
                            pin = ""
                        }
                    }
                },
                enabled = pin.length in 4..8,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Unlock") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    spaces: List<CardSpace>,
    status: String,
    hasNfc: Boolean,
    learnMode: Boolean,
    deletePending: Boolean,
    pendingDeleteName: String?,
    hideEntryCount: Boolean,
    onLearnClick: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NFC Security", fontWeight = FontWeight.Bold)
                        Text(
                            "Protect your privacy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (hasNfc && !deletePending) {
                FloatingActionButton(
                    onClick = onLearnClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Learn card")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            when {
                deletePending -> {
                    Banner(
                        "Tap card for \"$pendingDeleteName\" to confirm delete",
                        MaterialTheme.colorScheme.errorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancelDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel delete")
                    }
                    Spacer(Modifier.height(16.dp))
                }
                learnMode -> {
                    Banner("Learning – hold bank card to phone", MaterialTheme.colorScheme.primaryContainer)
                    Spacer(Modifier.height(16.dp))
                }
                status.isNotEmpty() -> {
                    Banner(status, MaterialTheme.colorScheme.secondaryContainer)
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (!hasNfc) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NFC not available")
                }
                return@Column
            }

            if (spaces.isEmpty()) {
                EmptyHero(onLearnClick)
            } else {
                Text("Your locked vaults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap a linked card to unlock. Multiple cards can open the same vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(spaces, key = { it.id }) { space ->
                        LockedCard(
                            space = space,
                            hideCount = hideEntryCount,
                            highlight = deletePending && pendingDeleteName == space.name,
                            onDelete = { onRequestDelete(space.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Nfc, null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyHero(onLearn: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Your Passwords &\nFiles, Safely Secured",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Each vault can be unlocked by one or more cards.\nTap + to register a card.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onLearn,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(0.7f).height(52.dp)
            ) {
                Text("Get Started", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun LockedCard(space: CardSpace, hideCount: Boolean, highlight: Boolean, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(space.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append("${space.cardUids.size} card${if (space.cardUids.size == 1) "" else "s"}")
                        if (!hideCount) append("  ·  ${space.allItems.size} entries")
                        append("  ·  locked")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    space: CardSpace,
    onUserAction: () -> Unit,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (String, SecureItem) -> Unit,
    onDeleteItem: (String, String) -> Unit,
    onRenameSpace: (String) -> Unit,
    onAddFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRequestDeleteSpace: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenFile: (SecureItem) -> Unit
) {
    var selectedFolderId by remember { mutableStateOf(space.folders.firstOrNull()?.id ?: "") }
    var search by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(space.name) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showDeleteSpace by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<VaultFolder?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, SecureItem>?>(null) }
    var revealed by remember { mutableStateOf(setOf<String>()) }

    val folder = space.folders.find { it.id == selectedFolderId } ?: space.folders.firstOrNull()
    if (folder != null && selectedFolderId != folder.id) selectedFolderId = folder.id

    val filtered = folder?.items?.filter {
        search.isBlank() || it.title.contains(search, true) ||
                it.value.contains(search, true) || it.type.contains(search, true) ||
                (it.fileName?.contains(search, true) == true)
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(space.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Lock")
                    }
                },
                actions = {
                    IconButton(onClick = { onUserAction(); showNewFolder = true }) {
                        Icon(Icons.Default.CreateNewFolder, "New folder")
                    }
                    IconButton(onClick = { onUserAction(); showRename = true }) {
                        Icon(Icons.Default.Edit, "Rename")
                    }
                    IconButton(onClick = { onUserAction(); showDeleteSpace = true }) {
                        Icon(Icons.Default.Delete, "Delete space")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (folder != null) {
                FloatingActionButton(
                    onClick = { onUserAction(); onAdd(folder.id) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it; onUserAction() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search entries") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                space.folders.forEach { f ->
                    FilterChip(
                        selected = f.id == selectedFolderId,
                        onClick = { selectedFolderId = f.id; onUserAction() },
                        label = { Text(f.name) },
                        leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) },
                        trailingIcon = if (space.folders.size > 1) {
                            {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete folder",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            onUserAction()
                                            folderToDelete = f
                                        },
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${space.cardUids.size} linked card${if (space.cardUids.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isNotBlank()) "No matches" else "Empty folder – tap + to add",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            isRevealed = item.id in revealed,
                            onToggle = {
                                onUserAction()
                                revealed = if (item.id in revealed) revealed - item.id else revealed + item.id
                            },
                            onCopy = { onUserAction(); onCopy(item.value) },
                            onEdit = { onUserAction(); folder?.let { onEdit(it.id, item) } },
                            onDelete = { onUserAction(); folder?.let { itemToDelete = it.id to item } },
                            onOpenFile = { onUserAction(); onOpenFile(item) }
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename vault") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRenameSpace(renameText.trim())
                    showRename = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Folder name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) onAddFolder(newFolderName.trim())
                    newFolderName = ""
                    showNewFolder = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteSpace) {
        AlertDialog(
            onDismissRequest = { showDeleteSpace = false },
            title = { Text("Delete vault?") },
            text = { Text("Tap a linked bank card to confirm. All folders, entries and files will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteSpace = false; onRequestDeleteSpace() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Require card") }
            },
            dismissButton = { TextButton(onClick = { showDeleteSpace = false }) { Text("Cancel") } }
        )
    }
    folderToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?") },
            text = {
                Text(
                    if (f.items.isEmpty()) "Remove folder \"${f.name}\"?"
                    else "Remove folder \"${f.name}\" and its ${f.items.size} entries?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder(f.id)
                        if (selectedFolderId == f.id) {
                            selectedFolderId = space.folders.firstOrNull { it.id != f.id }?.id ?: ""
                        }
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Cancel") } }
        )
    }
    itemToDelete?.let { (fid, item) ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("Remove \"${item.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteItem(fid, item.id); itemToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ItemCard(
    item: SecureItem,
    isRevealed: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.type == "file") {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                item.type.uppercase() + if (item.type == "file" && item.fileName != null) " · ${item.fileName}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            when (item.type) {
                "file" -> {
                    Text(
                        formatSize(item.fileSize) + (item.mimeType?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "password" -> {
                    Text(
                        if (!isRevealed) "••••••••••••" else item.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace
                    )
                }
                else -> {
                    Text(
                        item.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = if (item.type == "id") FontFamily.Monospace else FontFamily.Default
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (item.type == "password") {
                    IconButton(onClick = onToggle) {
                        Icon(if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
                if (item.type == "file") {
                    IconButton(onClick = onOpenFile) {
                        Icon(Icons.Default.OpenInNew, "Open file")
                    }
                } else {
                    IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    existing: SecureItem?,
    onSaveText: (SecureItem) -> Unit,
    onSaveFile: (title: String, uri: Uri, fileName: String, mime: String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var value by remember { mutableStateOf(existing?.value ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "password") }
    var showValue by remember { mutableStateOf(existing?.type != "password") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf(existing?.fileName ?: "") }
    var pickedMime by remember { mutableStateOf(existing?.mimeType) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            type = "file"
            // Prefer real display name from the content provider
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            // Caller Activity will refine mime/name on save; keep a sensible default here
            pickedName = name
            pickedMime = null
            if (title.isBlank()) title = name
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add entry" else "Edit entry") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            if (type != "file") {
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text("Password / ID / Note") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (type == "password" && !showValue) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = {
                        if (type == "password") {
                            IconButton(onClick = { showValue = !showValue }) {
                                Icon(if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        }
                    }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (pickedName.isNotBlank()) pickedName else "No file selected",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Images, videos, documents, txt…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (pickedUri != null || existing?.type == "file") "Change file" else "Choose file")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Type", style = MaterialTheme.typography.labelLarge)
            listOf(
                "password" to "Password",
                "id" to "ID / Number",
                "note" to "Note",
                "file" to "File (image / video / doc)"
            ).forEach { (k, l) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        type = k
                        if (k == "file" && pickedUri == null && existing?.type != "file") {
                            filePicker.launch(arrayOf("*/*"))
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = type == k, onClick = {
                        type = k
                        if (k == "file" && pickedUri == null && existing?.type != "file") {
                            filePicker.launch(arrayOf("*/*"))
                        }
                    })
                    Text(l)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when (type) {
                        "file" -> {
                            val uri = pickedUri
                            if (uri != null && title.isNotBlank()) {
                                onSaveFile(title.trim(), uri, pickedName.ifBlank { "file" }, pickedMime)
                            } else if (existing?.type == "file" && title.isNotBlank()) {
                                onSaveText(existing.copy(title = title.trim()))
                            }
                        }
                        else -> {
                            if (title.isNotBlank() && value.isNotBlank()) {
                                onSaveText(
                                    SecureItem(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        title = title.trim(),
                                        value = value,
                                        type = type
                                    )
                                )
                            }
                        }
                    }
                },
                enabled = when (type) {
                    "file" -> title.isNotBlank() && (pickedUri != null || existing?.type == "file")
                    else -> title.isNotBlank() && value.isNotBlank()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Save") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Cancel")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasMpin: Boolean,
    onSave: (AppSettings) -> Unit,
    onSetMpin: (String) -> Boolean,
    onClearMpin: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    var biometric by remember { mutableStateOf(settings.biometricEnabled) }
    var mpinEnabled by remember { mutableStateOf(settings.mpinEnabled) }
    var autoLock by remember { mutableStateOf(settings.autoLockSeconds.toFloat()) }
    var hideCount by remember { mutableStateOf(settings.hideEntryCount) }
    var showSetMpin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            SettingsRow("Biometric after card unlock", "Fingerprint / face after NFC") {
                Switch(checked = biometric, onCheckedChange = { biometric = it })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsRow("MPIN required", "4–8 digit PIN after NFC (before biometric if both on)") {
                Switch(
                    checked = mpinEnabled && hasMpin,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!hasMpin) showSetMpin = true
                            else mpinEnabled = true
                        } else {
                            mpinEnabled = false
                        }
                    }
                )
            }
            if (hasMpin) {
                TextButton(onClick = { showSetMpin = true }) { Text("Change MPIN") }
                TextButton(onClick = {
                    onClearMpin()
                    mpinEnabled = false
                }) { Text("Remove MPIN") }
            } else {
                TextButton(onClick = { showSetMpin = true }) { Text("Set MPIN") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Auto-lock after idle", style = MaterialTheme.typography.titleSmall)
            Text("${autoLock.toInt()} seconds", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = autoLock, onValueChange = { autoLock = it }, valueRange = 15f..180f, steps = 10)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsRow("Hide entry counts", "Less info on locked home list") {
                Switch(checked = hideCount, onCheckedChange = { hideCount = it })
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(AppSettings(biometric, mpinEnabled && hasMpin, autoLock.toInt(), hideCount))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Save settings") }
            Spacer(Modifier.height(32.dp))
            Text("Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Export shares vault JSON (metadata). Files are stored encrypted on device and are not part of the JSON backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Export backup")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Import backup")
            }
        }
    }

    if (showSetMpin) {
        AlertDialog(
            onDismissRequest = { showSetMpin = false; newPin = ""; confirmPin = "" },
            title = { Text(if (hasMpin) "Change MPIN" else "Set MPIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New MPIN (4–8 digits)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text("Confirm MPIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPin == confirmPin && newPin.length in 4..8) {
                            if (onSetMpin(newPin)) {
                                mpinEnabled = true
                                showSetMpin = false
                                newPin = ""
                                confirmPin = ""
                            }
                        }
                    },
                    enabled = newPin == confirmPin && newPin.length in 4..8
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSetMpin = false; newPin = ""; confirmPin = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
