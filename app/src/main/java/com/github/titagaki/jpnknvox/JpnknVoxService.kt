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
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * JPNKN Vox のバックグラウンドサービス
 *
 * - Foreground Service として常駐し、OS によるタスクキルを防止
 * - TextToSpeech を使用してコメントを読み上げ
 * - Android 14 以降の制約に対応
 */
class JpnknVoxService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JpnknVoxService"
        private const val NOTIFICATION_CHANNEL_ID = "jpnkn_vox_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // 通知チャンネルを作成
        createNotificationChannel()

        // TextToSpeech を初期化
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // Foreground Service として起動
        startForegroundServiceWithNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // バインドは使用しない
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        // TTS リソースを解放
        tts?.let { textToSpeech ->
            textToSpeech.stop()
            textToSpeech.shutdown()
            Log.d(TAG, "TTS shutdown completed")
        }
        tts = null
        isTtsInitialized = false

        super.onDestroy()
    }

    /**
     * TextToSpeech 初期化完了時のコールバック
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 日本語に設定
            val result = tts?.setLanguage(Locale.JAPANESE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Japanese language is not supported")
                isTtsInitialized = false
            } else {
                Log.d(TAG, "TTS initialized successfully")
                isTtsInitialized = true

                // テスト発話
                speak("JPNKN-Vox 起動しました")
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            isTtsInitialized = false
        }
    }

    /**
     * テキストを読み上げる
     *
     * @param text 読み上げるテキスト
     */
    fun speak(text: String) {
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS is not initialized yet")
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
        Log.d(TAG, "Speaking: $text")
    }

    /**
     * 通知チャンネルを作成
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "JPNKN Vox サービス",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "JPNKN Vox のバックグラウンド動作用通知"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created")
    }

    /**
     * Foreground Service を通知付きで起動
     *
     * Android 14 以降では startForeground() に foregroundServiceType を指定する必要がある
     */
    private fun startForegroundServiceWithNotification() {
        val notification = createNotification()

        // Android 14 (API 34) 以降では foregroundServiceType を明示的に指定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Log.d(TAG, "Foreground service started with notification")
    }

    /**
     * 通知を作成
     */
    private fun createNotification(): Notification {
        // 通知タップ時に MainActivity を開く Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("JPNKN Vox")
            .setContentText("読み上げサービス実行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

