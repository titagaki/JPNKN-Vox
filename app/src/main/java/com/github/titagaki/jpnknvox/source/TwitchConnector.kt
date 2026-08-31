package com.github.titagaki.jpnknvox.source

import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.ReceivedComment
import com.github.titagaki.jpnknvox.twitch.TwitchClient
import com.github.titagaki.jpnknvox.twitch.TwitchComment
import com.github.titagaki.jpnknvox.twitch.TwitchIrcCallbacks
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket

/**
 * Twitch の取得先
 *
 * チャットは配信していない間も動いているため、ツイキャスのような配信待ちは無い。
 * 繋いだら切れるまで受け続け、切れたら
 * [AppConfig.Twitch.RECONNECT_DELAY_MS] 待って繋ぎ直すだけを繰り返す。
 *
 * 接続時に過去のコメントは送られてこないため、
 * 過去ログを読み上げてしまう心配はない。
 */
class TwitchConnector(
    override val source: CommentSource,
    private val coroutineScope: CoroutineScope,
    private val callbacks: CommentConnectorCallbacks,
    private val client: TwitchClient = TwitchClient()
) : CommentConnector {

    private var job: Job? = null
    private var socket: WebSocket? = null

    /** 直前に通知した状態。同じ状態を繰り返しログに出さないために持つ */
    private var lastStatus: SourceStatus? = null

    override fun start() {
        job?.cancel()
        lastStatus = null
        updateStatus(SourceStatus.WAITING, "${source.sourceId}: チャットに接続します")

        job = coroutineScope.launch { runLoop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
        socket?.cancel()
        socket = null
        callbacks.onSystemLog("${source.sourceId}: 接続を終了しました")
    }

    /**
     * 接続 → コメント受信 → 切断 を繰り返す
     */
    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            receiveComments()

            if (currentCoroutineContext().isActive) {
                delay(AppConfig.Twitch.RECONNECT_DELAY_MS)
            }
        }
    }

    /**
     * チャットに繋ぎ、切断されるまでコメントを流し続ける
     *
     * 存在しないチャンネルへの JOIN は成功も失敗も返らず黙殺されるため、
     * JOIN の完了を待ち、来なければチャンネル名が間違っているものとして扱う。
     * その場合も繋ぎ直しは続ける。名前が合っていて通信の側で失敗しただけ、
     * という場合に自力で復帰できるようにするため。
     */
    private suspend fun receiveComments() {
        val joined = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Throwable?>()

        val webSocket = client.openChat(
            channel = source.sourceId,
            callbacks = object : TwitchIrcCallbacks {
                override fun onJoined() {
                    joined.complete(Unit)
                }

                override fun onComment(comment: TwitchComment) {
                    callbacks.onComment(
                        ReceivedComment(
                            sourceUuid = source.uuid,
                            // Twitch にはレス番号にあたるものが無い
                            no = "",
                            name = comment.name,
                            message = comment.message
                        )
                    )
                }

                override fun onNotice(message: String) {
                    callbacks.onSystemLog("${source.sourceId}: $message")
                }

                override fun onClosed(cause: Throwable?) {
                    closed.complete(cause)
                }
            }
        )
        socket = webSocket

        try {
            if (!awaitJoin(joined, closed)) return

            val cause = closed.await()
            if (currentCoroutineContext().isActive) {
                updateStatus(
                    SourceStatus.DISCONNECTED,
                    "${source.sourceId}: チャットから切断されました" +
                            (cause?.message?.let { " ($it)" } ?: "")
                )
            }
        } finally {
            // stop() によるキャンセルでもここを通り、ソケットを閉じる
            webSocket.cancel()
            socket = null
        }
    }

    /**
     * JOIN の完了を待ち、結果を状態に反映する
     *
     * JOIN の完了と切断のどちらか早い方を取る。切れたあとも JOIN を待ち続けると、
     * 電波の切り替わりで繋ぎ直すたびに待ち時間の分だけ復帰が遅れる。
     *
     * @return コメントの受信を続けてよいかどうか。JOIN が通らなかった場合は false
     */
    private suspend fun awaitJoin(
        joined: CompletableDeferred<Unit>,
        closed: CompletableDeferred<Throwable?>
    ): Boolean {
        val didJoin = withTimeoutOrNull(AppConfig.Twitch.JOIN_TIMEOUT_MS) {
            select {
                joined.onAwait { true }
                closed.onAwait { false }
            }
        }

        return when (didJoin) {
            true -> {
                updateStatus(SourceStatus.CONNECTED, "${source.sourceId}: チャットに接続しました")
                true
            }

            // 待っている間に切れた。呼び出し元にそのまま切断として扱わせる
            false -> true

            // 時間内に JOIN も切断も起きなかった
            null -> {
                updateStatus(SourceStatus.ERROR, "${source.sourceId}: チャンネルが見つかりません")
                false
            }
        }
    }

    /**
     * 状態を通知する
     *
     * 再接続を繰り返す間に同じ状態を何度もログへ出さないよう、
     * 変わった瞬間だけ出す。
     */
    private fun updateStatus(status: SourceStatus, logMessage: String) {
        val changed = lastStatus != status
        lastStatus = status

        callbacks.onStatusChanged(source, status)
        if (changed) {
            callbacks.onSystemLog(logMessage)
        }
    }
}
