package com.github.titagaki.jpnknvox

import org.json.JSONObject

/**
 * jpnkn MQTT メッセージのデータクラス
 *
 * JSON スキーマに基づいたメッセージ構造:
 * {
 *   "body": "名前<>メール<>日時<>本文<>",
 *   "no": "レス番号",
 *   "bbsid": "板ID",
 *   "threadkey": "スレッドキー"
 * }
 */
data class JpnknMessage(
    val body: String,
    val no: String,
    val bbsid: String,
    val threadkey: String
) {
    /**
     * body フィールドから本文を抽出
     *
     * body は "<>" で区切られており、構造は以下の通り:
     * parts[0]: 名前
     * parts[1]: メール欄 (sage等)
     * parts[2]: 日時
     * parts[3]: レス本文
     *
     * @return レス本文、取得できない場合は空文字列
     */
    fun extractMessage(): String {
        val parts = body.split("<>")
        return if (parts.size >= 4) {
            parts[3].trim()
        } else {
            ""
        }
    }

    /**
     * 名前を抽出
     */
    @Suppress("unused")
    fun extractName(): String {
        val parts = body.split("<>")
        return if (parts.isNotEmpty()) {
            parts[0].trim()
        } else {
            "名無し"
        }
    }

    companion object {
        /**
         * JSON 文字列からパース
         *
         * @param json JSON 文字列
         * @return JpnknMessage オブジェクト、パースに失敗した場合は null
         */
        fun fromJson(json: String): JpnknMessage? {
            return try {
                val jsonObject = JSONObject(json)
                JpnknMessage(
                    body = jsonObject.getString("body"),
                    no = jsonObject.getString("no"),
                    bbsid = jsonObject.getString("bbsid"),
                    threadkey = jsonObject.getString("threadkey")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

