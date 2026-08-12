package com.github.titagaki.jpnknvox.source

import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.ReceivedComment
import com.github.titagaki.jpnknvox.twicas.TwicasClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import java.io.IOException

/**
 * ツイキャスの取得先
 *
 * 配信は始まったり終わったりするので、次を繰り返し続ける:
 *
 * 1. 配信が始まるまで [AppConfig.Twicas.BROADCAST_POLLING_INTERVAL_MS] ごとに確認する
 * 2. 始まったらコメントサーバに繋いで、新着コメントを流す
 * 3. 切れたら 1 に戻る（枠が終わった場合もここを通る）
 *
 * 接続時に過去のコメントは取りに行かない。読み上げアプリで
 * 過去ログを喋り始めると事故になるため、繋いだ後の新着だけを扱う。
 */
class TwicasConnector(
    override val source: CommentSource,
    private val coroutineScope: CoroutineScope,
    private val callbacks: CommentConnectorCallbacks,
    private val client: TwicasClient = TwicasClient()
) : CommentConnector {

    companion object {
        private const val TAG = "TwicasConnector"
    }

    private var job: Job? = null
    private var socket: WebSocket? = null

    /** 直前に通知した状態。同じ状態を繰り返しログに出さないために持つ */
    private var lastStatus: SourceStatus? = null

    override fun start() {
        job?.cancel()
        lastStatus = null
        updateStatus(SourceStatus.WAITING, "${source.sourceId}: 配信を確認します")

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
     * 配信待ち → コメント受信 → 切断 を繰り返す
     */
    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val movie = try {
                client.fetchMovie(source.sourceId)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to fetch movie", e)
                updateStatus(
                    SourceStatus.DISCONNECTED,
                    "${source.sourceId}: 配信の確認に失敗しました (${e.message})"
                )
                delay(AppConfig.Twicas.BROADCAST_POLLING_INTERVAL_MS)
                continue
            }

            if (movie == null) {
                updateStatus(
                    SourceStatus.ERROR,
                    "${source.sourceId}: ユーザーが見つかりません"
                )
                delay(AppConfig.Twicas.BROADCAST_POLLING_INTERVAL_MS)
                continue
            }

            if (!movie.isLive) {
                updateStatus(SourceStatus.WAITING_BROADCAST, "${source.sourceId}: 配信の開始を待っています")
                delay(AppConfig.Twicas.BROADCAST_POLLING_INTERVAL_MS)
                continue
            }

            val url = try {
                client.fetchCommentServerUrl(movie.movieId)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to fetch comment server URL", e)
                updateStatus(
                    SourceStatus.DISCONNECTED,
                    "${source.sourceId}: コメントサーバの取得に失敗しました (${e.message})"
                )
                delay(AppConfig.Twicas.RECONNECT_DELAY_MS)
                continue
            }

            receiveComments(url)

            // 枠の終了・回線断のどちらでもここに来る。少し置いて配信待ちからやり直す
            if (currentCoroutineContext().isActive) {
                updateStatus(SourceStatus.DISCONNECTED, "${source.sourceId}: コメントサーバから切断されました")
                delay(AppConfig.Twicas.RECONNECT_DELAY_MS)
            }
        }
    }

    /**
     * コメントサーバに繋ぎ、切断されるまでコメントを流し続ける
     */
    private suspend fun receiveComments(url: String) {
        val closed = CompletableDeferred<Unit>()

        val webSocket = client.openCommentSocket(
            url = url,
            onComment = { comment ->
                callbacks.onComment(
                    ReceivedComment(
                        sourceUuid = source.uuid,
                        // ツイキャスにはレス番号にあたるものが無い
                        no = "",
                        name = comment.name,
                        message = comment.message
                    )
                )
            },
            onClosed = { closed.complete(Unit) }
        )
        socket = webSocket
        updateStatus(SourceStatus.CONNECTED, "${source.sourceId}: コメントサーバに接続しました")

        try {
            closed.await()
        } finally {
            // stop() によるキャンセルでもここを通り、ソケットを閉じる
            webSocket.cancel()
            socket = null
        }
    }

    /**
     * 状態を通知する
     *
     * 配信待ちのポーリングは 5 秒ごとに回るため、同じ状態が続く間は
     * システムログに出さない（変わった瞬間だけ出す）。
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
