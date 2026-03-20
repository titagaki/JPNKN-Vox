package com.github.titagaki.jpnknvox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.ui.navigation.Screen
import com.github.titagaki.jpnknvox.ui.screens.HomeScreen
import com.github.titagaki.jpnknvox.ui.screens.LogScreen
import com.github.titagaki.jpnknvox.ui.screens.SettingsScreen
import com.github.titagaki.jpnknvox.ui.theme.JPNKNVoxTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JPNKNVoxTheme {
                val vm: MainViewModel = viewModel()
                JpnknVoxApp(
                    viewModel = vm,
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    hasNotificationPermission = { checkNotificationPermission() },
                    hasOverlayPermission = { checkOverlayPermission() }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
}

// ========================================
// メインアプリ Composable
// ========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JpnknVoxApp(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasNotificationPermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val boardId by viewModel.boardId.collectAsState()

    // MessageManager の StateFlow を直接 collect
    val systemLogs by MessageManager.systemLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "JPNKN Vox",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (isServiceRunning) Color(0xFF4CAF50) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isServiceRunning) "稼働中" else "停止中",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isServiceRunning)
                                Color(0xFF4CAF50)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { checked ->
                                if (checked) viewModel.startService() else viewModel.stopService()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Log.route) {
                LogScreen(logMessages = systemLogs)
            }

            composable(Screen.Settings.route) {
                val isOverlayEnabled by viewModel.isOverlayEnabled.collectAsState()
                val maxMessageLength by viewModel.maxMessageLength.collectAsState()
                val overlayAlpha by viewModel.overlayAlpha.collectAsState()
                SettingsScreen(
                    boardId = boardId,
                    onBoardIdChange = { viewModel.updateBoardId(it) },
                    isServiceRunning = isServiceRunning,
                    hasNotificationPermission = hasNotificationPermission(),
                    hasOverlayPermission = hasOverlayPermission(),
                    isOverlayEnabled = isOverlayEnabled,
                    onOverlayEnabledChange = { viewModel.updateOverlayEnabled(it) },
                    overlayAlpha = overlayAlpha,
                    onOverlayAlphaChange = { viewModel.updateOverlayAlpha(it) },
                    maxMessageLength = maxMessageLength,
                    onMaxMessageLengthChange = { viewModel.updateMaxMessageLength(it) },
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
        }
    }
}