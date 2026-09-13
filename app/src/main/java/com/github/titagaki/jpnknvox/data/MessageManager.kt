package com.github.titagaki.jpnknvox.data

import com.github.titagaki.jpnknvox.source.SourceStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service と UI の橋渡しをするシングルトン
 *
 * メッセージログ・システムログ・取得先ごとの接続状態を一元管理し、
 * StateFlow を通じて UI へリアクティブに公開する。
 */
object MessageManager {

    private const val MAX_LOGS = 500

    private val _messageLogs = MutableStateFlow<List<MessageLog>>(emptyList())
    val messageLogs: StateFlow<List<MessageLog>> = _messageLogs.asStateFlow()

    /**
     * 受信したコメントを 1 件ずつ受信順に流す（配信アプリへの受け渡し用）。
     * 購読者がいない間に届いた分は捨てる。過去分は [messageLogs] を見る。
     */
    private val _newMessages = MutableSharedFlow<MessageLog>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<MessageLog> = _newMessages.asSharedFlow()

    /** [JpnknVoxService] が稼働中かどうか。サービスの onCreate / onDestroy で更新する */
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    /** 取得先の uuid ごとの接続状態。設定画面の一覧に出す */
    private val _sourceStatuses = MutableStateFlow<Map<String, SourceStatus>>(emptyMap())
    val sourceStatuses: StateFlow<Map<String, SourceStatus>> = _sourceStatuses.asStateFlow()

    /**
     * 受信したコメントを MessageLog に変換してリストに追加する。
     * リストが [MAX_LOGS] 件を超えた場合、古いものから削除する。
     */
    fun addMessage(comment: ReceivedComment, source: CommentSource) {
        val log = comment.toLog(source)
        _messageLogs.value = (_messageLogs.value + log).let { list ->
            if (list.size > MAX_LOGS) list.drop(list.size - MAX_LOGS) else list
        }
        _newMessages.tryEmit(log)
    }

    /**
     * サービスの稼働状態を更新する
     */
    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    /**
     * タイムスタンプ付きのシステムログを追加する。
     * リストが [MAX_LOGS] 件を超えた場合、古いものから削除する。
     */
    fun addSystemLog(text: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $text"
        _systemLogs.value = (_systemLogs.value + entry).let { list ->
            if (list.size > MAX_LOGS) list.drop(list.size - MAX_LOGS) else list
        }
    }

    /**
     * 取得先の接続状態を更新する
     */
    fun updateSourceStatus(sourceUuid: String, status: SourceStatus) {
        _sourceStatuses.value = _sourceStatuses.value + (sourceUuid to status)
    }

    /**
     * 取得先の接続状態を取り除く（取得先が消えた・サービスが止まったとき）
     */
    fun removeSourceStatus(sourceUuid: String) {
        _sourceStatuses.value = _sourceStatuses.value - sourceUuid
    }

    /**
     * すべての接続状態を消す
     */
    fun clearSourceStatuses() {
        _sourceStatuses.value = emptyMap()
    }
}
