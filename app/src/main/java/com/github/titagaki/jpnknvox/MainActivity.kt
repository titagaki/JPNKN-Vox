package com.github.titagaki.jpnknvox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.JpnknMessage
import com.github.titagaki.jpnknvox.ui.navigation.Screen
import com.github.titagaki.jpnknvox.ui.screens.HomeScreen
import com.github.titagaki.jpnknvox.ui.screens.LogScreen
import com.github.titagaki.jpnknvox.ui.screens.SettingsScreen
import com.github.titagaki.jpnknvox.ui.theme.JPNKNVoxTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ViewModel への参照（ブロードキャストレシーバーからアクセス用）
    private var viewModelRef: MainViewModel? = null

    // 通知権限リクエスト用のランチャー
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.w(TAG, "Notification permission denied")
        }
    }

    // ログ受信用のブロードキャストレシーバー
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.Broadcast.ACTION_LOG_UPDATE) {
                val message = intent.getStringExtra(AppConfig.Broadcast.EXTRA_LOG_MESSAGE)
                if (message != null) {
                    viewModelRef?.addLogMessage(message)
                }
            }
        }
    }

    // ポスト受信用のブロードキャストレシーバー
    private val postReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConfig.Broadcast.ACTION_POST_RECEIVED) {
                val json = intent.getStringExtra(AppConfig.Broadcast.EXTRA_POST_JSON)
                if (json != null) {
                    val message = JpnknMessage.fromJson(json)
                    if (message != null) {
                        viewModelRef?.addPost(message)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ブロードキャストレシーバーを登録
        registerReceivers()

        setContent {
            JPNKNVoxTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logReceiver)
        unregisterReceiver(postReceiver)
    }

    private fun registerReceivers() {
        val logFilter = IntentFilter(AppConfig.Broadcast.ACTION_LOG_UPDATE)
        val postFilter = IntentFilter(AppConfig.Broadcast.ACTION_POST_RECEIVED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, logFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(postReceiver, postFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ContextCompat.registerReceiver(
                this, logReceiver, logFilter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ContextCompat.registerReceiver(
                this, postReceiver, postFilter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
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

    val isServiceRunning by viewModel.isServiceRunning
    val boardId by viewModel.boardId

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
                    // サービス稼働状態インジケーター
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // ステータスドット
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

                        // サービス開始/停止スイッチ
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.startService()
                                } else {
                                    viewModel.stopService()
                                }
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
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
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
                LogScreen(
                    logMessages = viewModel.logMessages
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    boardId = boardId,
                    onBoardIdChange = { viewModel.updateBoardId(it) },
                    isServiceRunning = isServiceRunning,
                    hasNotificationPermission = hasNotificationPermission(),
                    hasOverlayPermission = hasOverlayPermission(),
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
        }
    }
}