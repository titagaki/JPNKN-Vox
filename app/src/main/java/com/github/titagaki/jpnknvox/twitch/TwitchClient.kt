package com.github.titagaki.jpnknvox.twitch

import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.net.SharedHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlin.random.Random

/**
 * IRC の接続から届く通知
 */
interface TwitchIrcCallbacks {

    /** チャンネルへの JOIN が通った */
    fun onJoined()

    /** コメントを受け取った */
    fun onComment(comment: TwitchComment)

    /** サーバから NOTICE が届いた。入れないチャンネルなどの理由が入る */
    fun onNotice(message: String)

    /**
     * 接続が切れた、またはサーバから繋ぎ直すよう促された
     *
     * @param cause 通信の失敗による切断の場合はその例外。それ以外は null
     */
    fun onClosed(cause: Throwable?)
}

/**
 * Twitch のコメント取得に使う通信をまとめたクラス
 *
 * 公式 API（Helix）は OAuth が要るため使わない。代わりに次の 2 つを使う
 * （詳細は docs/spec/twitch-comment-spec.md）:
 *
 * - [openChat] チャットの IRC サーバ。匿名で繋げて認証が要らない
 * - [fetchChannel] チャンネルの存在確認。接続テストでのみ使う
 */
class TwitchClient(
    private val httpClient: OkHttpClient = SharedHttpClient.instance
) {

    companion object {
        private const val TAG = "TwitchClient"

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    /**
     * チャンネルの存在と配信状態を取得する
     *
     * IRC は存在しないチャンネルへの JOIN を黙って捨てるため、
     * 「名前が間違っている」と伝えるにはこちらが要る。
     *
     * @param login チャンネル名
     * @return チャンネルの状態。チャンネルが見つからない場合は null
     * @throws IOException 通信に失敗した場合。
     *   「存在しない」と区別が付かなくなるのでここでは握り潰さない
     */
    @Throws(IOException::class)
    fun fetchChannel(login: String): TwitchChannel? {
        val request = Request.Builder()
            .url(AppConfig.Twitch.GQL_URL)
            .header("Client-Id", AppConfig.Twitch.GQL_CLIENT_ID)
            .post(TwitchEvent.buildChannelQuery(login).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("gql が ${response.code} を返しました")
            }
            val body = response.body?.string().orEmpty()
            return TwitchEvent.parseChannel(body)
        }
    }

    /**
     * チャットに接続し、チャンネルに JOIN する
     *
     * 接続した時点より前のコメントは送られてこないため、
     * 過去ログを読み上げてしまう心配はない。
     *
     * サーバは数分おきに PING を送ってきて、PONG を返さないと切断する。
     * その応答はここで完結させ、呼び出し側では扱わない。
     *
     * @param channel チャンネル名
     * @param callbacks 受信の通知先
     * @return 切断に使う WebSocket
     */
    fun openChat(channel: String, callbacks: TwitchIrcCallbacks): WebSocket {
        val request = Request.Builder().url(AppConfig.Twitch.IRC_URL).build()

        return httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 匿名ユーザーは PASS を送らない。同じ名前で複数繋ぐのを避けるため乱数を付ける
                val nick = AppConfig.Twitch.ANONYMOUS_NICK_PREFIX +
                        Random.nextInt(10000, 100000)

                webSocket.send("CAP REQ :${AppConfig.Twitch.CAPABILITIES}")
                webSocket.send("NICK $nick")
                webSocket.send("JOIN #${channel.lowercase()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                TwitchEvent.parseFrame(text).forEach { line ->
                    handleLine(webSocket, line)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Chat socket failed", t)
                callbacks.onClosed(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Chat socket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Chat socket closed: $code $reason")
                callbacks.onClosed(null)
            }

            private fun handleLine(webSocket: WebSocket, line: TwitchIrcLine) {
                when (line.command) {
                    TwitchEvent.CMD_PING ->
                        webSocket.send("PONG :${AppConfig.Twitch.IRC_HOST}")

                    TwitchEvent.CMD_PRIVMSG ->
                        TwitchEvent.toComment(line)?.let(callbacks::onComment)

                    TwitchEvent.CMD_END_OF_NAMES ->
                        callbacks.onJoined()

                    TwitchEvent.CMD_NOTICE ->
                        callbacks.onNotice(line.trailing)

                    // サーバの再起動などで送られてくる。繋ぎ直す以外にできることはない
                    TwitchEvent.CMD_RECONNECT ->
                        callbacks.onClosed(null)
                }
            }
        })
    }
}
