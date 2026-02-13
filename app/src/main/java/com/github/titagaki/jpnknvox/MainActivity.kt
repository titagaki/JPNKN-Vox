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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.ui.theme.JPNKNVoxTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // サービスの稼働状態
    private var isServiceRunning by mutableStateOf(false)

    // ログメッセージのリスト
    private val logMessages = mutableStateListOf<String>()

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
                    addLogMessage(message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ログレシーバーを登録
        val filter = IntentFilter(AppConfig.Broadcast.ACTION_LOG_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // API 33 未満では Context.RECEIVER_NOT_EXPORTED が使用できないため、
            // 通常の registerReceiver を使用
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ContextCompat.registerReceiver(
                this,
                logReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        setContent {
            JPNKNVoxTheme {
                DashboardScreen(
                    isServiceRunning = isServiceRunning,
                    logMessages = logMessages,
                    onStartService = { startService() },
                    onStopService = { stopService() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    hasNotificationPermission = { checkNotificationPermission() },
                    hasOverlayPermission = { checkOverlayPermission() }
                )
            }
        }

        // 初期ログを追加
        addLogMessage("アプリケーションを起動しました")
    }

    override fun onDestroy() {
        super.onDestroy()
        // ログレシーバーを解除
        unregisterReceiver(logReceiver)
    }

    /**
     * サービスを開始
     */
    private fun startService() {
        val serviceIntent = Intent(this, JpnknVoxService::class.java)
        startForegroundService(serviceIntent)
        isServiceRunning = true
        addLogMessage("サービスを開始しました")
        Log.d(TAG, "JpnknVoxService started")
    }

    /**
     * サービスを停止
     */
    private fun stopService() {
        val serviceIntent = Intent(this, JpnknVoxService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        addLogMessage("サービスを停止しました")
        Log.d(TAG, "JpnknVoxService stopped")
    }

    /**
     * 通知権限をリクエスト
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            addLogMessage("通知権限をリクエストしました")
        }
    }

    /**
     * オーバーレイ権限をリクエスト
     */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        addLogMessage("オーバーレイ権限の設定画面を開きました")
    }

    /**
     * 通知権限を確認
     */
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 以前では権限不要
        }
    }

    /**
     * オーバーレイ権限を確認
     */
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    /**
     * ログメッセージを追加
     */
    private fun addLogMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        logMessages.add(0, "[$timestamp] $message")
        // 最大100件まで保持
        if (logMessages.size > 100) {
            logMessages.removeAt(logMessages.size - 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isServiceRunning: Boolean,
    logMessages: List<String>,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasNotificationPermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JPNKN Vox Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // サービス制御セクション
            ServiceControlSection(
                isServiceRunning = isServiceRunning,
                onStartService = onStartService,
                onStopService = onStopService
            )

            // 権限状態セクション
            PermissionStatusSection(
                hasNotificationPermission = hasNotificationPermission(),
                hasOverlayPermission = hasOverlayPermission(),
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestOverlayPermission = onRequestOverlayPermission
            )

            // ログ表示セクション
            LogDisplaySection(logMessages = logMessages)
        }
    }
}

@Composable
fun ServiceControlSection(
    isServiceRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "サービス制御",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ステータスインジケーター
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isServiceRunning) Color.Green else Color.Gray,
                            shape = RoundedCornerShape(6.dp)
                        )
                )

                Text(
                    text = if (isServiceRunning) "稼働中" else "停止中",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartService,
                    enabled = !isServiceRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("開始")
                }

                Button(
                    onClick = onStopService,
                    enabled = isServiceRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止")
                }
            }
        }
    }
}

@Composable
fun PermissionStatusSection(
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "権限状態",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 通知権限
            PermissionItem(
                permissionName = "通知権限",
                isGranted = hasNotificationPermission,
                onRequestPermission = onRequestNotificationPermission
            )

            HorizontalDivider()

            // オーバーレイ権限
            PermissionItem(
                permissionName = "オーバーレイ権限",
                isGranted = hasOverlayPermission,
                onRequestPermission = onRequestOverlayPermission
            )
        }
    }
}

@Composable
fun PermissionItem(
    permissionName: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) Color.Green else Color.Red,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = permissionName,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("設定", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun LogDisplaySection(logMessages: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "接続ログ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (logMessages.isEmpty()) {
                        Text(
                            text = "ログはまだありません",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    } else {
                        logMessages.forEach { message ->
                            Text(
                                text = message,
                                color = Color(0xFF00FF00),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}