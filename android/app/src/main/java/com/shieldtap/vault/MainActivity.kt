package com.shieldtap.vault

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.shieldtap.vault.data.ApiClient
import com.shieldtap.vault.data.FolderDto
import com.shieldtap.vault.data.NfcStore
import com.shieldtap.vault.data.SessionStore
import com.shieldtap.vault.ui.auth.LoginScreen
import com.shieldtap.vault.ui.auth.RegisterScreen
import com.shieldtap.vault.ui.auth.SetMpinScreen
import com.shieldtap.vault.ui.components.ShieldLoadingAnimation
import com.shieldtap.vault.ui.components.UnlockScreen
import com.shieldtap.vault.ui.folder.FolderScreen
import com.shieldtap.vault.ui.home.HomeScreen
import com.shieldtap.vault.ui.profile.ProfileScreen
import com.shieldtap.vault.ui.settings.SettingsScreen
import com.shieldtap.vault.ui.theme.ShieldTapTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var nfcStore: NfcStore

    /** Latest NFC UID observed – Compose collects this */
    private val nfcUidState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionStore = SessionStore(applicationContext)
        nfcStore = NfcStore(applicationContext)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        ApiClient.setTokenProvider {
            kotlinx.coroutines.runBlocking { sessionStore.getToken() }
        }

        // Handle NFC tag if activity was started by a tag
        intent?.let { tryReadTag(it) }

        setContent {
            var themeMode by remember { mutableStateOf("system") }
            var startRoute by remember { mutableStateOf<String?>(null) }
            val nfcUid by nfcUidState

            LaunchedEffect(Unit) {
                themeMode = sessionStore.themeFlow.first()
                val token = sessionStore.getToken()
                val mpinSet = sessionStore.mpinSetFlow.first()
                startRoute = when {
                    token.isNullOrBlank() -> "login"
                    !mpinSet -> "set_mpin"
                    else -> {
                        try {
                            ApiClient.api.me()
                            sessionStore.lock()
                            "unlock"
                        } catch (_: Exception) {
                            sessionStore.clearSession()
                            "login"
                        }
                    }
                }
            }

            ShieldTapTheme(themeMode = themeMode) {
                if (startRoute == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ShieldLoadingAnimation(
                            message = "Starting ShieldTap",
                            subMessage = "Checking session…"
                        )
                    }
                } else {
                    AppNav(
                        sessionStore = sessionStore,
                        nfcStore = nfcStore,
                        nfcUidEvent = nfcUid,
                        onNfcConsumed = { nfcUidState.value = null },
                        startDestination = startRoute!!,
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reader mode – works while app is in foreground
        nfcAdapter?.enableReaderMode(
            this,
            { tag: Tag ->
                val uid = NfcStore.tagIdHex(tag)
                runOnUiThread { nfcUidState.value = uid }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tryReadTag(intent)
    }

    private fun tryReadTag(intent: android.content.Intent) {
        val tag: Tag? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag?.let { nfcUidState.value = NfcStore.tagIdHex(it) }
    }
}

@Composable
fun AppNav(
    sessionStore: SessionStore,
    nfcStore: NfcStore,
    nfcUidEvent: String?,
    onNfcConsumed: () -> Unit,
    startDestination: String,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    val navController = rememberNavController()
    var folderStack by remember { mutableStateOf<List<FolderDto>>(emptyList()) }
    val unlocked by sessionStore.unlocked.collectAsState()

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            LoginScreen(
                sessionStore = sessionStore,
                nfcStore = nfcStore,
                nfcUidEvent = nfcUidEvent,
                onNfcConsumed = onNfcConsumed,
                onLoginSuccess = {
                    navController.navigate("set_mpin") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onQuickUnlock = {
                    sessionStore.unlock()
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onGoRegister = { navController.navigate("register") }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegistered = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("set_mpin") {
            val mpinSet by sessionStore.mpinSetFlow.collectAsState(initial = false)
            if (mpinSet) {
                LaunchedEffect(Unit) {
                    sessionStore.lock()
                    navController.navigate("unlock") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                SetMpinScreen(
                    sessionStore = sessionStore,
                    onMpinSet = {
                        sessionStore.unlock()
                        navController.navigate("main") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("unlock") {
            UnlockScreen(
                sessionStore = sessionStore,
                nfcStore = nfcStore,
                nfcUidEvent = nfcUidEvent,
                onNfcConsumed = onNfcConsumed,
                onUnlocked = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            if (!unlocked) {
                LaunchedEffect(Unit) {
                    navController.navigate("unlock") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                MainScaffold(
                    sessionStore = sessionStore,
                    nfcStore = nfcStore,
                    nfcUidEvent = nfcUidEvent,
                    onNfcConsumed = onNfcConsumed,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onLogout = {
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenFolder = { folder ->
                        folderStack = listOf(folder)
                        navController.navigate("folder")
                    },
                    onLockApp = {
                        sessionStore.lock()
                        navController.navigate("unlock") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("folder") {
            if (!unlocked) {
                LaunchedEffect(Unit) {
                    navController.navigate("unlock") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                val current = folderStack.lastOrNull()
                if (current == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    // System back goes up one folder level, then exits to main
                    BackHandler {
                        if (folderStack.size > 1) {
                            folderStack = folderStack.dropLast(1)
                        } else {
                            folderStack = emptyList()
                            navController.popBackStack()
                        }
                    }
                    key(current._id) {
                        FolderScreen(
                            folder = current,
                            onBack = {
                                if (folderStack.size > 1) {
                                    folderStack = folderStack.dropLast(1)
                                } else {
                                    folderStack = emptyList()
                                    navController.popBackStack()
                                }
                            },
                            onOpenSubFolder = { sub ->
                                folderStack = folderStack + sub
                            }
                        )
                    }
                }
            }
        }

        composable("settings") {
            SettingsScreen(
                sessionStore = sessionStore,
                nfcStore = nfcStore,
                nfcUidEvent = nfcUidEvent,
                onNfcConsumed = onNfcConsumed,
                currentTheme = themeMode,
                onThemeChange = onThemeChange,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainScaffold(
    sessionStore: SessionStore,
    nfcStore: NfcStore,
    nfcUidEvent: String?,
    onNfcConsumed: () -> Unit,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenFolder: (FolderDto) -> Unit,
    onLockApp: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            sessionStore = sessionStore,
            nfcStore = nfcStore,
            nfcUidEvent = nfcUidEvent,
            onNfcConsumed = onNfcConsumed,
            currentTheme = themeMode,
            onThemeChange = onThemeChange,
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                tonalElevation = 0.dp,
                shadowElevation = 12.dp
            ) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(32.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.0f),
                    tonalElevation = 0.dp
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Star, null) },
                        label = { Text("Soon") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("Profile") },
                        colors = itemColors
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    sessionStore = sessionStore,
                    nfcStore = nfcStore,
                    nfcUidEvent = nfcUidEvent,
                    onNfcConsumed = onNfcConsumed,
                    onOpenFolder = onOpenFolder,
                    onLockApp = onLockApp
                )
                1 -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Coming soon",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                2 -> ProfileScreen(
                    sessionStore = sessionStore,
                    onOpenSettings = { showSettings = true },
                    onLogout = onLogout
                )
            }
        }
    }
}
