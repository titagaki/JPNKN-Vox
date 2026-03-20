package com.github.titagaki.jpnknvox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.JpnknMessage
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.mqtt.MqttManager
import com.github.titagaki.jpnknvox.overlay.OverlayManager
import com.github.titagaki.jpnknvox.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * JPNKN Vox のバックグラウンドサービス
 *
 * 各機能は専用のマネージャークラスに委譲し、
 * このサービスは統合と調整のみを担当する
 *
 * - Foreground Service として常駐し、OS によるタスクキルを防止
 * - MqttManager: MQTT 接続管理
 * - TtsManager: 音声合成管理
 * - OverlayManager: オーバーレイUI管理
 */
class JpnknVoxService : Service() {

    companion object {
        private const val TAG = "JpnknVoxService"
        const val EXTRA_BOARD_ID = "extra_board_id"
        const val EXTRA_MAX_MESSAGE_LENGTH = "extra_max_message_length"
        const val EXTRA_OVERLAY_ALPHA = "extra_overlay_alpha"
        const val EXTRA_OVERLAY_ENABLED = "extra_overlay_enabled"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // マネージャー
    private var ttsManager: TtsManager? = null
    private var mqttManager: MqttManager? = null
    private var overlayManager: OverlayManager? = null

    // 板 ID（Intent から設定される）
    private var boardId: String = ""

    // メッセージ最大文字数
    private var maxMessageLength: Int = 100

    // オーバーレイ背景の濃さ（0〜100 %）
    private var overlayAlpha: Int = 80

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        MessageManager.addSystemLog("サービスを初期化しています...")

        // 通知チャンネルを作成
        createNotificationChannel()

        // オーバーレイマネージャーを初期化
        overlayManager = OverlayManager(this).also {
            if (it.create(overlayAlpha)) {
                MessageManager.addSystemLog("オーバーレイを作成しました")
            }
        }

        // TTS マネージャーを初期化
        ttsManager = TtsManager(
            context = this,
            coroutineScope = serviceScope,
            onInitialized = { onTtsInitialized() },
            onError = { message ->
                MessageManager.addSystemLog("TTS エラー: $message")
            }
        )

        // MQTT マネージャーを初期化
        mqttManager = MqttManager(
            coroutineScope = serviceScope,
            onConnected = { onMqttConnected() },
            onDisconnected = { cause -> onMqttDisconnected(cause) },
            onMessageReceived = { message -> onMessageReceived(message) },
            onError = { message ->
                MessageManager.addSystemLog("MQTT エラー: $message")
            }
        ).also {
            it.initialize()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // 板 ID を Intent から取得（起動時のみ有効）
        intent?.getStringExtra(EXTRA_BOARD_ID)?.let {
            boardId = it
            Log.d(TAG, "Board ID set to: $boardId")
        }

        // 最大文字数を Intent から取得（起動時のみ有効）
        intent?.takeIf { it.hasExtra(EXTRA_MAX_MESSAGE_LENGTH) }?.let {
            maxMessageLength = it.getIntExtra(EXTRA_MAX_MESSAGE_LENGTH, 100)
            Log.d(TAG, "Max message length set to: $maxMessageLength")
        }

        // オーバーレイ濃さを Intent から取得し、生成済みオーバーレイに反映
        intent?.takeIf { it.hasExtra(EXTRA_OVERLAY_ALPHA) }?.let {
            overlayAlpha = it.getIntExtra(EXTRA_OVERLAY_ALPHA, 80)
            overlayManager?.updateAlpha(overlayAlpha)
            Log.d(TAG, "Overlay alpha set to: $overlayAlpha")
        }

        // オーバーレイ有効/無効
        intent?.takeIf { it.hasExtra(EXTRA_OVERLAY_ENABLED) }?.let {
            applyOverlayEnabled(it.getBooleanExtra(EXTRA_OVERLAY_ENABLED, true))
            Log.d(TAG, "Overlay enabled set to: ${it.getBooleanExtra(EXTRA_OVERLAY_ENABLED, true)}")
        }

        startForegroundServiceWithNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        MessageManager.addSystemLog("サービスを停止しています...")

        // オーバーレイを削除
        overlayManager?.remove()
        overlayManager = null

        // MQTT を切断
        mqttManager?.shutdown()
        mqttManager = null

        // TTS を解放
        ttsManager?.shutdown()
        ttsManager = null

        serviceScope.cancel()
        super.onDestroy()
    }

    // ========================================
    // 設定の即時反映
    // ========================================

    private fun applyOverlayEnabled(enabled: Boolean) {
        if (enabled) {
            if (overlayManager == null) {
                overlayManager = OverlayManager(this).also { it.create(overlayAlpha) }
                // 再作成後に現在の接続状態を反映
                val status = if (mqttManager?.connectionState == true) {
                    OverlayManager.ConnectionStatus.CONNECTED
                } else {
                    OverlayManager.ConnectionStatus.DISCONNECTED
                }
                overlayManager?.showStatus(status)
            }
        } else {
            overlayManager?.remove()
            overlayManager = null
        }
    }

    private fun applyMaxMessageLength(length: Int) {
        maxMessageLength = length
    }

    private fun applyOverlayAlpha(alpha: Int) {
        overlayAlpha = alpha
        overlayManager?.updateAlpha(alpha)
    }

    // ========================================
    // TTS コールバック
    // ========================================

    private fun onTtsInitialized() {
        MessageManager.addSystemLog("音声エンジンを初期化しました")
        ttsManager?.enqueue("じゃぱんくん-Vox 開始しました")

        // TTS 初期化後に MQTT 接続を開始（板 ID を使用）
        val topic = AppConfig.Mqtt.createTopic(boardId)
        MessageManager.addSystemLog("板 ID: $boardId (トピック: $topic)")
        mqttManager?.connect(topic)
    }

    // ========================================
    // MQTT コールバック
    // ========================================

    private fun onMqttConnected() {
        MessageManager.addSystemLog("MQTT 接続成功")
        overlayManager?.showStatus(OverlayManager.ConnectionStatus.CONNECTED)
    }

    private fun onMqttDisconnected(cause: Throwable?) {
        val message = cause?.message ?: "不明な理由"
        MessageManager.addSystemLog("MQTT 切断: $message")
        overlayManager?.showStatus(OverlayManager.ConnectionStatus.DISCONNECTED)
    }

    private fun onMessageReceived(message: JpnknMessage) {
        val text = message.extractMessage()

        // 最大文字数で省略
        val ttsText = if (text.length > maxMessageLength) {
            text.substring(0, maxMessageLength) + " 以下略"
        } else {
            text
        }

        MessageManager.addMessage(message)
        overlayManager?.updateMessage(text)
        ttsManager?.enqueue(ttsText)
    }

    // ========================================
    // 通知関連
    // ========================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AppConfig.Notification.CHANNEL_ID,
            AppConfig.Notification.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "JPNKN Vox のバックグラウンド動作用通知"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created")
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AppConfig.Notification.ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AppConfig.Notification.ID, notification)
        }

        Log.d(TAG, "Foreground service started with notification")
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConfig.Notification.CHANNEL_ID)
            .setContentTitle("JPNKN Vox")
            .setContentText("JPNKN 接続中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
