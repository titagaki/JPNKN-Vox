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
import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.data.ReceivedComment
import com.github.titagaki.jpnknvox.data.SettingsRepository
import com.github.titagaki.jpnknvox.data.SourceType
import com.github.titagaki.jpnknvox.overlay.OverlayManager
import com.github.titagaki.jpnknvox.source.CommentConnector
import com.github.titagaki.jpnknvox.source.CommentConnectorCallbacks
import com.github.titagaki.jpnknvox.source.JpnknConnector
import com.github.titagaki.jpnknvox.source.SourceStatus
import com.github.titagaki.jpnknvox.source.TwicasConnector
import com.github.titagaki.jpnknvox.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * JPNKN Vox のバックグラウンドサービス
 *
 * 各機能は専用のマネージャークラスに委譲し、
 * このサービスは統合と調整のみを担当する
 *
 * - Foreground Service として常駐し、OS によるタスクキルを防止
 * - CommentConnector: 取得先ごとの接続管理（jpnkn / ツイキャス）
 * - TtsManager: 音声合成管理。全取得先で 1 つのキューを共有する
 * - OverlayManager: オーバーレイUI管理
 */
class JpnknVoxService : Service() {

    companion object {
        private const val TAG = "JpnknVoxService"
        const val EXTRA_SOURCES = "extra_sources"
        const val EXTRA_MAX_MESSAGE_LENGTH = "extra_max_message_length"
        const val EXTRA_OVERLAY_ALPHA = "extra_overlay_alpha"
        const val EXTRA_OVERLAY_TEXT_SIZE = "extra_overlay_text_size"
        const val EXTRA_OVERLAY_ENABLED = "extra_overlay_enabled"
        const val EXTRA_SPEECH_RATE = "extra_speech_rate"
        const val EXTRA_SPEECH_VOLUME = "extra_speech_volume"

        /** サービスが現在稼働中かどうかを示すフラグ */
        var isRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // マネージャー
    private var ttsManager: TtsManager? = null
    private var overlayManager: OverlayManager? = null

    /** 稼働中の接続。キーは [CommentSource.uuid]。メインスレッドからのみ触る */
    private val connectors = LinkedHashMap<String, CommentConnector>()

    /**
     * 取得先ごとの接続状態。オーバーレイに出す状態をまとめるのに使う
     *
     * 各取得先の通信スレッドから書き込まれるため ConcurrentHashMap で持つ。
     */
    private val statuses = ConcurrentHashMap<String, SourceStatus>()

    /** 設定された取得先。読み上げ時に ID と識別色を引くのに使う（通信スレッドから読む） */
    private val sources = ConcurrentHashMap<String, CommentSource>()

    // メッセージ最大文字数
    private var maxMessageLength: Int = AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH

    // オーバーレイ背景の濃さ（0〜100 %）
    private var overlayAlpha: Int = AppConfig.Overlay.DEFAULT_ALPHA

    // オーバーレイの文字サイズ（sp）
    private var overlayTextSize: Int = AppConfig.Overlay.DEFAULT_TEXT_SIZE

    // 話す速度（100 で等倍の百分率）
    private var speechRate: Int = AppConfig.Tts.DEFAULT_SPEECH_RATE

    // 読み上げ音量（0〜100 %）
    private var speechVolume: Int = AppConfig.Tts.DEFAULT_VOLUME

    /** TTS の初期化が済むまで接続を待たせるための、保留中の取得先 */
    private var pendingSources: List<CommentSource>? = null

    private val connectorCallbacks = object : CommentConnectorCallbacks {
        override fun onStatusChanged(source: CommentSource, status: SourceStatus) {
            statuses[source.uuid] = status
            MessageManager.updateSourceStatus(source.uuid, status)
            overlayManager?.showStatus(OverlayManager.aggregateStatus(statuses.values))
        }

        override fun onComment(comment: ReceivedComment) {
            onCommentReceived(comment)
        }

        override fun onSystemLog(message: String) {
            MessageManager.addSystemLog(message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "Service onCreate")

        MessageManager.addSystemLog("サービスを初期化しています...")

        // 通知チャンネルを作成
        createNotificationChannel()

        // DataStore から保存済みの設定を読み込む
        val settingsRepository = SettingsRepository(this)
        runBlocking {
            overlayAlpha = settingsRepository.overlayAlphaFlow.first()
            overlayTextSize = settingsRepository.overlayTextSizeFlow.first()
            speechRate = settingsRepository.speechRateFlow.first()
            speechVolume = settingsRepository.speechVolumeFlow.first()
        }

        overlayManager = OverlayManager(this).also {
            if (it.create(overlayAlpha, overlayTextSize)) {
                MessageManager.addSystemLog("オーバーレイを作成しました")
            }
        }

        // TTS マネージャーを初期化
        ttsManager = TtsManager(
            context = this,
            coroutineScope = serviceScope,
            speechRate = speechRate,
            volume = speechVolume,
            onInitialized = { onTtsInitialized() },
            onError = { message ->
                MessageManager.addSystemLog("TTS エラー: $message")
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")

        // 最大文字数を Intent から取得
        intent?.takeIf { it.hasExtra(EXTRA_MAX_MESSAGE_LENGTH) }?.let {
            maxMessageLength =
                it.getIntExtra(EXTRA_MAX_MESSAGE_LENGTH, AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH)
            Log.d(TAG, "Max message length set to: $maxMessageLength")
        }

        // オーバーレイ濃さを Intent から取得し、生成済みオーバーレイに反映
        intent?.takeIf { it.hasExtra(EXTRA_OVERLAY_ALPHA) }?.let {
            overlayAlpha = it.getIntExtra(EXTRA_OVERLAY_ALPHA, AppConfig.Overlay.DEFAULT_ALPHA)
            overlayManager?.updateAlpha(overlayAlpha)
            Log.d(TAG, "Overlay alpha set to: $overlayAlpha")
        }

        // オーバーレイ文字サイズを Intent から取得し、生成済みオーバーレイに反映
        intent?.takeIf { it.hasExtra(EXTRA_OVERLAY_TEXT_SIZE) }?.let {
            overlayTextSize =
                it.getIntExtra(EXTRA_OVERLAY_TEXT_SIZE, AppConfig.Overlay.DEFAULT_TEXT_SIZE)
            overlayManager?.updateTextSize(overlayTextSize)
            Log.d(TAG, "Overlay text size set to: $overlayTextSize")
        }

        // 話す速度を Intent から取得し、TTS に反映
        intent?.takeIf { it.hasExtra(EXTRA_SPEECH_RATE) }?.let {
            speechRate = it.getIntExtra(EXTRA_SPEECH_RATE, AppConfig.Tts.DEFAULT_SPEECH_RATE)
            ttsManager?.setSpeechRate(speechRate)
            Log.d(TAG, "Speech rate set to: $speechRate")
        }

        // 読み上げ音量を Intent から取得し、TTS に反映
        intent?.takeIf { it.hasExtra(EXTRA_SPEECH_VOLUME) }?.let {
            speechVolume = it.getIntExtra(EXTRA_SPEECH_VOLUME, AppConfig.Tts.DEFAULT_VOLUME)
            ttsManager?.setVolume(speechVolume)
            Log.d(TAG, "Speech volume set to: $speechVolume")
        }

        // オーバーレイ有効/無効
        intent?.takeIf { it.hasExtra(EXTRA_OVERLAY_ENABLED) }?.let {
            applyOverlayEnabled(it.getBooleanExtra(EXTRA_OVERLAY_ENABLED, true))
            Log.d(TAG, "Overlay enabled set to: ${it.getBooleanExtra(EXTRA_OVERLAY_ENABLED, true)}")
        }

        // 取得先。開始・追加・編集・削除のいずれもここを通り、差分だけ反映する
        intent?.getStringExtra(EXTRA_SOURCES)?.let { json ->
            applySources(CommentSource.listFromJson(json))
        }

        startForegroundServiceWithNotification()

        // START_NOT_STICKY: プロセスが落ちても OS に勝手に作り直させない。
        // 再生成時は Intent が null になり取得先を失うため、開始・停止は明示的な操作に限る。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * タスク一覧からアプリがスワイプで終了されたとき、サービスも停止する
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed, stopping service")
        MessageManager.addSystemLog("アプリが終了したためサービスを停止します")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        Log.d(TAG, "Service onDestroy")
        MessageManager.addSystemLog("サービスを停止しています...")

        // オーバーレイを削除
        overlayManager?.remove()
        overlayManager = null

        // 取得先をすべて切断
        connectors.values.forEach { it.stop() }
        connectors.clear()
        statuses.clear()
        sources.clear()
        MessageManager.clearSourceStatuses()

        // TTS を解放
        ttsManager?.shutdown()
        ttsManager = null

        serviceScope.cancel()
        super.onDestroy()
    }

    // ========================================
    // 取得先の管理
    // ========================================

    /**
     * 設定された取得先に合わせて、稼働中の接続を差分で入れ替える
     *
     * 消えたものは切断し、増えたものは接続する。
     * 接続先（種別・ID）が変わったものは張り直す。識別色だけの変更では
     * 接続をそのままにする（[CommentSource.connectsTo]）。
     *
     * TTS の初期化前に呼ばれた場合は、初期化が済んでから接続する。
     */
    private fun applySources(newSources: List<CommentSource>) {
        sources.clear()
        newSources.forEach { sources[it.uuid] = it }

        if (ttsManager?.isReady != true) {
            // 開始直後はここを通る。TTS の準備ができてから繋ぐ
            pendingSources = newSources
            Log.d(TAG, "TTS is not ready yet, ${newSources.size} source(s) pending")
            return
        }
        pendingSources = null

        val newByUuid = newSources.associateBy { it.uuid }

        // 消えた取得先・接続先が変わった取得先を止める
        connectors.entries.toList().forEach { (uuid, connector) ->
            val updated = newByUuid[uuid]
            if (updated == null || !updated.connectsTo(connector.source)) {
                connector.stop()
                connectors.remove(uuid)
                statuses.remove(uuid)
                MessageManager.removeSourceStatus(uuid)
            }
        }

        // 増えた取得先を繋ぐ
        newSources.forEach { source ->
            if (!connectors.containsKey(source.uuid)) {
                createConnector(source).also {
                    connectors[source.uuid] = it
                    it.start()
                }
            }
        }

        overlayManager?.showStatus(OverlayManager.aggregateStatus(statuses.values))
        Log.d(TAG, "Applied ${newSources.size} source(s), ${connectors.size} connector(s) running")
    }

    private fun createConnector(source: CommentSource): CommentConnector = when (source.type) {
        SourceType.JPNKN -> JpnknConnector(source, serviceScope, connectorCallbacks)
        SourceType.TWICAS -> TwicasConnector(source, serviceScope, connectorCallbacks)
    }

    // ========================================
    // 設定の即時反映
    // ========================================

    private fun applyOverlayEnabled(enabled: Boolean) {
        if (enabled) {
            if (overlayManager == null) {
                overlayManager = OverlayManager(this).also {
                    it.create(overlayAlpha, overlayTextSize)
                }
                // 再作成後に現在の接続状態を反映
                overlayManager?.showStatus(OverlayManager.aggregateStatus(statuses.values))
            }
        } else {
            overlayManager?.remove()
            overlayManager = null
        }
    }

    // ========================================
    // TTS コールバック
    // ========================================

    private fun onTtsInitialized() {
        MessageManager.addSystemLog("音声エンジンを初期化しました")
        ttsManager?.enqueue("じゃぱんくん-Vox 開始しました")

        // TTS 初期化前に受け取っていた取得先があれば、ここで接続する
        pendingSources?.let { applySources(it) }
    }

    // ========================================
    // コメント受信
    // ========================================

    private fun onCommentReceived(comment: ReceivedComment) {
        val source = sources[comment.sourceUuid] ?: return
        val text = comment.message
        if (text.isBlank()) return

        // 最大文字数で省略
        val ttsText = if (text.length > maxMessageLength) {
            text.substring(0, maxMessageLength) + " 以下略"
        } else {
            text
        }

        MessageManager.addMessage(comment, source)

        // 取得先が 1 つだけなら、どこから来たかは自明なので出さない
        val overlaySourceId = if (sources.size > 1) source.sourceId else null

        // オーバーレイは受信時ではなく読み上げ開始時に更新する。
        // 読み上げが詰まっているとき、表示だけ先に進んで
        // 聞こえている内容と食い違うのを防ぐ
        ttsManager?.enqueue(ttsText) {
            overlayManager?.updateMessage(text, overlaySourceId)
        }
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
