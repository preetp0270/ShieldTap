package com.shieldtap.vault.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shieldtap.vault.data.SessionStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionStore: SessionStore,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val options = listOf(
        "system" to "System default" to Icons.Default.PhoneAndroid,
        "light" to "Light" to Icons.Default.LightMode,
        "dark" to "Dark" to Icons.Default.DarkMode
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            options.forEach { (pair, icon) ->
                val (value, label) = pair
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
                        .padding(vertical = 12.dp),
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

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text(
                "Theme applies to every screen in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
