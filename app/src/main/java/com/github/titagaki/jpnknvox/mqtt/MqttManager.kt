package com.github.titagaki.jpnknvox.mqtt

import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.JpnknMessage
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.util.concurrent.TimeUnit

/**
 * MQTT 接続管理クラス
 *
 * - HiveMQ MQTT Client を使用した接続管理
 * - 自動再接続
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
            .automaticReconnect()
                .initialDelay(AppConfig.Mqtt.INITIAL_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                .maxDelay(AppConfig.Mqtt.MAX_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
                .applyAutomaticReconnect()
            .addConnectedListener {
                Log.d(TAG, "Connected listener triggered")
                isConnected = true
                onConnected()
            }
            .addDisconnectedListener { context ->
                Log.d(TAG, "Disconnected listener triggered: ${context.cause}")
                isConnected = false
                onDisconnected(context.cause)
            }
            .buildAsync()

        Log.d(TAG, "MQTT client initialized with ID: $clientId")
    }

    /**
     * MQTT サーバーに接続
     */
    fun connect() {
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
            ?.cleanSession(false)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Connection failed", throwable)
                    isConnected = false
                    onError("接続失敗: ${throwable.message}")
                } else {
                    Log.d(TAG, "Connection successful")
                    isConnected = true
                    subscribe(currentTopic)
                }
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
        disconnect()
        client = null
        Log.d(TAG, "MQTT manager shutdown")
    }
}

