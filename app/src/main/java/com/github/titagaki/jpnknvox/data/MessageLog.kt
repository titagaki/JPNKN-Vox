package com.github.titagaki.jpnknvox.data

import java.util.UUID

/**
 * UI 表示用のメッセージログデータクラス
 *
 * JpnknMessage から必要な情報を抽出し、UI に最適化した形で保持する。
 */
data class MessageLog(
    val id: String,
    val no: String,
    val name: String,
    val message: String,
    val timestamp: Long
)

/**
 * JpnknMessage から MessageLog を生成する拡張関数
 */
fun JpnknMessage.toLog(): MessageLog {
    return MessageLog(
        id = UUID.randomUUID().toString(),
        no = no,
        name = extractName(),
        message = extractMessage(),
        timestamp = System.currentTimeMillis()
    )
}

