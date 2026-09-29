package com.shieldtap.vault.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldtap.vault.data.ApiClient
import com.shieldtap.vault.data.FolderDto
import com.shieldtap.vault.data.SessionStore
import com.shieldtap.vault.ui.components.MpinDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sessionStore: SessionStore,
    onOpenFolder: (FolderDto) -> Unit,
    onLockApp: () -> Unit
) {
    var folders by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showMpinForCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showMpin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            try {
                val res = ApiClient.api.getFolders(null)
                folders = res["folders"] ?: emptyList()
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Vaults", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onLockApp) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock app")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showMpinForCreate = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New folder")
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { load() }) { Text("Retry") }
                }
                folders.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No folders yet")
                    Text("Tap + to create your first vault", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folders, key = { it._id }) { folder ->
                        FolderCard(
                            folder = folder,
                            onClick = {
                                if (folder.isLocked) {
                                    pendingAction = { onOpenFolder(folder) }
                                    showMpin = true
                                } else {
                                    onOpenFolder(folder)
                                }
                            },
                            onRename = { name ->
                                pendingAction = {
                                    scope.launch {
                                        try {
                                            ApiClient.api.updateFolder(folder._id, mapOf("name" to name))
                                            load()
                                        } catch (_: Exception) {}
                                    }
                                }
                                showMpin = true
                            },
                            onDelete = {
                                pendingAction = {
                                    scope.launch {
                                        try {
                                            ApiClient.api.deleteFolder(folder._id)
                                            load()
                                        } catch (_: Exception) {}
                                    }
                                }
                                showMpin = true
                            },
                            onToggleLock = {
                                pendingAction = {
                                    scope.launch {
                                        try {
                                            ApiClient.api.updateFolder(folder._id, mapOf("isLocked" to !folder.isLocked))
                                            load()
                                        } catch (_: Exception) {}
                                    }
                                }
                                showMpin = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showMpinForCreate) {
        MpinDialog(
            sessionStore = sessionStore,
            title = "Enter MPIN to create folder",
            onSuccess = {
                showMpinForCreate = false
                showCreate = true
            },
            onDismiss = { showMpinForCreate = false }
        )
    }

    if (showMpin && pendingAction != null) {
        MpinDialog(
            sessionStore = sessionStore,
            onSuccess = {
                showMpin = false
                pendingAction?.invoke()
                pendingAction = null
            },
            onDismiss = {
                showMpin = false
                pendingAction = null
            }
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch {
                            try {
                                ApiClient.api.createFolder(mapOf("name" to newName.trim(), "parentId" to null))
                                newName = ""
                                showCreate = false
                                load()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FolderCard(
    folder: FolderDto,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onToggleLock: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(folder.name) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (folder.isLocked) Icons.Default.Folder else Icons.Default.FolderOpen,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (folder.isLocked) {
                    Text("Locked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (folder.isLocked) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { menuOpen = false; renameOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (folder.isLocked) "Unlock" else "Lock") },
                        onClick = { menuOpen = false; onToggleLock() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    renameOpen = false
                    if (renameText.isNotBlank()) onRename(renameText.trim())
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
        )
    }
}
