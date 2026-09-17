package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PPT 读取器（.pptx）—— 零依赖实现。
 *
 * pptx 是 ZIP + OOXML，每页幻灯片为 `ppt/slides/slideN.xml`：
 * - `a:p` 段落（一个文本框内的段落）
 * - `a:t` 文本
 * - `a:br` 换行
 *
 * 每个 slide 映射成一个 [Section]，内含 [Block.Slide]（标题 = 首个段落）。
 */
class PptReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean =
        kind == FileKind.PPT_PPTX || kind == FileKind.PPT_PPT

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        when (source.kind) {
            FileKind.PPT_PPT -> legacyModel(source)
            else -> readPptx(source)
        }
    }

    private fun legacyModel(source: FileSource) = DocumentModel(
        meta = DocumentMeta(
            fileName = source.fileName,
            fileSize = source.size,
            kind = source.kind,
            language = "PowerPoint 97-2003"
        ),
        sections = listOf(
            Section(
                title = "旧版 .ppt 格式",
                blocks = listOf(
                    Block.Heading(2, "无法解析 .ppt（旧版二进制格式）"),
                    Block.Paragraph(
                        "本阅读器支持 .pptx（Office 2007+ 的 ZIP+XML 格式）。\n" +
                            "旧版 .ppt 是 OLE2 复合二进制文档，需要重型解析库，暂不支持。\n\n" +
                            "解决办法：用 WPS / PowerPoint / LibreOffice 打开后「另存为 .pptx」，再重新打开。"
                    )
                )
            )
        )
    )

    private fun readPptx(source: FileSource): DocumentModel {
        val slidePattern = Regex("""^ppt/slides/slide(\d+)\.xml$""")

        val entries = source.openStream().use { input ->
            Ooxml.readEntries(input) { name -> slidePattern.matches(name) }
        }
        if (entries.isEmpty()) {
            throw IllegalStateException("不是有效的 .pptx 文件（未找到 ppt/slides/slideN.xml）")
        }

        val ordered = entries.entries
            .mapNotNull { (name, bytes) ->
                slidePattern.find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to bytes }
            }
            .sortedBy { it.first }

        val sections = ordered.mapIndexed { idx, (_, bytes) ->
            val paragraphs = parseSlide(bytes)
            val title = paragraphs.firstOrNull()
            val body = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
            Section(
                title = title ?: "幻灯片 ${idx + 1}",
                pageIndex = idx,
                blocks = listOf(Block.Slide(title = title, body = body))
            )
        }

        return DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = FileKind.PPT_PPTX,
                language = "演示文稿",
                pageCount = sections.size
            ),
            sections = sections
        )
    }

    /** 提取单页所有段落文本（按文档顺序）。 */
    private fun parseSlide(xml: ByteArray): List<String> {
        val paragraphs = ArrayList<String>(16)
        val buf = StringBuilder()
        var inText = false

        Ooxml.scan(xml) { ev ->
            when (ev) {
                is Ooxml.Event.Start -> when (ev.name) {
                    "p" -> buf.setLength(0)
                    "t" -> inText = true
                    "br" -> buf.append('\n')
                }
                is Ooxml.Event.Text -> if (inText) buf.append(ev.value)
                is Ooxml.Event.End -> when (ev.name) {
                    "t" -> inText = false
                    "p" -> {
                        val text = buf.toString().trim()
                        if (text.isNotEmpty()) paragraphs.add(text)
                        buf.setLength(0)
                    }
                }
            }
        }
        return paragraphs
    }
}