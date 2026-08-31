package com.github.titagaki.jpnknvox.net

import com.github.titagaki.jpnknvox.config.AppConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 取得先をまたいで使い回す OkHttp クライアント
 *
 * 取得先ごとに作るとスレッドプールと接続プールがその数だけ増える。
 * 屋外で長時間動かすアプリなので 1 つにまとめる。
 *
 * ツイキャスのコメントサーバも Twitch の IRC も WebSocket なので、
 * ping の設定も含めて同じものを共有する。
 */
object SharedHttpClient {

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(AppConfig.Http.REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(AppConfig.Http.REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            // 回線が黙って切れたときに WebSocket 側で気付けるようにする
            .pingInterval(AppConfig.Http.PING_INTERVAL_SEC, TimeUnit.SECONDS)
            .build()
    }
}
