package com.shieldtap.vault

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.shieldtap.vault.data.ApiClient
import com.shieldtap.vault.data.FolderDto
import com.shieldtap.vault.data.SessionStore
import com.shieldtap.vault.ui.auth.LoginScreen
import com.shieldtap.vault.ui.auth.RegisterScreen
import com.shieldtap.vault.ui.auth.SetMpinScreen
import com.shieldtap.vault.ui.folder.FolderScreen
import com.shieldtap.vault.ui.home.HomeScreen
import com.shieldtap.vault.ui.profile.ProfileScreen
import com.shieldtap.vault.ui.settings.SettingsScreen
import com.shieldtap.vault.ui.theme.ShieldTapTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionStore = SessionStore(applicationContext)
        ApiClient.setTokenProvider { 
            // Blocking read is ok for interceptor in this simple setup
            kotlinx.coroutines.runBlocking { sessionStore.getToken() }
        }

        setContent {
            var themeMode by remember { mutableStateOf("system") }
            var startRoute by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                themeMode = sessionStore.themeFlow.first()
                val token = sessionStore.getToken()
                val mpinSet = sessionStore.mpinSetFlow.first()
                startRoute = when {
                    token.isNullOrBlank() -> "login"
                    !mpinSet -> "set_mpin"
                    else -> {
                        // Touch session to extend
                        try {
                            ApiClient.api.me()
                        } catch (_: Exception) {
                            sessionStore.clearSession()
                            "login"
                        }
                        "main"
                    }
                }
            }

            ShieldTapTheme(themeMode = themeMode) {
                if (startRoute == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AppNav(
                        sessionStore = sessionStore,
                        startDestination = startRoute!!,
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNav(
    sessionStore: SessionStore,
    startDestination: String,
    themeMode: String,
    onThemeChange: (String) -> Unit
) {
    val navController = rememberNavController()
    // Stack of folders for nested navigation
    var folderStack by remember { mutableStateOf<List<FolderDto>>(emptyList()) }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("login") {
            LoginScreen(
                sessionStore = sessionStore,
                onLoginSuccess = {
                    // After login → check MPIN
                    navController.navigate("set_mpin") {
                        popUpTo("login") { inclusive = true }
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
            // Only show if not already set
            val mpinSet by sessionStore.mpinSetFlow.collectAsState(initial = false)
            if (mpinSet) {
                LaunchedEffect(Unit) {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                SetMpinScreen(
                    sessionStore = sessionStore,
                    onMpinSet = {
                        navController.navigate("main") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable("main") {
            MainScaffold(
                sessionStore = sessionStore,
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
                    // Simple lock = go back to require MPIN again (we keep token)
                    // For full lock we could clear a "unlocked" flag
                    navController.navigate("set_mpin") {
                        // force re-entry of MPIN by temporarily unsetting flag? 
                        // Better: use a local unlocked state
                    }
                }
            )
        }

        composable("folder") {
            val current = folderStack.lastOrNull()
            if (current == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                FolderScreen(
                    folder = current,
                    onBack = {
                        folderStack = folderStack.dropLast(1)
                        if (folderStack.isEmpty()) navController.popBackStack()
                    },
                    onOpenSubFolder = { sub ->
                        folderStack = folderStack + sub
                    }
                )
            }
        }

        composable("settings") {
            SettingsScreen(
                sessionStore = sessionStore,
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
            currentTheme = themeMode,
            onThemeChange = onThemeChange,
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            // Pill-shaped bottom bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                NavigationBar(
                    modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Star, null) },
                        label = { Text("Soon") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("Profile") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    sessionStore = sessionStore,
                    onOpenFolder = onOpenFolder,
                    onLockApp = onLockApp
                )
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Coming soon – you decide later")
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
