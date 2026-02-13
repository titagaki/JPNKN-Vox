package com.github.titagaki.jpnknvox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * JPNKN Vox のバックグラウンドサービス
 *
 * - Foreground Service として常駐し、OS によるタスクキルを防止
 * - TextToSpeech を使用してコメントを読み上げ
 * - WebSocket でリアルタイムコメント受信
 * - 指数バックオフによる自動再接続
 * - Android 14 以降の制約に対応
 */
class JpnknVoxService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JpnknVoxService"
        private const val NOTIFICATION_CHANNEL_ID = "jpnkn_vox_channel"
        private const val NOTIFICATION_ID = 1

        // WebSocket 接続先（仮のURL - 実際の接続先に変更してください）
        private const val WEBSOCKET_URL = "wss://example.jpnkn.com/ws"

        // 再接続設定
        private const val INITIAL_RETRY_DELAY_MS = 1000L // 初回1秒
        private const val MAX_RETRY_DELAY_MS = 60000L // 最大60秒
        private const val MAX_RETRY_ATTEMPTS = 10 // 最大再試行回数
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    // WebSocket 関連
    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var isWebSocketConnected = false

    // 再接続制御
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var retryAttempts = 0
    private var isServiceRunning = true

    // 読み上げキュー
    private val speechQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // 通知チャンネルを作成
        createNotificationChannel()

        // OkHttpClient を初期化
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket は無制限
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Keep-alive
            .build()

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

        // サービス停止フラグを設定
        isServiceRunning = false

        // 再接続を停止
        reconnectHandler.removeCallbacksAndMessages(null)

        // WebSocket を切断
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        isWebSocketConnected = false

        // OkHttpClient をシャットダウン
        okHttpClient?.dispatcher?.executorService?.shutdown()
        okHttpClient?.connectionPool?.evictAll()
        okHttpClient = null

        // TTS リソースを解放
        tts?.let { textToSpeech ->
            textToSpeech.stop()
            textToSpeech.shutdown()
            Log.d(TAG, "TTS shutdown completed")
        }
        tts = null
        isTtsInitialized = false

        // キューをクリア
        speechQueue.clear()

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

                // 読み上げ完了を検知するリスナーを設定
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech completed: $utteranceId")
                        isSpeaking = false
                        // 次のキューを処理
                        processSpeechQueue()
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error: $utteranceId")
                        isSpeaking = false
                        // 次のキューを処理
                        processSpeechQueue()
                    }
                })

                // テスト発話
                enqueueSpeech("JPNKN-Vox 起動しました")

                // WebSocket 接続を開始
                connectWebSocket()
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            isTtsInitialized = false
        }
    }

    /**
     * テキストを読み上げキューに追加
     *
     * @param text 読み上げるテキスト
     */
    private fun enqueueSpeech(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping")
            return
        }

        speechQueue.offer(text)
        Log.d(TAG, "Enqueued speech: $text (Queue size: ${speechQueue.size})")

        // キューを処理
        processSpeechQueue()
    }

    /**
     * 読み上げキューを処理
     */
    private fun processSpeechQueue() {
        // 既に読み上げ中の場合はスキップ
        if (isSpeaking) {
            Log.d(TAG, "Already speaking, waiting...")
            return
        }

        // TTS が初期化されていない場合はスキップ
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS is not initialized yet")
            return
        }

        // キューから取り出し
        val text = speechQueue.poll()
        if (text != null) {
            isSpeaking = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
            Log.d(TAG, "Speaking: $text (Remaining in queue: ${speechQueue.size})")
        } else {
            Log.d(TAG, "Speech queue is empty")
        }
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

    /**
     * WebSocket に接続
     */
    private fun connectWebSocket() {
        if (!isServiceRunning) {
            Log.d(TAG, "Service is not running, skipping WebSocket connection")
            return
        }

        if (isWebSocketConnected) {
            Log.d(TAG, "WebSocket is already connected")
            return
        }

        Log.d(TAG, "Connecting to WebSocket: $WEBSOCKET_URL")

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .build()

        webSocket = okHttpClient?.newWebSocket(request, createWebSocketListener())
    }

    /**
     * WebSocket 再接続（指数バックオフアルゴリズム）
     */
    private fun scheduleReconnect() {
        if (!isServiceRunning) {
            Log.d(TAG, "Service is not running, skipping reconnection")
            return
        }

        // 最大再試行回数を超えた場合
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts reached ($MAX_RETRY_ATTEMPTS), giving up")
            // TODO: ユーザーに通知を表示
            return
        }

        // 指数バックオフで遅延時間を計算
        val delay = min(
            INITIAL_RETRY_DELAY_MS * 2.0.pow(retryAttempts.toDouble()).toLong(),
            MAX_RETRY_DELAY_MS
        )

        retryAttempts++
        Log.d(TAG, "Scheduling reconnection attempt $retryAttempts in ${delay}ms")

        reconnectHandler.postDelayed({
            Log.d(TAG, "Reconnection attempt $retryAttempts")
            connectWebSocket()
        }, delay)
    }

    /**
     * WebSocketListener を作成
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isWebSocketConnected = true
                retryAttempts = 0 // 成功したのでカウンターをリセット

                // 接続成功を通知
                enqueueSpeech("接続しました")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")

                // 受信したコメントを読み上げキューに追加
                enqueueSpeech(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
                isWebSocketConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                isWebSocketConnected = false

                // サービスが実行中であれば再接続を試みる
                if (isServiceRunning) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                isWebSocketConnected = false

                // サービスが実行中であれば再接続を試みる
                if (isServiceRunning) {
                    scheduleReconnect()
                }
            }
        }
    }
}

