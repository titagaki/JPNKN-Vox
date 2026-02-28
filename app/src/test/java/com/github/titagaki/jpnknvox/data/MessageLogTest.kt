package com.github.titagaki.jpnknvox.data

import org.junit.Assert.*
import org.junit.Test

/**
 * MessageLog データクラスおよび toLog() 拡張関数のユニットテスト
 */
class MessageLogTest {

    @Test
    fun `toLog - JpnknMessage から正しく MessageLog を生成できる`() {
        val msg = JpnknMessage(
            body = "テスト太郎<>sage<>2024/01/01<>テスト本文です<>",
            no = "42",
            bbsid = "mamiko",
            threadkey = "12345"
        )

        val log = msg.toLog()

        assertEquals("42", log.no)
        assertEquals("テスト太郎", log.name)
        assertEquals("テスト本文です", log.message)
        assertTrue("id は空であってはならない", log.id.isNotEmpty())
        assertTrue("timestamp は正の値", log.timestamp > 0)
    }

    @Test
    fun `toLog - id は毎回異なるUUIDが生成される`() {
        val msg = JpnknMessage(
            body = "名前<>sage<>日時<>本文<>",
            no = "1",
            bbsid = "test",
            threadkey = "key"
        )

        val log1 = msg.toLog()
        val log2 = msg.toLog()

        assertNotEquals("id はユニークであること", log1.id, log2.id)
    }

    @Test
    fun `toLog - timestamp は現在時刻に近い値`() {
        val before = System.currentTimeMillis()
        val msg = JpnknMessage(
            body = "名前<>sage<>日時<>本文<>",
            no = "1",
            bbsid = "test",
            threadkey = "key"
        )
        val log = msg.toLog()
        val after = System.currentTimeMillis()

        assertTrue("timestamp は生成前後の範囲内", log.timestamp in before..after)
    }

    @Test
    fun `toLog - body のパーツ不足時はデフォルト値`() {
        val msg = JpnknMessage(
            body = "名前だけ",
            no = "99",
            bbsid = "test",
            threadkey = "key"
        )

        val log = msg.toLog()

        assertEquals("99", log.no)
        assertEquals("名前だけ", log.name)
        assertEquals("", log.message) // パーツ不足で本文は空
    }

    @Test
    fun `MessageLog の data class equality`() {
        val log1 = MessageLog(
            id = "abc",
            no = "1",
            name = "テスト",
            message = "メッセージ",
            timestamp = 1000L
        )
        val log2 = MessageLog(
            id = "abc",
            no = "1",
            name = "テスト",
            message = "メッセージ",
            timestamp = 1000L
        )

        assertEquals(log1, log2)
        assertEquals(log1.hashCode(), log2.hashCode())
    }

    @Test
    fun `MessageLog の data class inequality - id が異なる`() {
        val log1 = MessageLog("id1", "1", "名前", "本文", 1000L)
        val log2 = MessageLog("id2", "1", "名前", "本文", 1000L)

        assertNotEquals(log1, log2)
    }
}

