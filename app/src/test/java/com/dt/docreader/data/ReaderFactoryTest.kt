package com.dt.docreader.data

import com.dt.docreader.domain.model.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReaderFactory] 单元测试。
 *
 * 审计要求：TXT / Markdown / Code / JSON / XML / CSV 全部可解析；
 * 扩展名大小写不敏感；无扩展名 / 多点 / 中文名 / 空格 / Unicode 文件名不崩溃。
 */
class ReaderFactoryTest {

    // ---------- 已实现的 Reader 必须能解析 ----------

    @Test
    fun `TXT 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.TXT))
    }

    @Test
    fun `Markdown 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.MARKDOWN))
    }

    @Test
    fun `Code 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.CODE))
    }

    @Test
    fun `JSON 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.JSON))
    }

    @Test
    fun `XML 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.XML))
    }

    @Test
    fun `CSV 有对应 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.CSV))
    }

    @Test
    fun `HTML YAML CONFIG 也有 Reader`() {
        assertNotNull(ReaderFactory.resolve(FileKind.HTML))
        assertNotNull(ReaderFactory.resolve(FileKind.YAML))
        assertNotNull(ReaderFactory.resolve(FileKind.CONFIG))
    }

    @Test
    fun `UNKNOWN 没有 Reader（必须安全失败）`() {
        assertEquals(null, ReaderFactory.resolve(FileKind.UNKNOWN))
    }

    // ---------- isSupported 大小写不敏感 ----------

    @Test
    fun `isSupported 对小写扩展名为真`() {
        assertTrue(ReaderFactory.isSupported("a.txt"))
        assertTrue(ReaderFactory.isSupported("a.md"))
        assertTrue(ReaderFactory.isSupported("a.json"))
        assertTrue(ReaderFactory.isSupported("a.xml"))
        assertTrue(ReaderFactory.isSupported("a.csv"))
        assertTrue(ReaderFactory.isSupported("a.kt"))
    }

    @Test
    fun `isSupported 对大写扩展名为真`() {
        assertTrue(ReaderFactory.isSupported("a.TXT"))
        assertTrue(ReaderFactory.isSupported("a.MD"))
        assertTrue(ReaderFactory.isSupported("a.JSON"))
        assertTrue(ReaderFactory.isSupported("a.XML"))
        assertTrue(ReaderFactory.isSupported("a.CSV"))
        assertTrue(ReaderFactory.isSupported("a.KT"))
    }

    @Test
    fun `isSupported 对混合大小写为真`() {
        assertTrue(ReaderFactory.isSupported("a.TxT"))
        assertTrue(ReaderFactory.isSupported("a.MarkDown"))
        assertTrue(ReaderFactory.isSupported("a.JsOn"))
    }

    @Test
    fun `isSupported 对未知扩展名为假`() {
        assertFalse(ReaderFactory.isSupported("a.xyz"))
        assertFalse(ReaderFactory.isSupported("a.exe"))
        assertFalse(ReaderFactory.isSupported("a.bin"))
    }

    // ---------- 文件名边界 ----------

    @Test
    fun `无扩展名文件不崩溃`() {
        val r = runCatching { ReaderFactory.isSupported("README") }
        assertTrue("无扩展名不应抛异常", r.isSuccess)
    }

    @Test
    fun `多点文件名取最后一段扩展名`() {
        assertTrue(ReaderFactory.isSupported("archive.tar.txt"))
        assertFalse(ReaderFactory.isSupported("archive.txt.gz"))
    }

    @Test
    fun `中文文件名不崩溃`() {
        assertTrue(ReaderFactory.isSupported("测试文档.txt"))
        assertTrue(ReaderFactory.isSupported("笔记.md"))
        assertTrue(ReaderFactory.isSupported("数据.json"))
    }

    @Test
    fun `含空格的文件名不崩溃`() {
        assertTrue(ReaderFactory.isSupported("my document.txt"))
        assertTrue(ReaderFactory.isSupported("a b c.md"))
    }

    @Test
    fun `Unicode 文件名不崩溃`() {
        assertTrue(ReaderFactory.isSupported("日本語ファイル.txt"))
        assertTrue(ReaderFactory.isSupported("Русский.json"))
        assertTrue(ReaderFactory.isSupported("emoji😀.md"))
        assertTrue(ReaderFactory.isSupported("café.txt"))
    }

    @Test
    fun `空文件名不崩溃`() {
        val r = runCatching { ReaderFactory.isSupported("") }
        assertTrue(r.isSuccess)
        assertFalse(r.getOrDefault(true))
    }

    @Test
    fun `只有点的文件名不崩溃`() {
        val r = runCatching { ReaderFactory.isSupported(".") }
        assertTrue(r.isSuccess)
    }

    @Test
    fun `isSupported 缓存不改变结果（重复调用一致）`() {
        val name = "repeat-test.txt"
        val first = ReaderFactory.isSupported(name)
        repeat(1000) { assertEquals(first, ReaderFactory.isSupported(name)) }
    }

    // ---------- SUPPORTED_EXTENSIONS 集合 ----------

    @Test
    fun `SUPPORTED_EXTENSIONS 全为小写`() {
        ReaderFactory.SUPPORTED_EXTENSIONS.forEach { ext ->
            assertEquals("扩展名应统一小写: $ext", ext, ext.lowercase())
        }
    }

    @Test
    fun `SUPPORTED_EXTENSIONS 不含空串`() {
        assertFalse(ReaderFactory.SUPPORTED_EXTENSIONS.contains(""))
    }
}