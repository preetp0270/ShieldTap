package com.shieldtap.vault.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shieldtap.vault.data.SessionStore

/**
 * Asks for MPIN. On success calls onSuccess.
 * Handles 3-strike lock.
 */
@Composable
fun MpinDialog(
    sessionStore: SessionStore,
    title: String = "Enter MPIN",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var mpin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                if (sessionStore.isLocked()) {
                    val min = (sessionStore.lockRemainingMs() / 60000) + 1
                    Text("Locked for $min more minute(s)", color = MaterialTheme.colorScheme.error)
                } else {
                    OutlinedTextField(
                        value = mpin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) mpin = it },
                        label = { Text("MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (sessionStore.isLocked()) return@Button
                            if (sessionStore.verifyMpin(mpin)) {
                                sessionStore.resetFailedAttempts()
                                onSuccess()
                            } else {
                                val locked = sessionStore.recordFailedAttempt()
                                error = if (locked) "Wrong 3 times. Locked 5 min." else "Wrong MPIN"
                                mpin = ""
                            }
                        },
                        enabled = !sessionStore.isLocked()
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("No") }
        }
    )
}
