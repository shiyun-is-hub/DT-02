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
 * 代码读取器：把整个源文件作为一个 CodeBlock 输出，附带语言标识。
 *
 * 设计取舍：
 * - 代码文件不需要「切段落」——切开反而破坏缩进与上下文。
 *   因此整体作为一个 CodeBlock，交给 UI 做等宽显示 + 行号 + 语法高亮。
 * - 同时做简单的「顶层结构扫描」，把明显的函数/类声明抽成标题，
 *   方便在长文件里快速浏览（可选，不影响代码本体）。
 */
class CodeReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean = kind == FileKind.CODE

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        val decoded = source.openStream().use { EncodingDetector.readAllText(it) }
        val text = decoded.text
        val lang = LanguageRegistry.fromFileName(source.fileName)
            ?: LanguageRegistry.fallbackFor(source.kind)

        val blocks = mutableListOf<Block>()

        // 顶部信息：语言与规模
        val lineCount = text.count { it == '\n' } + 1
        blocks.add(
            Block.Paragraph(
                "语言：${lang.displayName} · $lineCount 行 · ${formatSize(source.size)}"
            )
        )

        // 顶层结构（函数/类）概览，最多抽 40 条，避免噪音
        val outline = extractOutline(text)
        if (outline.isNotEmpty()) {
            blocks.add(Block.Heading(2, "结构概览"))
            blocks.add(Block.BulletList(outline, ordered = false))
        }

        // 代码本体
        blocks.add(Block.Heading(2, "源码"))
        blocks.add(Block.CodeBlock(language = lang.id, code = text))

        DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = FileKind.CODE,
                encoding = decoded.charset.name(),
                language = lang.displayName
            ),
            sections = listOf(Section(title = null, pageIndex = 0, blocks = blocks))
        )
    }

    /**
     * 极简结构扫描：识别常见的函数/类/方法声明行。
     * 只做「像不像声明」的启发式判断，不追求完备 —— 目标是有用而非准确。
     */
    private fun extractOutline(text: String): List<String> {
        val patterns = listOf(
            Regex("""^\s*(?:public|private|internal|protected|open|abstract|sealed|final|static|async|export|default)\s*(?:class|interface|object|enum|struct|fun|function|def|fn|func|type)\s+\w+"""),
            Regex("""^\s*(?:class|interface|object|enum|struct|fun|function|def|fn|func|type|trait|impl)\s+\w+"""),
            Regex("""^\s*(?:public|private|protected|internal)?\s*(?:suspend\s+)?fun\s+\w+"""),
            Regex("""^\s*(?:public|private|protected|static|async)?\s*(?:[\w<>\[\],\s]+\s+)?\w+\s*\([^)]*\)\s*\{?\s*$"""),
            Regex("""^\s*(?:func|fn|def|function)\s+\w+""")
        )
        val out = LinkedHashSet<String>()
        text.lineSequence().forEachIndexed { idx, raw ->
            if (out.size >= 40) return@forEachIndexed
            val line = raw.trimEnd()
            if (line.isBlank() || line.length > 160) return@forEachIndexed
            if (patterns.any { it.containsMatchIn(line) }) {
                out.add("L${idx + 1}: ${line.trim().take(120)}")
            }
        }
        return out.toList()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
    }
}