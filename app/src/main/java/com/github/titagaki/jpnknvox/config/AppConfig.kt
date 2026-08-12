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

    // jpnkn 掲示板（MQTT 以外）
    object Jpnkn {
        /** 板の URL。板が実在するかの確認に使う */
        const val BOARD_BASE_URL = "https://bbs.jpnkn.com/"
    }

    // ツイキャス接続情報
    //
    // 公式 API v2 ではなく、認証の要らない内部エンドポイントを使う。
    // 詳細と選定理由は docs/spec/twicas-comment-spec.md を参照
    object Twicas {
        /** 配信中かどうかと動画 ID を取得する */
        const val STREAM_SERVER_URL = "https://twitcasting.tv/streamserver.php"

        /** コメントサーバ（WebSocket）の URL を取得する */
        const val EVENT_PUBSUB_URL = "https://twitcasting.tv/eventpubsuburl.php"

        /** 配信開始を待つ間のポーリング間隔 */
        const val BROADCAST_POLLING_INTERVAL_MS = 5000L

        /** コメントサーバから切断されたあと、取り直すまでの待ち時間 */
        const val RECONNECT_DELAY_MS = 5000L

        /**
         * WebSocket の ping 間隔（秒）
         *
         * モバイル回線では切断が通知されないまま黙って止まることがあるため、
         * こちらから ping を打って検出する。
         */
        const val PING_INTERVAL_SEC = 30L

        /** 通信のタイムアウト（秒） */
        const val REQUEST_TIMEOUT_SEC = 15L
    }

    // コメント取得先の設定
    object Source {
        /**
         * 取得先の識別色として選べる色（ARGB）
         *
         * 色相の順（赤→黄→緑→水色→青→紫→桃）に並べる。
         * 隣り合うものほど色が近いので、離れた色を選べば見分けやすいと分かる。
         *
         * 明るい背景の上に 3〜4dp の細い帯として出るため、
         * 黄と水色は薄くしすぎると背景に溶ける。色名として通じる範囲で沈めてある。
         */
        val PALETTE: List<Int> = listOf(
            0xFFC7411F.toInt(), // 赤
            0xFFEFB700.toInt(), // 黄
            0xFF1E7B45.toInt(), // 緑
            0xFF29B6F6.toInt(), // 水色
            0xFF0B57D0.toInt(), // 青
            0xFF8B3FE8.toInt(), // 紫
            0xFFB0398A.toInt()  // 桃
        )
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
    }

    // オーバーレイ設定
    object Overlay {
        /** メッセージ表示の行数。受信内容によらず高さを固定するため常にこの行数を確保する */
        const val MESSAGE_LINES = 2

        /** オーバーレイに表示するメッセージの最大文字数（[MESSAGE_LINES] 行に収まる目安） */
        const val MAX_MESSAGE_LENGTH = 60

        /** 背景の濃さの既定値（0〜100 %） */
        const val DEFAULT_ALPHA = 80

        /** メッセージの文字色（ARGB） */
        const val MESSAGE_TEXT_COLOR = 0xFFF5F5F5.toInt()

        // 文字の視認性（縁取り・影）。固定 px にすると文字サイズを変えたとき破綻するため、
        // すべて文字サイズに対する割合で持つ
        /** 縁取りの幅 */
        const val TEXT_STROKE_RATIO = 1f / 8f
        /** 影のぼかし半径 */
        const val TEXT_SHADOW_RADIUS_RATIO = 1f / 8f
        /** 影を下にずらす量 */
        const val TEXT_SHADOW_DY_RATIO = 1f / 16f
        /** 縁取りの色 */
        const val TEXT_STROKE_COLOR = 0xFF000000.toInt()
        /** 影の色 */
        const val TEXT_SHADOW_COLOR = 0x99000000.toInt()

        const val INITIAL_Y_POSITION = 100

        /** メッセージの文字サイズの既定値（sp）。設定画面の選択肢「中」に対応する */
        const val DEFAULT_TEXT_SIZE = 12

        /**
         * アプリ名（接続状態）の文字サイズ。メッセージの文字サイズに対する割合
         *
         * コメントより控えめにしつつ、文字サイズを変えても比率が崩れないようにする。
         */
        const val STATUS_TEXT_SIZE_RATIO = 11f / 12f

        /** 内側の余白（dp）。px で持つと画面密度によって見え方が変わるため dp で持つ */
        const val PADDING_HORIZONTAL_DP = 12f

        /**
         * 上下の内側の余白（dp）
         *
         * 上下で値が違うのは、下側だけ最終行の descent（文字の下に伸びる部分）が
         * 余白に上乗せされるため。同じ値にすると下が広く見える。
         */
        const val PADDING_TOP_DP = 3f
        const val PADDING_BOTTOM_DP = 6f
    }
}

