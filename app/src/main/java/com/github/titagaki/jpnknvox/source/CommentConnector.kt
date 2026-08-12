package com.github.titagaki.jpnknvox.source

import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.ReceivedComment

/**
 * 取得先ごとの接続状態
 *
 * @param label オーバーレイや設定画面に出す文言
 * @param isHealthy 対処が要らない状態かどうか。オーバーレイの色の集約に使う
 */
enum class SourceStatus(val label: String, val isHealthy: Boolean) {
    /** 開始直後。まだ接続を試みていない */
    WAITING("待機", isHealthy = true),

    CONNECTED("接続中", isHealthy = true),

    /**
     * ツイキャス専用。ユーザーは正しいが配信が始まっていない
     *
     * 異常ではなく待っているだけなので、正常扱いにする。
     */
    WAITING_BROADCAST("配信待ち", isHealthy = true),

    DISCONNECTED("再接続中", isHealthy = false),

    ERROR("エラー", isHealthy = false);

    companion object {
        /**
         * 取得先ごとの状態を、全体を代表する 1 つの状態にまとめる
         *
         * オーバーレイには色を 1 つしか出せないので、対処が要る取得先が
         * 1 つでもあればそちらを優先して見せる。
         * ツイキャスの [WAITING_BROADCAST] は待っているだけなので正常扱い。
         *
         * @return 代表する状態。取得先が 1 つも無い場合は null
         */
        fun aggregate(statuses: Collection<SourceStatus>): SourceStatus? = when {
            statuses.isEmpty() -> null
            statuses.contains(ERROR) -> ERROR
            statuses.contains(DISCONNECTED) -> DISCONNECTED
            statuses.all { it == WAITING } -> WAITING
            else -> CONNECTED
        }
    }
}

/**
 * 1 つの取得先からコメントを受け取る接続
 *
 * jpnkn は MQTT、ツイキャスは WebSocket と手段が違うため、
 * サービスからはこのインターフェース越しに同じ扱いをする。
 */
interface CommentConnector {

    /** この接続が担当する取得先 */
    val source: CommentSource

    /** 接続を開始する */
    fun start()

    /** 接続を終了し、リソースを解放する */
    fun stop()
}

/**
 * [CommentConnector] からサービスへの通知
 */
interface CommentConnectorCallbacks {
    fun onStatusChanged(source: CommentSource, status: SourceStatus)
    fun onComment(comment: ReceivedComment)
    fun onSystemLog(message: String)
}
