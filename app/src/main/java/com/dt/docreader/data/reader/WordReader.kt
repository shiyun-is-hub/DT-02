package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Word 读取器（.docx）—— 零依赖实现。
 *
 * docx 是 ZIP + OOXML，正文在 `word/document.xml`：
 * - `w:p`         段落
 * - `w:t`         文本
 * - `w:br`        换行
 * - `w:tab`       制表符
 * - `w:pStyle`    样式 → 识别 Heading / Title
 * - `w:numPr`     编号 → 识别列表
 * - `w:tbl/tr/tc` 表格
 */
class WordReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean =
        kind == FileKind.WORD_DOCX || kind == FileKind.WORD_DOC

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        when (source.kind) {
            FileKind.WORD_DOC -> legacyModel(source, "Word 97-2003", ".doc", ".docx")
            else -> readDocx(source)
        }
    }

    private fun legacyModel(source: FileSource, label: String, ext: String, target: String) =
        DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = source.kind,
                language = label
            ),
            sections = listOf(
                Section(
                    title = "旧版 $ext 格式",
                    blocks = listOf(
                        Block.Heading(2, "无法解析 $ext（旧版二进制格式）"),
                        Block.Paragraph(
                            "本阅读器支持 $target（Office 2007+ 的 ZIP+XML 格式）。\n" +
                                "旧版 $ext 是 OLE2 复合二进制文档，需要重型解析库，暂不支持。\n\n" +
                                "解决办法：用 WPS / Office / LibreOffice 打开后「另存为 $target」，再重新打开。"
                        )
                    )
                )
            )
        )

    private fun readDocx(source: FileSource): DocumentModel {
        val entries = source.openStream().use { input ->
            Ooxml.readEntries(input) { name ->
                name == "word/document.xml" || name == "docProps/core.xml"
            }
        }
        val docBytes = entries["word/document.xml"]
            ?: throw IllegalStateException("不是有效的 .docx 文件（缺少 word/document.xml）")

        val blocks = parseDocument(docBytes)
        val title = entries["docProps/core.xml"]?.let { extractTitle(it) }

        return DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = FileKind.WORD_DOCX,
                language = "Word 文档"
            ),
            sections = listOf(
                Section(title = title ?: source.fileName, pageIndex = 0, blocks = blocks)
            )
        )
    }

    // ---------------- 正文解析 ----------------

    /**
     * 单遍扫描 document.xml，按状态机累积段落 / 表格。
     *
     * 状态：
     * - [inTable] 为真时，文本进入当前单元格
     * - 否则进入当前段落缓冲
     */
    private fun parseDocument(xml: ByteArray): List<Block> {
        val blocks = ArrayList<Block>(64)

        // 段落状态
        val paraBuf = StringBuilder()
        var headingLevel = 0
        var isListItem = false

        // 表格状态
        var inTable = false
        var currentRow: MutableList<String>? = null
        var cellBuf: StringBuilder? = null
        var tblDepth = 0

        // 当前文本是否属于 w:t（避免收集到无关文本）
        var inText = false

        fun flushParagraph() {
            val text = paraBuf.toString().trim()
            paraBuf.setLength(0)
            val level = headingLevel
            val isList = isListItem
            headingLevel = 0
            isListItem = false
            if (text.isEmpty()) return
            blocks.add(
                when {
                    level > 0 -> Block.Heading(level, text)
                    isList -> Block.BulletList(listOf(text), ordered = false)
                    else -> Block.Paragraph(text)
                }
            )
        }

        Ooxml.scan(xml) { ev ->
            when (ev) {
                is Ooxml.Event.Start -> when (ev.name) {
                    "tbl" -> {
                        flushParagraph()
                        inTable = true
                        tblDepth = 1
                    }
                    "tr" -> if (inTable) currentRow = ArrayList()
                    "tc" -> if (inTable) cellBuf = StringBuilder()
                    "p" -> {
                        if (inTable) {
                            // 单元格内段落：只重置缓冲
                            cellBuf?.let { if (it.isNotEmpty()) it.append('\n') }
                        } else {
                            paraBuf.setLength(0)
                            headingLevel = 0
                            isListItem = false
                        }
                    }
                    "pStyle" -> if (!inTable) {
                        val v = ev.attrs["val"] ?: ""
                        headingLevel = when {
                            v.startsWith("Heading", true) || v.startsWith("标题", true) ->
                                v.filter { it.isDigit() }.firstOrNull()?.digitToInt()?.coerceIn(1, 6) ?: 2
                            v.equals("Title", true) -> 1
                            v.equals("Subtitle", true) -> 3
                            else -> 0
                        }
                    }
                    "numPr" -> if (!inTable) isListItem = true
                    "br", "cr" -> {
                        if (inTable) cellBuf?.append('\n') else paraBuf.append('\n')
                    }
                    "tab" -> {
                        if (inTable) cellBuf?.append('\t') else paraBuf.append('\t')
                    }
                    "t" -> inText = true
                }

                is Ooxml.Event.Text -> if (inText) {
                    if (inTable) cellBuf?.append(ev.value) else paraBuf.append(ev.value)
                }

                is Ooxml.Event.End -> when (ev.name) {
                    "t" -> inText = false
                    "tc" -> if (inTable) {
                        cellBuf?.let { currentRow?.add(it.toString().trim()) }
                        cellBuf = null
                    }
                    "tr" -> if (inTable) {
                        currentRow?.let { if (it.isNotEmpty()) blocks.add(Block.TableBlock(listOf(it))) }
                        currentRow = null
                    }
                    "tbl" -> {
                        inTable = false
                        tblDepth = 0
                        // 合并连续的同一表格行（上面是逐行 add，这里统一合并）
                        mergeAdjacentTableRows(blocks)
                    }
                    "p" -> if (!inTable) flushParagraph()
                }
            }
        }
        if (!inTable) flushParagraph()
        mergeAdjacentTableRows(blocks)
        return blocks
    }

    /** 把连续的 `TableBlock(单行)` 合并成一个完整 TableBlock。 */
    private fun mergeAdjacentTableRows(blocks: MutableList<Block>) {
        var i = blocks.size - 1
        // 从后往前找连续的 TableBlock
        var end = -1
        while (i >= 0) {
            if (blocks[i] is Block.TableBlock) {
                if (end == -1) end = i
            } else {
                break
            }
            i--
        }
        if (end == -1) return
        val start = i + 1
        if (end == start) return
        val rows = ArrayList<List<String>>()
        for (k in start..end) {
            rows.addAll((blocks[k] as Block.TableBlock).rows)
        }
        for (k in end downTo start) blocks.removeAt(k)
        blocks.add(start, Block.TableBlock(rows))
    }

    private fun extractTitle(xml: ByteArray): String? {
        var title: String? = null
        var capturing = false
        val sb = StringBuilder()
        Ooxml.scan(xml) { ev ->
            when (ev) {
                is Ooxml.Event.Start -> if (ev.name == "title") {
                    capturing = true
                    sb.setLength(0)
                }
                is Ooxml.Event.Text -> if (capturing) sb.append(ev.value)
                is Ooxml.Event.End -> if (ev.name == "title" && capturing) {
                    title = sb.toString().trim().ifEmpty { null }
                    capturing = false
                }
            }
        }
        return title
    }
}