package com.github.titagaki.jpnknvox.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service と UI の橋渡しをするシングルトン
 *
 * メッセージログとシステムログを一元管理し、
 * StateFlow を通じて UI へリアクティブに公開する。
 */
object MessageManager {

    private const val MAX_LOGS = 500

    private val _messageLogs = MutableStateFlow<List<MessageLog>>(emptyList())
    val messageLogs: StateFlow<List<MessageLog>> = _messageLogs.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(emptyList())
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    /**
     * JpnknMessage を MessageLog に変換してリストに追加する。
     * リストが [MAX_LOGS] 件を超えた場合、古いものから削除する。
     */
    fun addMessage(msg: JpnknMessage) {
        val log = msg.toLog()
        _messageLogs.value = (_messageLogs.value + log).let { list ->
            if (list.size > MAX_LOGS) list.drop(list.size - MAX_LOGS) else list
        }
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
}

