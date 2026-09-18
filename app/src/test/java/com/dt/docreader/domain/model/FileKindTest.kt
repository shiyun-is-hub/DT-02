package com.dt.docreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FileKind] 单元测试。
 *
 * 审计要求：TXT / MD / JSON / XML / CSV / CODE 均需测试，
 * 且**大小写扩展名**都要正确识别。
 */
class FileKindTest {

    @Test
    fun `TXT 扩展名识别（含大小写）`() {
        assertEquals(FileKind.TXT, FileKind.fromExtension("a.txt"))
        assertEquals(FileKind.TXT, FileKind.fromExtension("a.TXT"))
        assertEquals(FileKind.TXT, FileKind.fromExtension("a.Txt"))
        assertEquals(FileKind.TXT, FileKind.fromExtension("a.log"))
        assertEquals(FileKind.TXT, FileKind.fromExtension("a.LOG"))
    }

    @Test
    fun `Markdown 扩展名识别（含大小写）`() {
        assertEquals(FileKind.MARKDOWN, FileKind.fromExtension("a.md"))
        assertEquals(FileKind.MARKDOWN, FileKind.fromExtension("a.MD"))
        assertEquals(FileKind.MARKDOWN, FileKind.fromExtension("a.Markdown"))
        assertEquals(FileKind.MARKDOWN, FileKind.fromExtension("a.mkd"))
    }

    @Test
    fun `JSON 扩展名识别（含大小写）`() {
        assertEquals(FileKind.JSON, FileKind.fromExtension("a.json"))
        assertEquals(FileKind.JSON, FileKind.fromExtension("a.JSON"))
        assertEquals(FileKind.JSON, FileKind.fromExtension("a.Json"))
    }

    @Test
    fun `XML 扩展名识别（含大小写）`() {
        assertEquals(FileKind.XML, FileKind.fromExtension("a.xml"))
        assertEquals(FileKind.XML, FileKind.fromExtension("a.XML"))
        assertEquals(FileKind.XML, FileKind.fromExtension("a.svg"))
    }

    @Test
    fun `CSV 扩展名识别（含大小写）`() {
        assertEquals(FileKind.CSV, FileKind.fromExtension("a.csv"))
        assertEquals(FileKind.CSV, FileKind.fromExtension("a.CSV"))
        assertEquals(FileKind.CSV, FileKind.fromExtension("a.tsv"))
    }

    @Test
    fun `CODE 扩展名识别（含大小写）`() {
        assertEquals(FileKind.CODE, FileKind.fromExtension("a.kt"))
        assertEquals(FileKind.CODE, FileKind.fromExtension("a.KT"))
        assertEquals(FileKind.CODE, FileKind.fromExtension("a.Java"))
        assertEquals(FileKind.CODE, FileKind.fromExtension("a.PY"))
        assertEquals(FileKind.CODE, FileKind.fromExtension("a.rs"))
    }

    // ---------- 边界 ----------

    @Test
    fun `无扩展名返回 UNKNOWN`() {
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension("README"))
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension("Dockerfile"))
    }

    @Test
    fun `未知扩展名返回 UNKNOWN`() {
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension("a.xyz123"))
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension("a."))
    }

    @Test
    fun `空文件名不崩溃`() {
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension(""))
    }

    @Test
    fun `多点文件名取最后一段扩展名`() {
        assertEquals(FileKind.TXT, FileKind.fromExtension("archive.tar.txt"))
        assertEquals(FileKind.UNKNOWN, FileKind.fromExtension("archive.tar.gz"))
    }

    @Test
    fun `隐藏文件（点开头）按扩展名归类`() {
        // ".gitignore" 的 substringAfterLast('.') 得到 "gitignore" -> CONFIG
        assertEquals(FileKind.CONFIG, FileKind.fromExtension(".gitignore"))
        assertEquals(FileKind.CONFIG, FileKind.fromExtension(".editorconfig"))
    }

    @Test
    fun `Office 与 PDF 扩展名识别`() {
        assertEquals(FileKind.WORD_DOCX, FileKind.fromExtension("a.docx"))
        assertEquals(FileKind.WORD_DOC, FileKind.fromExtension("a.doc"))
        assertEquals(FileKind.PPT_PPTX, FileKind.fromExtension("a.pptx"))
        assertEquals(FileKind.PPT_PPT, FileKind.fromExtension("a.ppt"))
        assertEquals(FileKind.PDF, FileKind.fromExtension("a.pdf"))
        assertEquals(FileKind.PDF, FileKind.fromExtension("a.PDF"))
    }

    @Test
    fun `isTextFamily 只对文本家族为真`() {
        assertEquals(true, FileKind.TXT.isTextFamily)
        assertEquals(true, FileKind.MARKDOWN.isTextFamily)
        assertEquals(true, FileKind.CODE.isTextFamily)
        assertEquals(true, FileKind.JSON.isTextFamily)
        assertEquals(false, FileKind.PDF.isTextFamily)
        assertEquals(false, FileKind.WORD_DOCX.isTextFamily)
        assertEquals(false, FileKind.UNKNOWN.isTextFamily)
    }

    // ---------- Magic Number ----------

    @Test
    fun `PDF magic number 被识别`() {
        val head = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34)
        assertEquals(FileKind.PDF, FileKind.fromMagicNumber(head))
    }

    @Test
    fun `ZIP 容器 magic number 不崩溃`() {
        val head = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00)
        // docx/pptx/xlsx 共用 PK 头，实现返回占位值，由扩展名细化
        assertEquals(FileKind.WORD_DOCX, FileKind.fromMagicNumber(head))
    }

    @Test
    fun `过短的文件头返回 null`() {
        assertEquals(null, FileKind.fromMagicNumber(byteArrayOf(0x25, 0x50)))
        assertEquals(null, FileKind.fromMagicNumber(ByteArray(0)))
    }
}