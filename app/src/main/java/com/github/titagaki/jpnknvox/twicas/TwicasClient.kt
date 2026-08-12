package com.github.titagaki.jpnknvox.twicas

import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ツイキャスのコメント取得に使う通信をまとめたクラス
 *
 * 公式 API v2 ではなく、認証の要らない内部エンドポイントを使う。
 * 手順は 3 段階（詳細は docs/spec/twicas-comment-spec.md）:
 *
 * 1. [fetchMovie] で配信中かどうかと動画 ID を得る
 * 2. [fetchCommentServerUrl] でコメントサーバ（WebSocket）の URL を得る
 * 3. [openCommentSocket] でそこに繋ぎ、新着コメントを受け取る
 */
class TwicasClient(
    private val httpClient: OkHttpClient = sharedHttpClient
) {

    companion object {
        private const val TAG = "TwicasClient"

        /**
         * 取得先をまたいで使い回す OkHttp クライアント
         *
         * 取得先ごとに作るとスレッドプールと接続プールがその数だけ増える。
         * 屋外で長時間動かすアプリなので 1 つにまとめる。
         */
        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(AppConfig.Twicas.REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(AppConfig.Twicas.REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)
                // 回線が黙って切れたときに WebSocket 側で気付けるようにする
                .pingInterval(AppConfig.Twicas.PING_INTERVAL_SEC, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * 配信状態を取得する
     *
     * @param userId ツイキャスのユーザー ID
     * @return 配信状態。ユーザーが見つからない場合は null
     * @throws IOException 通信に失敗した場合。
     *   「配信していない」と区別が付かなくなるのでここでは握り潰さない
     */
    @Throws(IOException::class)
    fun fetchMovie(userId: String): TwicasMovie? {
        val url = AppConfig.Twicas.STREAM_SERVER_URL.toHttpUrl().newBuilder()
            .addQueryParameter("target", userId)
            .addQueryParameter("mode", "client")
            .build()

        val request = Request.Builder().url(url).get().build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("streamserver.php が ${response.code} を返しました")
            }
            val body = response.body?.string().orEmpty()
            return TwicasEvent.parseStreamServer(body)
        }
    }

    /**
     * コメントサーバ（WebSocket）の URL を取得する
     *
     * @param movieId 動画 ID
     * @return WebSocket の URL
     * @throws IOException 通信に失敗した場合、または URL を取得できなかった場合
     */
    @Throws(IOException::class)
    fun fetchCommentServerUrl(movieId: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("movie_id", movieId)
            .addFormDataPart("__n", System.currentTimeMillis().toString())
            .addFormDataPart("password", "")
            .build()

        val request = Request.Builder()
            .url(AppConfig.Twicas.EVENT_PUBSUB_URL)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("eventpubsuburl.php が ${response.code} を返しました")
            }
            val text = response.body?.string().orEmpty()
            return TwicasEvent.parsePubSubUrl(text)
                ?: throw IOException("コメントサーバの URL を取得できませんでした")
        }
    }

    /**
     * コメントサーバに接続する
     *
     * 新着が無い間も空の配列が届くため、それ自体が接続の生存確認になる。
     *
     * @param url [fetchCommentServerUrl] で取得した URL
     * @param onComment 新着コメントを受け取ったときに呼ばれる
     * @param onClosed 接続が切れたときに呼ばれる
     * @return 切断に使う WebSocket
     */
    fun openCommentSocket(
        url: String,
        onComment: (TwicasComment) -> Unit,
        onClosed: (Throwable?) -> Unit
    ): WebSocket {
        val request = Request.Builder().url(url).build()

        return httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                TwicasEvent.parseComments(text).forEach(onComment)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Comment socket failed", t)
                onClosed(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Comment socket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Comment socket closed: $code $reason")
                onClosed(null)
            }
        })
    }
}
