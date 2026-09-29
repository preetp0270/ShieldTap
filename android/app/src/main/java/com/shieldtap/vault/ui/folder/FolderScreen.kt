package com.shieldtap.vault.ui.folder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shieldtap.vault.data.ApiClient
import com.shieldtap.vault.data.FolderDto
import com.shieldtap.vault.data.VaultItemDto
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folder: FolderDto,
    onBack: () -> Unit,
    onOpenSubFolder: (FolderDto) -> Unit
) {
    var subFolders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var items by remember { mutableStateOf<List<VaultItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var passKey by remember { mutableStateOf("") }
    var passValue by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun load() {
        scope.launch {
            loading = true
            try {
                val fRes = ApiClient.api.getFolders(folder._id)
                subFolders = fRes["folders"] ?: emptyList()
                val iRes = ApiClient.api.getVaultItems(folder._id)
                items = iRes["items"] ?: emptyList()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(folder._id) { load() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val part = MultipartBody.Part.createFormData(
                    "file", "image.jpg",
                    bytes.toRequestBody("image/*".toMediaTypeOrNull())
                )
                ApiClient.api.uploadFile(
                    part,
                    folder._id.toRequestBody(),
                    "image".toRequestBody(),
                    null
                )
                load()
            } catch (_: Exception) {}
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val name = uri.lastPathSegment ?: "file"
                val part = MultipartBody.Part.createFormData(
                    "file", name,
                    bytes.toRequestBody("*/*".toMediaTypeOrNull())
                )
                ApiClient.api.uploadFile(
                    part,
                    folder._id.toRequestBody(),
                    "file".toRequestBody(),
                    name.toRequestBody()
                )
                load()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder.name, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { /* lock this folder – only main folders, handled on home */ }) {
                        Icon(Icons.Default.Lock, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddMenu = true }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subFolders.isNotEmpty()) {
                        item {
                            Text("Folders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }
                        items(subFolders, key = { it._id }) { sf ->
                            ListItem(
                                headlineContent = { Text(sf.name) },
                                leadingContent = { Icon(Icons.Default.Folder, null) },
                                modifier = Modifier.clickable { onOpenSubFolder(sf) }
                            )
                        }
                    }
                    if (items.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("Items", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                        }
                        items(items, key = { it._id }) { item ->
                            VaultItemRow(item = item, onDelete = {
                                scope.launch {
                                    try {
                                        ApiClient.api.deleteItem(item._id)
                                        load()
                                    } catch (_: Exception) {}
                                }
                            })
                        }
                    }
                    if (subFolders.isEmpty() && items.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Inbox, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                    Text("Empty folder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            title = { Text("Add to folder") },
            text = {
                Column {
                    TextButton(onClick = { showAddMenu = false; showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Key, null); Spacer(Modifier.width(8.dp)); Text("Password")
                    }
                    TextButton(onClick = { showAddMenu = false; imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Photo")
                    }
                    TextButton(onClick = { showAddMenu = false; filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(8.dp)); Text("File")
                    }
                    TextButton(onClick = { showAddMenu = false; showNewFolder = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("New sub-folder")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMenu = false }) { Text("Close") } }
        )
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Add Password") },
            text = {
                Column {
                    OutlinedTextField(value = passKey, onValueChange = { passKey = it }, label = { Text("Key (e.g. Gmail)") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = passValue, onValueChange = { passValue = it }, label = { Text("Value (password)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passKey.isNotBlank() && passValue.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiClient.api.addPassword(mapOf("folderId" to folder._id, "key" to passKey, "value" to passValue))
                                passKey = ""; passValue = ""
                                showPasswordDialog = false
                                load()
                            } catch (_: Exception) {}
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("New sub-folder") },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        scope.launch {
                            try {
                                // Nested folders – no MPIN required
                                ApiClient.api.createFolder(mapOf("name" to newFolderName.trim(), "parentId" to folder._id))
                                newFolderName = ""
                                showNewFolder = false
                                load()
                            } catch (_: Exception) {}
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultItemRow(item: VaultItemDto, onDelete: () -> Unit) {
    var showValue by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (item.type) {
                "password" -> {
                    Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.key ?: "", fontWeight = FontWeight.Medium)
                        Text(
                            if (showValue) (item.value ?: "") else "••••••••",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { showValue = !showValue }) {
                        Icon(if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
                "image" -> {
                    AsyncImage(model = item.url, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item.title ?: "Image", modifier = Modifier.weight(1f))
                }
                else -> {
                    Icon(Icons.Default.InsertDriveFile, null)
                    Spacer(Modifier.width(12.dp))
                    Text(item.title ?: "File", modifier = Modifier.weight(1f))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
