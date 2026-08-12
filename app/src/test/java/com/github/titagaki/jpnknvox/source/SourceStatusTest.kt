package com.github.titagaki.jpnknvox.source

import org.junit.Assert.*
import org.junit.Test

/**
 * 複数の取得先の状態を 1 つにまとめるルールのテスト
 */
class SourceStatusTest {

    @Test
    fun `取得先が無ければ null`() {
        assertNull(SourceStatus.aggregate(emptyList()))
    }

    @Test
    fun `全部つながっていれば接続中`() {
        val statuses = listOf(SourceStatus.CONNECTED, SourceStatus.CONNECTED)

        assertEquals(SourceStatus.CONNECTED, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `配信待ちは正常扱いなので接続中にまとめる`() {
        // ツイキャスの配信待ちは異常ではない。ここで異常扱いにすると
        // 配信していない間ずっとオーバーレイが警告色になってしまう
        val statuses = listOf(SourceStatus.CONNECTED, SourceStatus.WAITING_BROADCAST)

        assertEquals(SourceStatus.CONNECTED, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `配信待ちだけでも接続中`() {
        assertEquals(
            SourceStatus.CONNECTED,
            SourceStatus.aggregate(listOf(SourceStatus.WAITING_BROADCAST))
        )
    }

    @Test
    fun `1 つでも切断していれば切断を優先する`() {
        val statuses = listOf(SourceStatus.CONNECTED, SourceStatus.DISCONNECTED)

        assertEquals(SourceStatus.DISCONNECTED, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `エラーは切断より優先する`() {
        val statuses = listOf(SourceStatus.DISCONNECTED, SourceStatus.ERROR, SourceStatus.CONNECTED)

        assertEquals(SourceStatus.ERROR, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `全部が待機なら待機`() {
        val statuses = listOf(SourceStatus.WAITING, SourceStatus.WAITING)

        assertEquals(SourceStatus.WAITING, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `待機とつながっている取得先が混ざれば接続中`() {
        val statuses = listOf(SourceStatus.WAITING, SourceStatus.CONNECTED)

        assertEquals(SourceStatus.CONNECTED, SourceStatus.aggregate(statuses))
    }

    @Test
    fun `対処が要る状態だけが異常扱い`() {
        assertTrue(SourceStatus.WAITING.isHealthy)
        assertTrue(SourceStatus.CONNECTED.isHealthy)
        assertTrue(SourceStatus.WAITING_BROADCAST.isHealthy)
        assertFalse(SourceStatus.DISCONNECTED.isHealthy)
        assertFalse(SourceStatus.ERROR.isHealthy)
    }
}
