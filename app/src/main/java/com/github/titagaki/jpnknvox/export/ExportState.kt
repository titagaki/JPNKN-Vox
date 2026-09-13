package com.github.titagaki.jpnknvox.export

import com.github.titagaki.jpnknvox.source.SourceStatus
import io.github.titagaki.genkaibroadcaster.comment.ICommentSource

/**
 * 配信アプリ (genkai-broadcaster) へ知らせる取得状態
 *
 * @param state `ICommentSource.STATE_*`
 * @param detail 配信アプリが状態行に出す補足。READY のときは空
 */
data class ExportState(val state: Int, val detail: String) {

    companion object {
        /**
         * サービスの稼働状態と取得先ごとの接続状態から、配信アプリ向けの 1 つの状態を決める
         *
         * 配信アプリは ERROR の detail だけをそのまま表示するので、
         * 配信者が対処できること（サービスの開始）は ERROR に寄せて文言を出す。
         * 再接続中・接続待ちは配信アプリ側で「接続中」と出るので CONNECTING にまとめる。
         */
        fun from(serviceRunning: Boolean, statuses: Collection<SourceStatus>): ExportState {
            if (!serviceRunning) {
                return ExportState(ICommentSource.STATE_ERROR, "JPNKN Vox が停止中")
            }
            return when (SourceStatus.aggregate(statuses)) {
                null -> ExportState(ICommentSource.STATE_CONNECTING, "取得先の接続待ち")
                SourceStatus.ERROR -> ExportState(ICommentSource.STATE_ERROR, "JPNKN Vox の取得先でエラー")
                SourceStatus.DISCONNECTED -> ExportState(ICommentSource.STATE_CONNECTING, "再接続中")
                SourceStatus.WAITING -> ExportState(ICommentSource.STATE_CONNECTING, "接続待ち")
                SourceStatus.CONNECTED, SourceStatus.WAITING_BROADCAST -> ExportState(ICommentSource.STATE_READY, "")
            }
        }
    }
}
