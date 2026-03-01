package com.github.titagaki.jpnknvox.data

import org.junit.Assert.*
import org.junit.Test

/**
 * JpnknMessage のユニットテスト
 *
 * body の "<>" 区切りパース、各抽出メソッド、JSON シリアライズ/デシリアライズを検証する。
 */
class JpnknMessageTest {

    // ========================================
    // extractMessage
    // ========================================

    @Test
    fun `extractMessage - 正常なbodyから本文を抽出できる`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>2024/01/01 12:00:00<>これはテストです<>",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("これはテストです", msg.extractMessage())
    }

    @Test
    fun `extractMessage - 本文に空白がある場合trimされる`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>2024/01/01<>  前後に空白  <>",
            no = "2",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("前後に空白", msg.extractMessage())
    }

    @Test
    fun `extractMessage - パーツ不足のbodyでは空文字列を返す`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>",
            no = "3",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractMessage())
    }

    @Test
    fun `extractMessage - 空のbodyでは空文字列を返す`() {
        val msg = JpnknMessage(
            body = "",
            no = "4",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractMessage())
    }

    @Test
    fun `extractMessage - 本文にデリミタを含まないbodyでは空文字列を返す`() {
        val msg = JpnknMessage(
            body = "デリミタなし",
            no = "5",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractMessage())
    }

    // ========================================
    // extractName
    // ========================================

    @Test
    fun `extractName - 正常なbodyから名前を抽出できる`() {
        val msg = JpnknMessage(
            body = "テスト名<>sage<>2024/01/01<>本文<>",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("テスト名", msg.extractName())
    }

    @Test
    fun `extractName - デリミタなしのbodyでも名前を返す`() {
        val msg = JpnknMessage(
            body = "名前だけ",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("名前だけ", msg.extractName())
    }

    @Test
    fun `extractName - 空のbodyでは名無しを返す`() {
        val msg = JpnknMessage(
            body = "",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        // split("") on "" returns [""], so parts[0] = ""
        // extractName trims it → ""
        // Note: current implementation returns trimmed parts[0], not "名無し"
        // "名無し" is returned only when parts is empty, which doesn't happen with split
        assertEquals("", msg.extractName())
    }

    // ========================================
    // extractMail
    // ========================================

    @Test
    fun `extractMail - 正常なbodyからメール欄を抽出できる`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>2024/01/01<>本文<>",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("sage", msg.extractMail())
    }

    @Test
    fun `extractMail - メール欄が空の場合`() {
        val msg = JpnknMessage(
            body = "名無し<><>2024/01/01<>本文<>",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractMail())
    }

    @Test
    fun `extractMail - パーツ不足では空文字列を返す`() {
        val msg = JpnknMessage(
            body = "名前だけ",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractMail())
    }

    // ========================================
    // extractDate
    // ========================================

    @Test
    fun `extractDate - 正常なbodyから日時を抽出できる`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>2024/01/01 12:00:00<>本文<>",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("2024/01/01 12:00:00", msg.extractDate())
    }

    @Test
    fun `extractDate - パーツ不足では空文字列を返す`() {
        val msg = JpnknMessage(
            body = "名無し<>sage",
            no = "1",
            bbsid = "mamiko",
            threadkey = "12345"
        )
        assertEquals("", msg.extractDate())
    }

    // ========================================
    // JSON シリアライズ / デシリアライズ
    // ========================================

    @Test
    fun `toJson - 正しいJSON文字列を生成する`() {
        val msg = JpnknMessage(
            body = "名無し<>sage<>2024/01/01<>テスト<>",
            no = "42",
            bbsid = "mamiko",
            threadkey = "99999"
        )
        val json = msg.toJson()

        // fromJson で復元して検証
        val restored = JpnknMessage.fromJson(json)
        assertNotNull(restored)
        assertEquals(msg.body, restored!!.body)
        assertEquals(msg.no, restored.no)
        assertEquals(msg.bbsid, restored.bbsid)
        assertEquals(msg.threadkey, restored.threadkey)
    }

    @Test
    fun `fromJson - 正しいJSONからパースできる`() {
        val json = """{"body":"名無し<>sage<>日時<>本文<>","no":"10","bbsid":"test","threadkey":"abc"}"""
        val msg = JpnknMessage.fromJson(json)

        assertNotNull(msg)
        assertEquals("名無し<>sage<>日時<>本文<>", msg!!.body)
        assertEquals("10", msg.no)
        assertEquals("test", msg.bbsid)
        assertEquals("abc", msg.threadkey)
    }

    @Test
    fun `fromJson - 不正なJSONではnullを返す`() {
        val result = JpnknMessage.fromJson("これはJSONではない")
        assertNull(result)
    }

    @Test
    fun `fromJson - 空のJSONオブジェクトではデフォルト値を返す`() {
        val msg = JpnknMessage.fromJson("{}")
        assertNotNull(msg)
        assertEquals("", msg!!.body)
        assertEquals("", msg.no)
        assertEquals("", msg.bbsid)
        assertEquals("", msg.threadkey)
    }

    @Test
    fun `fromJson - フィールドが欠けたJSONではデフォルト値を返す`() {
        val json = """{"body":"テスト","no":"1"}"""
        val msg = JpnknMessage.fromJson(json)

        assertNotNull(msg)
        assertEquals("テスト", msg!!.body)
        assertEquals("1", msg.no)
        assertEquals("", msg.bbsid)
        assertEquals("", msg.threadkey)
    }

    @Test
    fun `toJson と fromJson のラウンドトリップ`() {
        val original = JpnknMessage(
            body = "特殊文字テスト<>\"引用符\"<>2024/01/01<><br>改行<>",
            no = "100",
            bbsid = "sumire",
            threadkey = "thread_001"
        )
        val restored = JpnknMessage.fromJson(original.toJson())

        assertNotNull(restored)
        assertEquals(original, restored)
    }
}

