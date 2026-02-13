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
import com.github.titagaki.jpnknvox.mqtt.MqttManager
import com.github.titagaki.jpnknvox.overlay.OverlayManager
import com.github.titagaki.jpnknvox.tts.TtsManager
import com.github.titagaki.jpnknvox.util.LogBroadcaster

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
 * - LogBroadcaster: ログ配信
 */
class JpnknVoxService : Service() {

    companion object {
        private const val TAG = "JpnknVoxService"
    }

    // マネージャー
    private lateinit var logBroadcaster: LogBroadcaster
    private var ttsManager: TtsManager? = null
    private var mqttManager: MqttManager? = null
    private var overlayManager: OverlayManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // ログブロードキャスターを初期化
        logBroadcaster = LogBroadcaster(this)
        logBroadcaster.info("サービスを初期化しています...")

        // 通知チャンネルを作成
        createNotificationChannel()

        // オーバーレイマネージャーを初期化
        overlayManager = OverlayManager(this).also {
            if (it.create()) {
                logBroadcaster.info("オーバーレイを作成しました")
            }
        }

        // TTS マネージャーを初期化
        ttsManager = TtsManager(
            context = this,
            onInitialized = { onTtsInitialized() },
            onError = { message -> logBroadcaster.error(message) }
        )

        // MQTT マネージャーを初期化
        mqttManager = MqttManager(
            onConnected = { onMqttConnected() },
            onDisconnected = { cause -> onMqttDisconnected(cause) },
            onMessageReceived = { message -> onMessageReceived(message) },
            onError = { message -> logBroadcaster.error(message) }
        ).also {
            it.initialize()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        startForegroundServiceWithNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        // オーバーレイを削除
        overlayManager?.remove()
        overlayManager = null

        // MQTT を切断
        mqttManager?.shutdown()
        mqttManager = null

        // TTS を解放
        ttsManager?.shutdown()
        ttsManager = null

        super.onDestroy()
    }

    // ========================================
    // TTS コールバック
    // ========================================

    private fun onTtsInitialized() {
        logBroadcaster.info("音声エンジンを初期化しました")
        ttsManager?.enqueue("JPNKN-Vox 起動しました")

        // TTS 初期化後に MQTT 接続を開始
        mqttManager?.connect()
    }

    // ========================================
    // MQTT コールバック
    // ========================================

    private fun onMqttConnected() {
        logBroadcaster.info("MQTT 接続成功")
        overlayManager?.showConnected()
        ttsManager?.enqueue("接続しました")
    }

    private fun onMqttDisconnected(cause: Throwable?) {
        val message = cause?.message ?: "不明な理由"
        logBroadcaster.warn("MQTT 切断: $message")
        overlayManager?.showDisconnected()
    }

    private fun onMessageReceived(message: JpnknMessage) {
        val text = message.extractMessage()
        logBroadcaster.info("メッセージ受信: $text")
        overlayManager?.updateMessage(text)
        ttsManager?.enqueue(text)
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

