package com.dt.docreader.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TextChunker] 单元测试。
 *
 * 重点验证**无损性**：分块后拼接必须与原文逐字符一致，
 * 这是分块编辑正确性的前提（保存时会用 join 还原全文）。
 */
class TextChunkerTest {

    // ---------- 无损性（最关键） ----------

    @Test
    fun `小文本不分块且原样返回`() {
        val text = "hello\nworld"
        val chunks = TextChunker.chunk(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
        assertFalse(TextChunker.shouldChunk(text))
    }

    @Test
    fun `空文本返回单个空块`() {
        val chunks = TextChunker.chunk("")
        assertEquals(listOf(""), chunks)
        assertEquals("", TextChunker.join(chunks))
    }

    @Test
    fun `大文本分块后拼接与原文一致`() {
        // 构造超过阈值的文本：每行固定内容，行数足够多
        val sb = StringBuilder()
        repeat(200_000) { sb.append("line-").append(it).append("-abcdefghij\n") }
        val text = sb.toString()
        assertTrue("测试数据应触发分块", TextChunker.shouldChunk(text))

        val chunks = TextChunker.chunk(text)
        assertTrue("应切成多块", chunks.size > 1)
        assertEquals("分块后拼接必须无损", text, TextChunker.join(chunks))
    }

    @Test
    fun `无结尾换行的文本也能无损还原`() {
        val sb = StringBuilder()
        repeat(100_000) { sb.append("x").append(it).append("\n") }
        sb.append("最后一行没有换行符")
        val text = sb.toString()

        val chunks = TextChunker.chunk(text)
        assertEquals(text, TextChunker.join(chunks))
    }

    @Test
    fun `连续空行与空行边界不丢字符`() {
        val sb = StringBuilder()
        repeat(60_000) { sb.append("a\n\n\n") }
        val text = sb.toString()

        val chunks = TextChunker.chunk(text)
        assertEquals(text, TextChunker.join(chunks))
    }

    @Test
    fun `单行超长文本（无换行）也能无损分块`() {
        val text = "x".repeat(TextChunker.CHUNK_THRESHOLD_BYTES)
        val chunks = TextChunker.chunk(text)
        assertEquals(text, TextChunker.join(chunks))
    }

    // ---------- join 的边界 ----------

    @Test
    fun `join 单块直接返回该块`() {
        assertEquals("abc", TextChunker.join(listOf("abc")))
    }

    @Test
    fun `join 空列表返回空串`() {
        assertEquals("", TextChunker.join(emptyList()))
    }

    // ---------- 行号偏移 ----------

    @Test
    fun `lineOffsetOf 累加前面所有块的行数`() {
        val chunks = listOf("a\nb\n", "c\nd\n", "e\n")
        assertEquals(0, TextChunker.lineOffsetOf(chunks, 0))
        assertEquals(2, TextChunker.lineOffsetOf(chunks, 1))
        assertEquals(4, TextChunker.lineOffsetOf(chunks, 2))
    }

    @Test
    fun `lineOffsetOf 越界索引不崩溃`() {
        val chunks = listOf("a\n", "b\n")
        assertEquals(2, TextChunker.lineOffsetOf(chunks, 99))
    }

    // ---------- 阈值判定 ----------

    @Test
    fun `shouldChunk 阈值边界`() {
        // 阈值按 length >= CHUNK_THRESHOLD_BYTES / 2 判定（中英混合估算）
        val justBelow = "a".repeat(TextChunker.CHUNK_THRESHOLD_BYTES / 2 - 1)
        val justAbove = "a".repeat(TextChunker.CHUNK_THRESHOLD_BYTES / 2)
        assertFalse(TextChunker.shouldChunk(justBelow))
        assertTrue(TextChunker.shouldChunk(justAbove))
    }
}