package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section
import com.dt.docreader.infra.EncodingDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 结构化文本读取器：处理 JSON / XML / HTML / CSV / YAML / 配置文件。
 *
 * 策略：
 * - 这些格式都是纯文本，直接整体作为 CodeBlock（带语言高亮）。
 * - 针对几类做「有信息的预处理」：
 *   · CSV/TSV -> 额外解析成 TableBlock，便于表格阅读
 *   · JSON    -> 额外统计顶层键数量
 *   · HTML    -> 额外抽取 <title>
 *   · XML     -> 额外抽取根元素名
 * - 其余保持原样高亮。
 */
class StructuredTextReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean = kind in setOf(
        FileKind.JSON, FileKind.XML, FileKind.HTML,
        FileKind.CSV, FileKind.YAML, FileKind.CONFIG
    )

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        val decoded = source.openStream().use { EncodingDetector.readAllText(it) }
        val text = decoded.text
        val lang = LanguageRegistry.fromFileName(source.fileName)
            ?: LanguageRegistry.fallbackFor(source.kind)

        val blocks = mutableListOf<Block>()

        // 摘要行
        val summary = buildString {
            append("格式：${lang.displayName}")
            val lines = text.count { it == '\n' } + 1
            append(" · $lines 行 · ${formatSize(source.size)}")
        }
        blocks.add(Block.Paragraph(summary))

        // 各格式的额外提示
        when (source.kind) {
            FileKind.CSV -> addCsvPreview(text, blocks)
            FileKind.JSON -> addJsonInfo(text, blocks)
            FileKind.HTML -> addHtmlTitle(text, blocks)
            FileKind.XML -> addXmlRoot(text, blocks)
            else -> Unit
        }

        blocks.add(Block.Heading(2, "内容"))
        blocks.add(Block.CodeBlock(language = lang.id, code = text))

        DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = source.kind,
                encoding = decoded.charset.name(),
                language = lang.displayName
            ),
            sections = listOf(Section(title = null, pageIndex = 0, blocks = blocks))
        )
    }

    /** CSV/TSV：解析前若干行成表格预览。 */
    private fun addCsvPreview(text: String, blocks: MutableList<Block>) {
        val sep = if (text.lineSequence().firstOrNull()?.contains('\t') == true) '\t' else ','
        val rows = text.lineSequence()
            .take(21)
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it, sep) }
            .toList()
        if (rows.size >= 2) {
            blocks.add(Block.Heading(2, "表格预览（前 ${rows.size - 1} 行数据）"))
            blocks.add(Block.TableBlock(rows))
        }
    }

    private fun splitCsvLine(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = !inQuotes
                }
                c == sep && !inQuotes -> { out.add(sb.toString().trim()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    /** JSON：统计顶层键数量（极简，不引依赖）。 */
    private fun addJsonInfo(text: String, blocks: MutableList<Block>) {
        val trimmed = text.trim()
        val info = when {
            trimmed.startsWith("{") -> {
                val keys = Regex(""""(?:[^"\\]|\\.)*"\s*:""").findAll(trimmed).count()
                "JSON 对象 · 约 $keys 个键值对"
            }
            trimmed.startsWith("[") -> {
                val items = trimmed.count { it == ',' } + 1
                "JSON 数组 · 约 $items 个元素"
            }
            else -> return
        }
        blocks.add(Block.Paragraph("结构：$info"))
    }

    /** HTML：抽取标题。 */
    private fun addHtmlTitle(text: String, blocks: MutableList<Block>) {
        val title = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
        if (!title.isNullOrEmpty()) {
            blocks.add(Block.Paragraph("页面标题：$title"))
        }
    }

    /** XML：抽取根元素名。 */
    private fun addXmlRoot(text: String, blocks: MutableList<Block>) {
        val root = Regex("""<\?xml[^>]*\?>\s*<([\w:.-]+)""").find(text)?.groupValues?.get(1)
            ?: Regex("""<([\w:.-]+)""").find(text)?.groupValues?.get(1)
        if (!root.isNullOrEmpty()) {
            blocks.add(Block.Paragraph("根元素：<$root>"))
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
    }
}