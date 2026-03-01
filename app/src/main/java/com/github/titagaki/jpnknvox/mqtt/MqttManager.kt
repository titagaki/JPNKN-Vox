package com.github.titagaki.jpnknvox.mqtt

import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.JpnknMessage
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.*

/**
 * MQTT 接続管理クラス
 *
 * - HiveMQ MQTT Client を使用した接続管理
 * - 切断時の自動再接続（手動リトライ方式、認証情報を毎回送信）
 * - メッセージの受信とパース
 */
class MqttManager(
    private val onConnected: () -> Unit,
    private val onDisconnected: (Throwable?) -> Unit,
    private val onMessageReceived: (JpnknMessage) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "MqttManager"
    }

    private var client: Mqtt3AsyncClient? = null
    private var isConnected = false
    private var currentTopic: String = AppConfig.Mqtt.DEFAULT_TOPIC

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var isShuttingDown = false

    /**
     * 接続状態を取得
     */
    val connectionState: Boolean
        get() = isConnected

    /**
     * MQTT クライアントを初期化
     */
    fun initialize() {
        val clientId = "${AppConfig.Mqtt.CLIENT_ID_PREFIX}_${System.currentTimeMillis()}"

        client = MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(AppConfig.Mqtt.SERVER_HOST)
            .serverPort(AppConfig.Mqtt.SERVER_PORT)
            .buildAsync()

        Log.d(TAG, "MQTT client initialized with ID: $clientId")
    }

    /**
     * MQTT サーバーに接続
     *
     * @param topic 購読するトピック（省略時はデフォルトトピック）
     */
    fun connect(topic: String = AppConfig.Mqtt.DEFAULT_TOPIC) {
        currentTopic = topic
        isShuttingDown = false
        doConnect()
    }

    /**
     * 実際の接続処理（再接続時も同じメソッドを使用）
     */
    private fun doConnect() {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }

        Log.d(TAG, "Connecting to ${AppConfig.Mqtt.SERVER_HOST}:${AppConfig.Mqtt.SERVER_PORT}")

        client?.connectWith()
            ?.simpleAuth()
            ?.username(AppConfig.Mqtt.USERNAME)
            ?.password(AppConfig.Mqtt.PASSWORD.toByteArray())
            ?.applySimpleAuth()
            ?.keepAlive(60)
            ?.cleanSession(true)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Connection failed", throwable)
                    isConnected = false
                    onError("接続失敗: ${throwable.message}")
                    onDisconnected(throwable)
                    scheduleReconnect()
                } else {
                    Log.d(TAG, "Connection successful")
                    isConnected = true
                    onConnected()
                    subscribe(currentTopic)
                }
            }
    }

    /**
     * 再接続をスケジュール（指数バックオフ）
     */
    private var retryCount = 0

    private fun scheduleReconnect() {
        if (isShuttingDown) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = minOf(
                AppConfig.Mqtt.INITIAL_RETRY_DELAY_MS * (1L shl retryCount.coerceAtMost(6)),
                AppConfig.Mqtt.MAX_RETRY_DELAY_MS
            )
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt ${retryCount + 1})")
            delay(delayMs)
            retryCount++
            if (!isShuttingDown) doConnect()
        }
    }

    /**
     * トピックを購読
     *
     * @param topic 購読するトピック
     */
    fun subscribe(topic: String) {
        currentTopic = topic

        client?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(MqttQos.AT_MOST_ONCE)
            ?.callback { publish: Mqtt3Publish ->
                handleMessage(publish)
            }
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Subscribe failed: $topic", throwable)
                    onError("トピック購読失敗: ${throwable.message}")
                } else {
                    Log.d(TAG, "Subscribed to: $topic")
                    retryCount = 0
                    // 購読成功後、切断を監視するためにポーリング
                    startDisconnectWatcher()
                }
            }
    }

    /**
     * 切断を検知して再接続をトリガーするウォッチャー
     */
    private var watcherJob: Job? = null

    private fun startDisconnectWatcher() {
        watcherJob?.cancel()
        watcherJob = scope.launch {
            while (isActive && !isShuttingDown) {
                delay(5000)
                val clientConnected = client?.state?.isConnected == true
                if (!clientConnected && !isShuttingDown) {
                    Log.d(TAG, "Disconnect detected by watcher")
                    if (isConnected) {
                        isConnected = false
                        onDisconnected(null)
                    }
                    scheduleReconnect()
                    break
                }
            }
        }
    }

    /**
     * 受信メッセージを処理
     */
    private fun handleMessage(publish: Mqtt3Publish) {
        val payload = String(publish.payloadAsBytes)
        Log.d(TAG, "Message received on ${publish.topic}: $payload")

        val message = JpnknMessage.fromJson(payload)
        if (message != null) {
            val text = message.extractMessage()
            if (text.isNotEmpty()) {
                onMessageReceived(message)
            } else {
                Log.w(TAG, "Empty message after extraction")
            }
        } else {
            Log.e(TAG, "Failed to parse message: $payload")
            onError("JSONパースエラー")
        }
    }

    /**
     * 切断
     */
    fun disconnect() {
        client?.let { c ->
            if (c.state.isConnected) {
                c.disconnect().whenComplete { _, throwable ->
                    if (throwable != null) {
                        Log.e(TAG, "Disconnect error", throwable)
                    } else {
                        Log.d(TAG, "Disconnected")
                    }
                }
            }
        }
        isConnected = false
    }

    /**
     * リソースを解放
     */
    fun shutdown() {
        isShuttingDown = true
        reconnectJob?.cancel()
        watcherJob?.cancel()
        scope.cancel()
        disconnect()
        client = null
        Log.d(TAG, "MQTT manager shutdown")
    }
}

