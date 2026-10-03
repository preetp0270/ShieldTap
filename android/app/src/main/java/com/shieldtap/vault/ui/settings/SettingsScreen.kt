package com.shieldtap.vault.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shieldtap.vault.data.NfcStore
import com.shieldtap.vault.data.SessionStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionStore: SessionStore,
    nfcStore: NfcStore,
    nfcUidEvent: String?,
    onNfcConsumed: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var registeringNfc by remember { mutableStateOf(false) }
    var cardRegistered by remember { mutableStateOf(nfcStore.isCardRegistered()) }

    // Capture next NFC tap to register
    LaunchedEffect(nfcUidEvent, registeringNfc) {
        if (!registeringNfc) return@LaunchedEffect
        val uid = nfcUidEvent ?: return@LaunchedEffect
        nfcStore.registerCard(uid)
        cardRegistered = true
        registeringNfc = false
        onNfcConsumed()
        snackbarHostState.showSnackbar("NFC card registered")
    }

    val options = listOf(
        Triple("system", "System default", Icons.Default.PhoneAndroid),
        Triple("light", "Light", Icons.Default.LightMode),
        Triple("dark", "Dark", Icons.Default.DarkMode)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    options.forEach { (value, label, icon) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currentTheme == value,
                                    onClick = {
                                        onThemeChange(value)
                                        scope.launch { sessionStore.setTheme(value) }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null)
                            Spacer(Modifier.width(16.dp))
                            Text(label, modifier = Modifier.weight(1f))
                            RadioButton(
                                selected = currentTheme == value,
                                onClick = null
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("NFC Card", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Register a card to unlock the app, folders, and login without typing MPIN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Nfc, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (cardRegistered) "Card registered"
                                else if (!nfcStore.hasHardware()) "NFC not available on this device"
                                else if (!nfcStore.isNfcAvailable()) "NFC is turned off"
                                else "No card registered"
                            )
                            if (registeringNfc) {
                                Text(
                                    "Hold card near the phone…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!nfcStore.hasHardware()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("This device has no NFC")
                                    }
                                    return@Button
                                }
                                if (!nfcStore.isNfcAvailable()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Turn on NFC in system settings")
                                    }
                                    return@Button
                                }
                                registeringNfc = true
                            },
                            shape = RoundedCornerShape(50),
                            enabled = !registeringNfc
                        ) {
                            Text(if (cardRegistered) "Replace card" else "Register card")
                        }
                        if (cardRegistered) {
                            OutlinedButton(
                                onClick = {
                                    nfcStore.clearCard()
                                    cardRegistered = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("NFC card removed")
                                    }
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Remove")
                            }
                        }
                        if (registeringNfc) {
                            TextButton(onClick = { registeringNfc = false }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Theme uses black / white / grey with glass surfaces. NFC UID is stored only on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
