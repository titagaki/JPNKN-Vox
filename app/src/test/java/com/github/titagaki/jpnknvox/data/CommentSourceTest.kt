package com.github.titagaki.jpnknvox.data

import com.github.titagaki.jpnknvox.config.AppConfig
import org.junit.Assert.*
import org.junit.Test

/**
 * コメント取得先の永続化と移行のテスト
 */
class CommentSourceTest {

    // ========================================
    // JSON の往復
    // ========================================

    @Test
    fun `listToJson と listFromJson で往復できる`() {
        val sources = listOf(
            CommentSource("uuid-1", SourceType.JPNKN, "niijima", 0xFF0B57D0.toInt()),
            CommentSource("uuid-2", SourceType.TWICAS, "pcast_live", 0xFFC7411F.toInt())
        )

        val restored = CommentSource.listFromJson(CommentSource.listToJson(sources))

        assertEquals(sources, restored)
    }

    @Test
    fun `listFromJson - 空の配列は空のリスト`() {
        assertTrue(CommentSource.listFromJson("[]").isEmpty())
    }

    @Test
    fun `listFromJson - 壊れた JSON は空のリスト`() {
        assertTrue(CommentSource.listFromJson("壊れた").isEmpty())
    }

    @Test
    fun `listFromJson - 種別が不明な要素は読み飛ばし、残りは保つ`() {
        // 将来 Twitch などを足したあとに戻した場合、知らない種別が残っていても
        // 設定全体が消えないようにする
        val json = """
            [{"uuid":"a","type":"twitch","sourceId":"ch","color":1},
             {"uuid":"b","type":"jpnkn","sourceId":"mamiko","color":2}]
        """.trimIndent()

        val restored = CommentSource.listFromJson(json)

        assertEquals(1, restored.size)
        assertEquals("mamiko", restored[0].sourceId)
    }

    @Test
    fun `listFromJson - ID が空の要素は読み飛ばす`() {
        val json = """[{"uuid":"a","type":"jpnkn","sourceId":"","color":1}]"""

        assertTrue(CommentSource.listFromJson(json).isEmpty())
    }

    @Test
    fun `listFromJson - 表示名を持っていた頃の設定も読める`() {
        // 表示名は廃止したが、残っていても他のフィールドは読めること
        val json = """[{"uuid":"a","type":"jpnkn","sourceId":"mamiko","name":"まみこ板","color":2}]"""

        val restored = CommentSource.listFromJson(json)

        assertEquals(1, restored.size)
        assertEquals("mamiko", restored[0].sourceId)
        assertEquals(2, restored[0].color)
    }

    // ========================================
    // 接続先の比較
    // ========================================

    @Test
    fun `connectsTo - 識別色だけ違う場合は同じ接続先`() {
        val before = CommentSource("u", SourceType.JPNKN, "niijima", 1)
        val after = before.copy(color = 2)

        assertTrue(after.connectsTo(before))
    }

    @Test
    fun `connectsTo - ID が違えば別の接続先`() {
        val before = CommentSource("u", SourceType.JPNKN, "niijima", 1)
        val after = before.copy(sourceId = "mamiko")

        assertFalse(after.connectsTo(before))
    }

    @Test
    fun `connectsTo - 種別が違えば別の接続先`() {
        val jpnkn = CommentSource("u", SourceType.JPNKN, "same", 1)
        val twicas = jpnkn.copy(type = SourceType.TWICAS)

        assertFalse(twicas.connectsTo(jpnkn))
    }

    // ========================================
    // 板 ID からの移行
    // ========================================

    @Test
    fun `migrateFromBoardId - 板 ID が jpnkn の取得先 1 件になる`() {
        val sources = CommentSource.migrateFromBoardId("mamiko")

        assertEquals(1, sources.size)
        assertEquals(SourceType.JPNKN, sources[0].type)
        assertEquals("mamiko", sources[0].sourceId)
        assertEquals(AppConfig.Source.PALETTE.first(), sources[0].color)
    }

    @Test
    fun `migrateFromBoardId - 板 ID が未設定なら空のリスト`() {
        assertTrue(CommentSource.migrateFromBoardId(null).isEmpty())
        assertTrue(CommentSource.migrateFromBoardId("").isEmpty())
        assertTrue(CommentSource.migrateFromBoardId("   ").isEmpty())
    }

    @Test
    fun `migrateFromBoardId - 呼ぶたびに同じ uuid になる`() {
        // 保存される前に読み直しても、別の取得先として扱われないようにする
        val first = CommentSource.migrateFromBoardId("mamiko")
        val second = CommentSource.migrateFromBoardId("mamiko")

        assertEquals(first[0].uuid, second[0].uuid)
    }

    // ========================================
    // 取得先の場所の表示
    // ========================================

    @Test
    fun `locationHint - 種別ごとに取得先の場所を表す`() {
        assertEquals("bbs/mamiko", SourceType.JPNKN.locationHint("mamiko"))
        assertEquals("twitcasting.tv/pcast_live", SourceType.TWICAS.locationHint("pcast_live"))
    }

    @Test
    fun `locationHint - ID が空なら何も出さない`() {
        // 入力例を見せるためのものではないので、未入力なら空にする
        // （その場合は入力欄の下に idFieldDescription を出す）
        assertEquals("", SourceType.JPNKN.locationHint(""))
        assertEquals("", SourceType.TWICAS.locationHint("  "))
    }

    @Test
    fun `idFieldDescription - 種別ごとに何を入れる欄かを説明する`() {
        SourceType.entries.forEach { type ->
            assertTrue(
                "${type.name} の説明が空でないこと",
                type.idFieldDescription.isNotBlank()
            )
        }
    }
}
