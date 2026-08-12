package com.github.titagaki.jpnknvox.data

import com.github.titagaki.jpnknvox.config.AppConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * コメントの取得先の種別
 *
 * @param id 永続化に使う識別子。[label] を変えても保存済みの設定が壊れないよう、別に持つ
 * @param label 設定画面に出す名前
 * @param idFieldLabel ID 入力欄のラベル
 * @param idFieldDescription ID 入力欄が空のときに出す説明。何を入れる欄なのかを示す
 */
enum class SourceType(
    val id: String,
    val label: String,
    val idFieldLabel: String,
    val idFieldDescription: String
) {
    JPNKN(
        "jpnkn",
        "jpnkn",
        "板 ID",
        "板の URL の bbs.jpnkn.com/ に続く部分"
    ),
    TWICAS(
        "twicas",
        "ツイキャス",
        "ユーザー ID",
        "配信者の URL の twitcasting.tv/ に続く部分"
    );

    /**
     * 取得先がどこを指しているかを表す文字列
     *
     * ID 入力欄の下に出し、入力した ID がどこを指すかを確かめられるようにする。
     *
     * @return ID が空の場合は空文字列（入力例を出すためのものではない）
     */
    fun locationHint(sourceId: String): String = when {
        sourceId.isBlank() -> ""
        this == JPNKN -> "${AppConfig.Mqtt.TOPIC_PREFIX}$sourceId"
        else -> "twitcasting.tv/$sourceId"
    }

    companion object {
        fun fromId(id: String): SourceType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * コメントの取得先 1 件
 *
 * @param uuid 内部識別子。ID を編集しても同じ取得先として追えるようにする
 * @param sourceId 板 ID（jpnkn）またはユーザー ID（ツイキャス）。一覧やログの表示にも使う
 * @param color 一覧やログで取得先を見分けるための識別色（ARGB）
 */
data class CommentSource(
    val uuid: String,
    val type: SourceType,
    val sourceId: String,
    val color: Int
) {

    /**
     * 接続先が同じかどうか
     *
     * 識別色だけの変更で接続を張り直さないよう、種別と ID だけで比べる。
     */
    fun connectsTo(other: CommentSource): Boolean =
        type == other.type && sourceId == other.sourceId

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_UUID, uuid)
        put(KEY_TYPE, type.id)
        put(KEY_SOURCE_ID, sourceId)
        put(KEY_COLOR, color)
    }

    companion object {
        private const val KEY_UUID = "uuid"
        private const val KEY_TYPE = "type"
        private const val KEY_SOURCE_ID = "sourceId"
        private const val KEY_COLOR = "color"

        /**
         * 板 ID しか持っていなかった頃の設定から移行するときの uuid
         *
         * 乱数にすると、保存される前に読み直すたびに別の取得先として扱われてしまう。
         * 移行元は 1 件しかないので固定値でよい。
         */
        private const val LEGACY_JPNKN_UUID = "legacy-jpnkn"

        /**
         * 新しい取得先を作る
         */
        fun create(
            type: SourceType,
            sourceId: String,
            color: Int
        ): CommentSource = CommentSource(
            uuid = UUID.randomUUID().toString(),
            type = type,
            sourceId = sourceId,
            color = color
        )

        /**
         * JSON からパース
         *
         * @return パースできない場合は null（種別が不明な場合を含む）
         */
        fun fromJson(obj: JSONObject): CommentSource? {
            val type = SourceType.fromId(obj.optString(KEY_TYPE)) ?: return null
            val sourceId = obj.optString(KEY_SOURCE_ID)
            if (sourceId.isBlank()) return null

            return CommentSource(
                uuid = obj.optString(KEY_UUID).ifBlank { UUID.randomUUID().toString() },
                type = type,
                sourceId = sourceId,
                color = obj.optInt(KEY_COLOR, AppConfig.Source.PALETTE.first())
            )
        }

        /**
         * 取得先のリストを JSON 文字列に変換
         */
        fun listToJson(sources: List<CommentSource>): String {
            val array = JSONArray()
            sources.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        /**
         * JSON 文字列から取得先のリストを復元
         *
         * 壊れた要素は読み飛ばす。全体がパースできない場合は空のリストを返す。
         */
        fun listFromJson(json: String): List<CommentSource> {
            return try {
                val array = JSONArray(json)
                (0 until array.length()).mapNotNull { i ->
                    array.optJSONObject(i)?.let { fromJson(it) }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /**
         * 板 ID 1 つだけを持っていた頃の設定を、取得先 1 件のリストに変換する
         *
         * @return 板 ID が未設定なら空のリスト
         */
        fun migrateFromBoardId(boardId: String?): List<CommentSource> {
            if (boardId.isNullOrBlank()) return emptyList()

            return listOf(
                CommentSource(
                    uuid = LEGACY_JPNKN_UUID,
                    type = SourceType.JPNKN,
                    sourceId = boardId,
                    color = AppConfig.Source.PALETTE.first()
                )
            )
        }
    }
}
