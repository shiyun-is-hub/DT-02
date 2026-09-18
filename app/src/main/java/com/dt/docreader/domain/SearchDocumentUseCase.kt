package com.dt.docreader.domain

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

/**
 * 文档内搜索。
 *
 * 设计取舍：
 * - 搜索在**原文**（rawText）上进行，而不是解析后的 blocks。
 *   原因：Markdown 的内联标记在解析时被剥离，若基于 blocks 搜索，
 *   用户搜 `**bold**` 或表格分隔符会搜不到。
 * - 但结果要能**跳转到渲染位置**，所以每个命中需要映射回 blockIndex。
 *   解析器不保留字符偏移，因此采用**文本近似定位**：
 *   取命中处的片段，在 blocks 的代表文本里查找包含它的 block。
 * - 结果数量有上限，避免超大文档搜索产生海量结果拖垮 UI。
 */
object SearchDocumentUseCase {

    /** 单个文档最多返回的命中数。 */
    const val MAX_RESULTS = 500

    /** 结果列表中每条展示的上下文半径（字符）。 */
    private const val CONTEXT_RADIUS = 40

    /** 用于定位的片段长度。 */
    private const val PROBE_LEN = 24

    data class Hit(
        /** 在原文中的字符偏移。 */
        val offset: Int,
        /** 命中所在行（必要时以命中为中心截断）。 */
        val lineText: String,
        /** 命中在 [lineText] 中的起始位置（用于高亮）。 */
        val matchStartInLine: Int,
        val matchLength: Int,
        /** 估算的 block 序号，用于跳转；-1 表示无法定位。 */
        val blockIndex: Int
    )

    /**
     * 执行搜索。
     *
     * @param query 关键字（空则返回空结果）
     * @param caseSensitive 是否区分大小写
     */
    fun search(
        doc: DocumentModel,
        query: String,
        caseSensitive: Boolean = false,
        limit: Int = MAX_RESULTS
    ): List<Hit> {
        if (query.isEmpty()) return emptyList()
        val raw = doc.rawText ?: return emptyList()

        val haystack = if (caseSensitive) raw else raw.lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        if (needle.isEmpty()) return emptyList()

        // 预扫描：每个 block 的代表文本，用于命中定位
        val blockTexts = doc.allBlocks.map { representativeText(it).lowercase() }

        val hits = ArrayList<Hit>(minOf(limit, 64))
        var from = 0
        while (hits.size < limit) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) break

            val lineStart = raw.lastIndexOf('\n', idx).let { if (it < 0) 0 else it + 1 }
            val lineEnd = raw.indexOf('\n', idx).let { if (it < 0) raw.length else it }
            val fullLine = raw.substring(lineStart, lineEnd)

            val matchInLine = idx - lineStart
            val (shown, shownMatchStart) = windowAround(fullLine, matchInLine, query.length)

            val probe = raw.substring(idx, minOf(idx + PROBE_LEN, raw.length)).lowercase()
            hits.add(
                Hit(
                    offset = idx,
                    lineText = shown,
                    matchStartInLine = shownMatchStart,
                    matchLength = query.length,
                    blockIndex = locateBlock(blockTexts, probe)
                )
            )
            from = idx + needle.length
        }
        return hits
    }

    /**
     * 以命中为中心开窗，保证命中完整可见。
     *
     * @return 截断后的行文本 与 命中在该文本中的起始位置
     */
    private fun windowAround(line: String, matchStart: Int, matchLen: Int): Pair<String, Int> {
        if (line.length <= CONTEXT_RADIUS * 2 + matchLen) return line to matchStart

        val start = (matchStart - CONTEXT_RADIUS).coerceAtLeast(0)
        val end = (matchStart + matchLen + CONTEXT_RADIUS).coerceAtMost(line.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < line.length) "…" else ""

        val shown = prefix + line.substring(start, end) + suffix
        // 命中在新串中的位置 = 原位置 - 窗口起点 + 前缀长度
        return shown to (matchStart - start + prefix.length)
    }

    /**
     * 把命中的片段映射到 block 序号（近似定位）。
     *
     * 定位失败返回 -1，UI 会退化为「仅显示结果，不跳转」。
     */
    private fun locateBlock(blockTextsLower: List<String>, probeLower: String): Int {
        if (probeLower.isBlank()) return -1
        blockTextsLower.forEachIndexed { i, t ->
            if (t.isNotEmpty() && t.contains(probeLower)) return i
        }
        return -1
    }

    /** 取 block 的代表文本（用于定位）。 */
    private fun representativeText(block: Block): String = when (block) {
        is Block.Heading -> block.text
        is Block.Paragraph -> block.text
        is Block.CodeBlock -> block.code
        is Block.BulletList -> block.items.joinToString("\n")
        is Block.TableBlock -> block.rows.joinToString("\n") { it.joinToString(" ") }
        is Block.ImageBlock -> block.uri
        is Block.Slide -> (block.title ?: "") + "\n" + block.body.joinToString("\n")
    }
}