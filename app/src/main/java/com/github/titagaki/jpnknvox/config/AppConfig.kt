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


    // 読み上げ設定
    object Tts {
        /** 話す速度（%）。100 で等倍 */
        const val DEFAULT_SPEECH_RATE = 120
        const val MIN_SPEECH_RATE = 50
        const val MAX_SPEECH_RATE = 200

        /** 音量（0〜100 %） */
        const val DEFAULT_VOLUME = 80

        /** 読み上げるメッセージの最大文字数 */
        const val DEFAULT_MAX_MESSAGE_LENGTH = 100

        /** テスト再生で読み上げるテキスト */
        const val TEST_TEXT = "じゃぱんくん-Vox のテスト再生です"

        /**
         * 1 件読み上げてから次の 1 件を読み始めるまでの間隔（ミリ秒）
         *
         * 短くすると連投に追従しやすくなるが、聞き取りづらくなる。
         * 連投テストモード（デバッグビルド）で実際の聞こえ方を確認しながら調整する。
         */
        const val SPEECH_INTERVAL_MS = 1000L
    }

    // 連投テスト設定（デバッグビルドのみ使用）
    object TestBurst {
        const val DEFAULT_COUNT = 20
        const val DEFAULT_INTERVAL_MS = 300L
        const val MAX_COUNT = 500
        const val MAX_INTERVAL_MS = 10000L
    }

    // オーバーレイ設定
    object Overlay {
        const val MAX_MESSAGE_LENGTH = 30

        /** 背景の濃さの既定値（0〜100 %） */
        const val DEFAULT_ALPHA = 80
        const val INITIAL_Y_POSITION = 100
        const val STATUS_TEXT_SIZE = 14f
        const val MESSAGE_TEXT_SIZE = 12f
        const val PADDING_HORIZONTAL = 16
        const val PADDING_VERTICAL = 8
    }
}

