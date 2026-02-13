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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.github.titagaki.jpnknvox.ui.theme.JPNKNVoxTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // 通知権限リクエスト用のランチャー
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            startJpnknVoxService()
        } else {
            Log.w(TAG, "Notification permission denied")
            // 権限が拒否されてもサービスは起動（通知は表示されない可能性がある）
            startJpnknVoxService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JPNKNVoxTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // オーバーレイ権限をチェック
        checkOverlayPermission()

        // アプリ起動時に通知権限をチェックし、サービスを開始
        checkNotificationPermissionAndStartService()
    }

    /**
     * オーバーレイ権限をチェック
     */
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission not granted, requesting...")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Log.d(TAG, "Overlay permission already granted")
            }
        }
    }

    /**
     * 通知権限をチェックし、JpnknVoxService を開始
     */
    private fun checkNotificationPermissionAndStartService() {
        // Android 13 (API 33) 以降では POST_NOTIFICATIONS 権限が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 権限が既に許可されている
                    Log.d(TAG, "Notification permission already granted")
                    startJpnknVoxService()
                }
                else -> {
                    // 権限をリクエスト
                    Log.d(TAG, "Requesting notification permission")
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Android 12 以前では権限不要
            startJpnknVoxService()
        }
    }

    /**
     * JpnknVoxService を開始
     */
    private fun startJpnknVoxService() {
        val serviceIntent = Intent(this, JpnknVoxService::class.java)

        // Foreground Service として起動
        startForegroundService(serviceIntent)

        Log.d(TAG, "JpnknVoxService started")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JPNKNVoxTheme {
        Greeting("Android")
    }
}