package com.github.titagaki.jpnknvox.config

/**
 * アプリケーション設定
 *
 * 将来的には SharedPreferences や DataStore から読み込むように拡張可能
 */
object AppConfig {

    // MQTT 接続情報
    object Mqtt {
        const val SERVER_HOST = "bbs.jpnkn.com"
        const val SERVER_PORT = 1883
        const val USERNAME = "genkai"
        const val PASSWORD = "7144"
        const val TOPIC_PREFIX = "bbs/"
        const val CLIENT_ID_PREFIX = "jpnkn_vox_android"

        // 再接続設定
        const val INITIAL_RETRY_DELAY_MS = 1000L
        const val MAX_RETRY_DELAY_MS = 60000L
        const val MAX_RETRY_ATTEMPTS = 10

        /**
         * 板 ID からトピックを生成
         */
        fun createTopic(boardId: String): String {
            return "$TOPIC_PREFIX$boardId"
        }
    }

    // 通知設定
    object Notification {
        const val CHANNEL_ID = "jpnkn_vox_channel"
        const val CHANNEL_NAME = "JPNKN Vox サービス"
        const val ID = 1
    }


    // オーバーレイ設定
    object Overlay {
        const val MAX_MESSAGE_LENGTH = 30
        const val INITIAL_Y_POSITION = 100
        const val STATUS_TEXT_SIZE = 14f
        const val MESSAGE_TEXT_SIZE = 12f
        const val PADDING_HORIZONTAL = 16
        const val PADDING_VERTICAL = 8
    }
}

