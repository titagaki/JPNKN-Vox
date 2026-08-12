package com.github.titagaki.jpnknvox.source

import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.toReceivedComment
import com.github.titagaki.jpnknvox.mqtt.MqttManager
import kotlinx.coroutines.CoroutineScope

/**
 * jpnkn 掲示板の取得先
 *
 * 既存の [MqttManager] を 1 取得先につき 1 つ持ち、
 * その通知を [CommentConnectorCallbacks] に流し替えるだけの薄い層。
 */
class JpnknConnector(
    override val source: CommentSource,
    coroutineScope: CoroutineScope,
    private val callbacks: CommentConnectorCallbacks
) : CommentConnector {

    private val mqttManager = MqttManager(
        coroutineScope = coroutineScope,
        onConnected = {
            callbacks.onSystemLog("${source.sourceId}: MQTT 接続成功")
            callbacks.onStatusChanged(source, SourceStatus.CONNECTED)
        },
        onDisconnected = { cause ->
            callbacks.onSystemLog("${source.sourceId}: MQTT 切断 (${cause?.message ?: "不明な理由"})")
            callbacks.onStatusChanged(source, SourceStatus.DISCONNECTED)
        },
        onMessageReceived = { message ->
            callbacks.onComment(message.toReceivedComment(source.uuid))
        },
        onError = { message ->
            callbacks.onSystemLog("${source.sourceId}: MQTT エラー: $message")
        }
    )

    override fun start() {
        val topic = AppConfig.Mqtt.createTopic(source.sourceId)
        callbacks.onStatusChanged(source, SourceStatus.WAITING)
        callbacks.onSystemLog("${source.sourceId}: 接続を開始します (トピック: $topic)")

        mqttManager.initialize()
        mqttManager.connect(topic)
    }

    override fun stop() {
        mqttManager.shutdown()
        callbacks.onSystemLog("${source.sourceId}: 接続を終了しました")
    }
}
