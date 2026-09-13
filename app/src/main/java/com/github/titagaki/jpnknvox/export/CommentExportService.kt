package com.github.titagaki.jpnknvox.export

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import com.github.titagaki.jpnknvox.data.MessageLog
import com.github.titagaki.jpnknvox.data.MessageManager
import io.github.titagaki.genkaibroadcaster.comment.CommentEntry
import io.github.titagaki.genkaibroadcaster.comment.ICommentListener
import io.github.titagaki.genkaibroadcaster.comment.ICommentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 配信アプリ (genkai-broadcaster) へコメントを渡す Bound Service
 *
 * 配信アプリが配信中だけ bind してくる。こちらは [MessageManager] を購読して
 * 新着コメントと取得状態を AIDL ([ICommentListener]) で送るだけで、
 * コメントの取得そのものは [com.github.titagaki.jpnknvox.JpnknVoxService] が担う。
 * bind されてもサービスは自動では開始しない（読み上げが動いていなければ「停止中」と知らせる）。
 *
 * 契約（action・権限・AIDL の版）は `docs/spec/comment-export-spec.md`。
 * AIDL ファイルは配信アプリのものをコピーして `app/src/main/aidl` に置いている。
 */
class CommentExportService : Service() {

    companion object {
        private const val TAG = "CommentExportService"
        private const val SOURCE_NAME = "JPNKN Vox"
    }

    /** 購読はメインスレッドに寄せ、[RemoteCallbackList] の beginBroadcast を重ねない */
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** 配信アプリ側の listener。相手のプロセスが死んだら自動で外れる */
    private val listeners = RemoteCallbackList<ICommentListener>()

    /** 最後に送った状態。同じ状態を繰り返し送らない */
    private var lastState: ExportState? = null

    private val binder = object : ICommentSource.Stub() {
        override fun getVersion(): Int = ICommentSource.VERSION

        override fun getSourceName(): String = SOURCE_NAME

        override fun registerListener(listener: ICommentListener?) {
            listener ?: return
            if (!listeners.register(listener)) return
            // 現在の状態をすぐ知らせる（次の変化まで待たせない）
            val state = currentState()
            runCatching { listener.onStateChanged(state.state, state.detail) }
                .onFailure { Log.w(TAG, "initial onStateChanged failed", it) }
            MessageManager.addSystemLog("配信アプリが接続しました")
        }

        override fun unregisterListener(listener: ICommentListener?) {
            listener ?: return
            if (listeners.unregister(listener)) {
                MessageManager.addSystemLog("配信アプリが切断しました")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        scope.launch {
            MessageManager.newMessages.collect { deliver(it) }
        }
        scope.launch {
            combine(MessageManager.serviceRunning, MessageManager.sourceStatuses) { running, statuses ->
                ExportState.from(running, statuses.values)
            }.collect { pushState(it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        listeners.kill()
        super.onDestroy()
    }

    private fun currentState(): ExportState =
        ExportState.from(MessageManager.serviceRunning.value, MessageManager.sourceStatuses.value.values)

    private fun deliver(log: MessageLog) {
        val entry = CommentEntry(
            id = log.id,
            receivedAtMillis = log.timestamp,
            author = log.name.ifBlank { null },
            body = log.message
        )
        broadcast { it.onComments(listOf(entry)) }
    }

    private fun pushState(state: ExportState) {
        if (state == lastState) return
        lastState = state
        broadcast { it.onStateChanged(state.state, state.detail) }
    }

    /** 登録中の全 listener へ送る。死んだ listener は [RemoteCallbackList] が外す */
    private fun broadcast(action: (ICommentListener) -> Unit) {
        val count = listeners.beginBroadcast()
        try {
            for (i in 0 until count) {
                runCatching { action(listeners.getBroadcastItem(i)) }
                    .onFailure { Log.w(TAG, "listener call failed", it) }
            }
        } finally {
            listeners.finishBroadcast()
        }
    }
}
