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
 * Markdown 读取器：把 Markdown 解析为统一 IR Block。
 *
 * 覆盖的语法（P2 范围）：
 * - ATX 标题 `#` ~ `######`
 * - Setext 标题（`===` / `---` 下划线式）
 * - 无序列表 `-` `*` `+`、有序列表 `1.`
 * - 围栏代码块 ```lang ... ```
 * - 引用块 `>`
 * - 水平分隔线 `---` `***` `___`
 * - GFM 表格（`| a | b |` + 分隔行）
 * - 普通段落
 *
 * 刻意不做的：内联样式（粗体/斜体/链接）不在这里拆解——
 * 那属于「渲染层」的事，由 UI 用 Markwon 或自绘 span 处理。
 * 本层只负责「块级结构」，保持 IR 干净。
 */
class MarkdownReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean = kind == FileKind.MARKDOWN

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        val decoded = source.openStream().use { EncodingDetector.readAllText(it) }
        val blocks = parse(decoded.text)

        DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = FileKind.MARKDOWN,
                encoding = decoded.charset.name(),
                language = "Markdown",
                truncated = decoded.truncated
            ),
            sections = listOf(Section(title = null, pageIndex = 0, blocks = blocks)),
            rawText = decoded.text
        )
    }

    private fun parse(text: String): List<Block> {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val blocks = mutableListOf<Block>()
        val para = StringBuilder()

        fun flushPara() {
            if (para.isNotBlank()) {
                blocks.add(Block.Paragraph(stripInline(para.toString().trim())))
            }
            para.clear()
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val trimmed = line.trim()

            // ---- 围栏代码块 ----
            val fence = Regex("""^\s*(`{3,}|~{3,})\s*([\w+-]*)""").find(line)
            if (fence != null) {
                flushPara()
                val marker = fence.groupValues[1]
                val lang = fence.groupValues[2].ifBlank { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker.take(3))) {
                    code.append(lines[i].trimEnd('\r')).append('\n')
                    i++
                }
                blocks.add(Block.CodeBlock(language = lang, code = code.toString().trimEnd('\n')))
                i++ // 跳过结束围栏
                continue
            }

            // ---- ATX 标题 ----
            val atx = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""").find(line)
            if (atx != null) {
                flushPara()
                blocks.add(Block.Heading(atx.groupValues[1].length, stripInline(atx.groupValues[2])))
                i++
                continue
            }

            // ---- Setext 标题 ----
            if (trimmed.isNotEmpty() && i + 1 < lines.size) {
                val next = lines[i + 1].trim()
                if (next.matches(Regex("""^=+$""")) || next.matches(Regex("""^-{2,}$"""))) {
                    if (!isSeparator(trimmed)) {
                        flushPara()
                        val level = if (next.startsWith("=")) 1 else 2
                        blocks.add(Block.Heading(level, stripInline(trimmed)))
                        i += 2
                        continue
                    }
                }
            }

            // ---- 水平分隔线 ----
            if (isSeparator(trimmed)) {
                flushPara()
                i++
                continue
            }

            // ---- 引用块 ----
            if (trimmed.startsWith(">")) {
                flushPara()
                val quote = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quote.append(lines[i].trim().removePrefix(">").trim()).append('\n')
                    i++
                }
                blocks.add(Block.Paragraph("｜ " + quote.toString().trim().replace("\n", "\n｜ ")))
                continue
            }

            // ---- 表格 ----
            if (trimmed.startsWith("|") && i + 1 < lines.size &&
                lines[i + 1].trim().matches(Regex("""^\|?[\s:|-]+\|?$""")) &&
                lines[i + 1].contains("-")
            ) {
                flushPara()
                val tableRows = mutableListOf<List<String>>()
                tableRows.add(splitRow(trimmed))
                i += 2 // 跳过分隔行
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableRows.add(splitRow(lines[i].trim()))
                    i++
                }
                blocks.add(Block.TableBlock(tableRows))
                continue
            }

            // ---- 列表（连续项合并为一个 BulletList） ----
            val ul = Regex("""^\s*[-*+]\s+(.*)$""").find(line)
            val ol = Regex("""^\s*\d+[.)]\s+(.*)$""").find(line)
            if (ul != null || ol != null) {
                flushPara()
                val ordered = ol != null
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val cur = lines[i]
                    val m = if (ordered) Regex("""^\s*\d+[.)]\s+(.*)$""").find(cur)
                    else Regex("""^\s*[-*+]\s+(.*)$""").find(cur)
                    if (m != null) {
                        items.add(stripInline(m.groupValues[1].trim()))
                        i++
                    } else if (cur.trim().isEmpty()) {
                        break
                    } else if (cur.startsWith("  ") && items.isNotEmpty()) {
                        // 简单处理续行
                        items[items.size - 1] = items.last() + " " + stripInline(cur.trim())
                        i++
                    } else break
                }
                blocks.add(Block.BulletList(items, ordered = ordered))
                continue
            }

            // ---- 空行 ----
            if (trimmed.isEmpty()) {
                flushPara()
                i++
                continue
            }

            // ---- 普通段落行 ----
            if (para.isNotEmpty()) para.append(' ')
            para.append(trimmed)
            i++
        }
        flushPara()
        return blocks
    }

    private fun isSeparator(s: String): Boolean =
        s.matches(Regex("""^([-*_])\s*(\1\s*){2,}$"""))

    private fun splitRow(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim().let(::stripInline) }

    /** 去掉内联标记（粗体/斜体/行内代码/链接），IR 层只保留纯文本。 */
    private fun stripInline(s: String): String {
        var t = s
        t = t.replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "$1")   // 图片 -> alt
        t = t.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")    // 链接 -> 文本
        t = t.replace(Regex("""`{1,3}([^`]*)`{1,3}"""), "$1")      // 行内代码
        t = t.replace(Regex("""(\*\*|__)(.*?)\1"""), "$2")         // 粗体
        t = t.replace(Regex("""(\*|_)(.*?)\1"""), "$2")            // 斜体
        t = t.replace(Regex("""~~(.*?)~~"""), "$1")                // 删除线
        return t.trim()
    }
}