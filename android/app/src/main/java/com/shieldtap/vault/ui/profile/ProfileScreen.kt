package com.shieldtap.vault.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.shieldtap.vault.data.ApiClient
import com.shieldtap.vault.data.NotificationDto
import com.shieldtap.vault.data.SessionStore
import com.shieldtap.vault.data.UserDto
import com.shieldtap.vault.data.toUserMessage
import com.shieldtap.vault.ui.components.PillButton
import com.shieldtap.vault.ui.components.PillShape
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionStore: SessionStore,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    var user by remember { mutableStateOf<UserDto?>(null) }
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun load() {
        scope.launch {
            loading = true
            try {
                val me = ApiClient.api.me()
                user = me.user
                notifications = me.user.notifications ?: emptyList()
                sessionStore.saveSession(
                    sessionStore.getToken() ?: "",
                    me.expiresAt,
                    Gson().toJson(me.user)
                )
            } catch (e: Exception) {
                sessionStore.getUserJson()?.let {
                    user = Gson().fromJson(it, UserDto::class.java)
                }
                snackbarHostState.showSnackbar(e.toUserMessage())
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val part = MultipartBody.Part.createFormData(
                    "avatar", "avatar.jpg",
                    bytes.toRequestBody("image/*".toMediaTypeOrNull())
                )
                ApiClient.api.uploadAvatar(part)
                load()
                snackbarHostState.showSnackbar("Avatar updated")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.toUserMessage())
            }
        }
    }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val part = MultipartBody.Part.createFormData(
                    "background", "bg.jpg",
                    bytes.toRequestBody("image/*".toMediaTypeOrNull())
                )
                ApiClient.api.uploadBackground(part)
                load()
                snackbarHostState.showSnackbar("Background updated")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.toUserMessage())
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (loading && user == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { bgPicker.launch("image/*") }
                    ) {
                        if (!user?.backgroundUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user?.backgroundUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = 16.dp)
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!user?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = user?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            user?.displayName ?: user?.username ?: "User",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "@${user?.username ?: ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodyMedium)
                        if (!user?.phone.isNullOrBlank()) {
                            Text(user?.phone ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Notifications") },
                        leadingContent = { Icon(Icons.Default.Notifications, null) }
                    )
                }

                items(notifications) { n ->
                    ListItem(
                        headlineContent = { Text(n.message) },
                        supportingContent = { Text(n.type.replace('_', ' ')) },
                        leadingContent = {
                            Icon(
                                when (n.type) {
                                    "login" -> Icons.Default.Login
                                    "register" -> Icons.Default.PersonAdd
                                    "profile_update" -> Icons.Default.Edit
                                    else -> Icons.Default.Info
                                },
                                null
                            )
                        }
                    )
                }

                if (notifications.isEmpty()) {
                    item {
                        Text(
                            "No notifications yet",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    PillButton(
                        text = "Logout",
                        onClick = {
                            scope.launch {
                                try {
                                    ApiClient.api.logout()
                                } catch (_: Exception) {
                                }
                                sessionStore.clearSession()
                                onLogout()
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
