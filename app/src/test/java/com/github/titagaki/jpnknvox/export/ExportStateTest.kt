package com.github.titagaki.jpnknvox.export

import com.github.titagaki.jpnknvox.source.SourceStatus
import io.github.titagaki.genkaibroadcaster.comment.ICommentSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 配信アプリへ知らせる状態の決め方のテスト
 */
class ExportStateTest {

    @Test
    fun `サービスが止まっていれば取得先の状態によらず ERROR`() {
        val state = ExportState.from(serviceRunning = false, statuses = listOf(SourceStatus.CONNECTED))

        assertEquals(ICommentSource.STATE_ERROR, state.state)
        assertEquals("JPNKN Vox が停止中", state.detail)
    }

    @Test
    fun `稼働中で取得先が無ければ CONNECTING`() {
        val state = ExportState.from(serviceRunning = true, statuses = emptyList())

        assertEquals(ICommentSource.STATE_CONNECTING, state.state)
    }

    @Test
    fun `全部つながっていれば READY で補足は空`() {
        val state = ExportState.from(
            serviceRunning = true,
            statuses = listOf(SourceStatus.CONNECTED, SourceStatus.WAITING_BROADCAST)
        )

        assertEquals(ExportState(ICommentSource.STATE_READY, ""), state)
    }

    @Test
    fun `再接続中は CONNECTING`() {
        val state = ExportState.from(
            serviceRunning = true,
            statuses = listOf(SourceStatus.CONNECTED, SourceStatus.DISCONNECTED)
        )

        assertEquals(ICommentSource.STATE_CONNECTING, state.state)
        assertEquals("再接続中", state.detail)
    }

    @Test
    fun `エラーの取得先があれば ERROR`() {
        val state = ExportState.from(
            serviceRunning = true,
            statuses = listOf(SourceStatus.CONNECTED, SourceStatus.ERROR)
        )

        assertEquals(ICommentSource.STATE_ERROR, state.state)
    }
}
