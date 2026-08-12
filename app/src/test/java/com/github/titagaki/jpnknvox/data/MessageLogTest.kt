package com.github.titagaki.jpnknvox.data

import org.junit.Assert.*
import org.junit.Test

/**
 * MessageLog データクラスおよび toLog() 拡張関数のユニットテスト
 */
class MessageLogTest {

    private val source = CommentSource(
        uuid = "source-uuid",
        type = SourceType.JPNKN,
        sourceId = "mamiko",
        color = 0xFF0B57D0.toInt()
    )

    @Test
    fun `toLog - 受信したコメントから正しく MessageLog を生成できる`() {
        val comment = jpnknComment(
            body = "テスト太郎<>sage<>2024/01/01<>テスト本文です<>",
            no = "42"
        )

        val log = comment.toLog(source)

        assertEquals("42", log.no)
        assertEquals("テスト太郎", log.name)
        assertEquals("テスト本文です", log.message)
        assertTrue("id は空であってはならない", log.id.isNotEmpty())
        assertTrue("timestamp は正の値", log.timestamp > 0)
    }

    @Test
    fun `toLog - 取得先の識別色が入る`() {
        val log = jpnknComment("名前<>sage<>日時<>本文<>", "1").toLog(source)

        assertEquals(0xFF0B57D0.toInt(), log.sourceColor)
    }

    @Test
    fun `toLog - id は毎回異なるUUIDが生成される`() {
        val comment = jpnknComment("名前<>sage<>日時<>本文<>", "1")

        val log1 = comment.toLog(source)
        val log2 = comment.toLog(source)

        assertNotEquals("id はユニークであること", log1.id, log2.id)
    }

    @Test
    fun `toLog - timestamp は現在時刻に近い値`() {
        val before = System.currentTimeMillis()
        val log = jpnknComment("名前<>sage<>日時<>本文<>", "1").toLog(source)
        val after = System.currentTimeMillis()

        assertTrue("timestamp は生成前後の範囲内", log.timestamp in before..after)
    }

    @Test
    fun `toLog - body のパーツ不足時はデフォルト値`() {
        val log = jpnknComment(body = "名前だけ", no = "99").toLog(source)

        assertEquals("99", log.no)
        assertEquals("名前だけ", log.name)
        assertEquals("", log.message) // パーツ不足で本文は空
    }

    @Test
    fun `toReceivedComment - 受信元の取得先を保つ`() {
        val comment = jpnknComment("名前<>sage<>日時<>本文<>", "1")

        assertEquals("source-uuid", comment.sourceUuid)
    }

    @Test
    fun `MessageLog の data class equality`() {
        val log1 = MessageLog(
            id = "abc",
            no = "1",
            name = "テスト",
            message = "メッセージ",
            timestamp = 1000L,
            sourceColor = 1
        )
        val log2 = MessageLog(
            id = "abc",
            no = "1",
            name = "テスト",
            message = "メッセージ",
            timestamp = 1000L,
            sourceColor = 1
        )

        assertEquals(log1, log2)
        assertEquals(log1.hashCode(), log2.hashCode())
    }

    @Test
    fun `MessageLog の data class inequality - id が異なる`() {
        val log1 = MessageLog("id1", "1", "名前", "本文", 1000L, 1)
        val log2 = MessageLog("id2", "1", "名前", "本文", 1000L, 1)

        assertNotEquals(log1, log2)
    }

    // ========================================
    // ヘルパー
    // ========================================

    private fun jpnknComment(body: String, no: String): ReceivedComment = JpnknMessage(
        body = body,
        no = no,
        bbsid = "test",
        threadkey = "key"
    ).toReceivedComment(source.uuid)
}
