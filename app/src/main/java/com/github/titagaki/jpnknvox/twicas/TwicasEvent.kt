package com.github.titagaki.jpnknvox.twicas

import org.json.JSONArray
import org.json.JSONObject

/**
 * ツイキャスの配信状態
 *
 * @param movieId 動画 ID
 * @param isLive 配信中かどうか
 */
data class TwicasMovie(
    val movieId: String,
    val isLive: Boolean
)

/**
 * コメントサーバから受け取ったコメント 1 件
 *
 * ツイキャスのコメントにはレス番号にあたるものが無い。
 * `numComments` は配信全体の累計コメント数であって個々のコメントの番号ではないため、扱わない。
 */
data class TwicasComment(
    val name: String,
    val message: String
)

/**
 * ツイキャスから届く JSON のパース
 *
 * 通信を伴わない純粋な変換だけをここに置き、ユニットテストの対象にする。
 */
object TwicasEvent {

    /**
     * `streamserver.php` の応答をパースする
     *
     * 存在しないユーザーを指定した場合、応答は 200 だが中身が `{}` になり
     * `movie` ごと無い。この場合は null を返す。
     *
     * @return 配信状態。ユーザーが見つからない・パースできない場合は null
     */
    fun parseStreamServer(json: String): TwicasMovie? {
        return try {
            val movie = JSONObject(json).optJSONObject("movie") ?: return null
            val movieId = movie.opt("id")?.toString().orEmpty()
            if (movieId.isBlank()) return null

            TwicasMovie(
                movieId = movieId,
                isLive = movie.optBoolean("live", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * `eventpubsuburl.php` の応答からコメントサーバの URL を取り出す
     *
     * @return WebSocket の URL。取得できない場合は null
     */
    fun parsePubSubUrl(json: String): String? {
        return try {
            JSONObject(json).optString("url").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * コメントサーバから届いた 1 フレームを読み上げ対象のコメントに変換する
     *
     * フレームはイベントの配列で、新着が無いときは `[]` が届く（接続維持を兼ねている）。
     * コメントとギフト以外のイベントは読み飛ばす。
     *
     * @return 読み上げるコメントの一覧。新着が無ければ空のリスト
     */
    fun parseComments(raw: String): List<TwicasComment> {
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { toComment(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * イベント 1 件をコメントに変換する
     *
     * @return コメント。読み上げる内容が無い場合は null
     */
    private fun toComment(event: JSONObject): TwicasComment? {
        val message = event.optString("message").trim()

        return when (event.optString("type")) {
            "comment" -> {
                if (message.isEmpty()) return null
                TwicasComment(
                    name = event.optJSONObject("author")?.optString("name").orEmpty(),
                    message = message
                )
            }

            // ギフトはアイテム名を頭に付けて、コメントと同じ扱いで読み上げる
            "gift" -> {
                val itemName = event.optJSONObject("item")?.optString("name").orEmpty()
                val text = listOf(itemName, message).filter { it.isNotEmpty() }.joinToString(" ")
                if (text.isEmpty()) return null

                TwicasComment(
                    name = event.optJSONObject("sender")?.optString("name").orEmpty(),
                    message = text
                )
            }

            else -> null
        }
    }
}
