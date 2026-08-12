package com.github.titagaki.jpnknvox.data

import java.util.UUID

/**
 * UI 表示用のメッセージログデータクラス
 *
 * 受信したコメントから必要な情報を抽出し、UI に最適化した形で保持する。
 *
 * @param sourceColor どの取得先から来たかを示す識別色（ARGB）
 */
data class MessageLog(
    val id: String,
    val no: String,
    val name: String,
    val message: String,
    val timestamp: Long,
    val sourceColor: Int
)

/**
 * 受信したコメントから MessageLog を生成する拡張関数
 *
 * @param source 受信元の取得先
 */
fun ReceivedComment.toLog(source: CommentSource): MessageLog {
    return MessageLog(
        id = UUID.randomUUID().toString(),
        no = no,
        name = name,
        message = message,
        timestamp = System.currentTimeMillis(),
        sourceColor = source.color
    )
}
