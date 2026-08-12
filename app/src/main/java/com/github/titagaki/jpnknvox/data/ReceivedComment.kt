package com.github.titagaki.jpnknvox.data

/**
 * 取得先から受け取ったコメント 1 件
 *
 * jpnkn のレスもツイキャスのコメントもこの形に寄せ、
 * サービスから先（読み上げ・オーバーレイ・ログ）は取得先の種別を意識しない。
 *
 * @param sourceUuid 受信元の [CommentSource.uuid]
 * @param no レス番号（jpnkn）。ツイキャスには相当するものが無いので空文字列
 * @param name 投稿者名
 * @param message 本文
 */
data class ReceivedComment(
    val sourceUuid: String,
    val no: String,
    val name: String,
    val message: String
)

/**
 * jpnkn の MQTT メッセージを [ReceivedComment] に変換する
 */
fun JpnknMessage.toReceivedComment(sourceUuid: String): ReceivedComment = ReceivedComment(
    sourceUuid = sourceUuid,
    no = no,
    name = extractName(),
    message = extractMessage()
)
