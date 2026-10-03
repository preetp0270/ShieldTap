package com.shieldtap.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shieldtap.vault.data.SessionStore
import com.shieldtap.vault.ui.theme.GlassBlack
import com.shieldtap.vault.ui.theme.GlassWhite
import com.shieldtap.vault.ui.theme.GlassWhiteStrong

/** Pill shape used across the app */
val PillShape = RoundedCornerShape(50)

/** Soft card radius */
val CardShape = RoundedCornerShape(20.dp)

/** Glass surface – semi-transparent with subtle border */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == Color.Black ||
            MaterialTheme.colorScheme.background.red < 0.15f
    val glassColor = if (isDark) GlassWhite else GlassBlack
    val borderColor = if (isDark) GlassWhiteStrong else Color(0x22000000)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(glassColor)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .padding(16.dp),
        content = content
    )
}

/** Pill-shaped primary button */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Outlined pill button */
@Composable
fun PillOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

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
        GlassCard(radius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                if (sessionStore.isLocked()) {
                    val min = (sessionStore.lockRemainingMs() / 60000) + 1
                    Text(
                        "Locked for $min more minute(s)",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedTextField(
                        value = mpin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) mpin = it },
                        label = { Text("MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
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
                        enabled = !sessionStore.isLocked(),
                        shape = PillShape
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
        shape = RoundedCornerShape(24.dp),
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

/** Full-screen unlock gate when app is locked */
@Composable
fun UnlockScreen(
    sessionStore: SessionStore,
    onUnlocked: () -> Unit
) {
    var mpin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            radius = 28.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("ShieldTap Locked", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Enter your device MPIN to continue",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(28.dp))

                if (sessionStore.isLocked()) {
                    val min = (sessionStore.lockRemainingMs() / 60000) + 1
                    Text(
                        "Too many attempts. Try again in $min min",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedTextField(
                        value = mpin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) mpin = it },
                        label = { Text("MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(24.dp))
                PillButton(
                    text = "Unlock",
                    onClick = {
                        if (sessionStore.isLocked()) return@PillButton
                        if (sessionStore.verifyMpin(mpin)) {
                            sessionStore.resetFailedAttempts()
                            sessionStore.unlock()
                            onUnlocked()
                        } else {
                            val locked = sessionStore.recordFailedAttempt()
                            error = if (locked) "Wrong 3 times. Locked 5 min." else "Wrong MPIN"
                            mpin = ""
                        }
                    },
                    enabled = !sessionStore.isLocked(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
