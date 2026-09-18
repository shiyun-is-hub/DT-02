package com.dt.docreader.ui.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

/**
 * contentType 常量（编译期内联，零分配）。
 *
 * 放在文件顶层而非 companion object，便于各 data class 直接引用。
 */
private const val CT_CODE_HEADER = "code_header"
private const val CT_CODE_LINE = "code_line"
private const val CT_CODE_FOOTER = "code_footer"
private const val CT_TABLE_ROW = "table_row"
private const val CT_TABLE_FOOTER = "table_footer"
private const val CT_LIST_ITEM = "list_item"
private const val CT_SIMPLE_BLOCK = "simple_block"
private const val CT_PARAGRAPH_CHUNK = "paragraph_chunk"

/**
 * 扁平化渲染单元（性能核心）。
 *
 * 每项都带一个**预先分配好的 Int id**，直接用作 LazyColumn 的 key：
 * - 避免在组合期做字符串拼接生成 key（那是每帧的热点，会造成 UI 线程压力与 GC）；
 * - Int key 的 hashCode/equals 是零成本。
 *
 * 每项还带 [contentType]（编译期常量字符串），交给 LazyColumn 做**按类型复用**：
 * 同类型的 item 才共用同一个节点槽位，避免代码行 / 表格行 / 段落之间互相顶替导致反复重建。
 */
sealed interface RenderItem {
    /** LazyColumn 使用的稳定数字 key（构建期一次性分配）。 */
    val id: Int

    /** LazyColumn 的 contentType（编译期常量，零分配）。 */
    val contentType: String

    data class CodeHeader(
        override val id: Int,
        val blockIndex: Int,
        val language: String?,
        val lineCount: Int,
        /** 整块原始代码，供复制按钮使用。 */
        val rawCode: String
    ) : RenderItem {
        override val contentType: String get() = CT_CODE_HEADER
    }

    data class CodeLine(
        override val id: Int,
        val blockIndex: Int,
        val numberText: String,
        val text: String
    ) : RenderItem {
        override val contentType: String get() = CT_CODE_LINE
    }

    data class CodeFooter(
        override val id: Int,
        val blockIndex: Int,
        /** 是否因行数上限被截断（超出部分未渲染）。 */
        val truncated: Boolean = false
    ) : RenderItem {
        override val contentType: String get() = CT_CODE_FOOTER
    }

    data class TableRow(
        override val id: Int,
        val blockIndex: Int,
        val rowIndex: Int,
        val cells: List<String>,
        val isHeader: Boolean
    ) : RenderItem {
        override val contentType: String get() = CT_TABLE_ROW
    }

    data class TableFooter(override val id: Int, val blockIndex: Int) : RenderItem {
        override val contentType: String get() = CT_TABLE_FOOTER
    }

    data class ListItemRow(
        override val id: Int,
        val blockIndex: Int,
        val index: Int,
        val marker: String,
        val text: String
    ) : RenderItem {
        override val contentType: String get() = CT_LIST_ITEM
    }

    data class SimpleBlock(
        override val id: Int,
        val blockIndex: Int,
        val block: Block
    ) : RenderItem {
        override val contentType: String get() = CT_SIMPLE_BLOCK
    }

    /**
     * 超长段落被拆分后的一个片段。
     *
     * 为什么需要它：`Block.Paragraph` 是**不可分割的渲染单元**，
     * 一个 10MB 无空行的 .log / .txt 会产出单个含数十万字符的 Paragraph，
     * 交给一个 Text 做布局时，测量成本与文本长度线性相关，
     * 且该 item 高度可能远超一屏 —— 首屏组合即卡死。
     *
     * 拆成片段后，LazyColumn 只组合可见的若干片段，成本与屏幕高度相关，
     * 与文档总长度无关。
     *
     * @param text    片段文本
     * @param isFirst 是否是该段落的首片段（首片段保留段前间距）
     * @param isLast  是否是末片段（末片段保留段后间距）
     */
    data class ParagraphChunk(
        override val id: Int,
        val blockIndex: Int,
        val text: String,
        val isFirst: Boolean,
        val isLast: Boolean
    ) : RenderItem {
        override val contentType: String get() = CT_PARAGRAPH_CHUNK
    }
}

/**
 * 把 DocumentModel 展开为扁平渲染单元。
 *
 * 这是一次性准备工作，应在进入 UI 前完成（构建期分配 id，渲染期零计算）。
 */
object RenderPlan {

    /** 表格单块最多渲染的行数（避免超大 CSV 造成首屏卡顿）。 */
    private const val TABLE_ROW_LIMIT = 500

    /**
     * 代码块最多展开的行数。
     *
     * 展开本身是 O(行数)：10 万行的压缩 JS 会生成 10 万个 item，
     * 即便 LazyColumn 只组合可见项，[build] 的遍历与内存开销仍不可接受。
     * 超出部分不再展开，由 CodeFooter 提示「已截断」。
     */
    const val CODE_LINE_LIMIT = 5000

    /**
     * 单行代码的字符上限。
     *
     * 这是**掉帧长尾的主因**：压缩后的 JS/JSON 一行可达数万字符，
     * 交给单个 Text 做软换行时，断行测量（line breaking）成本随行长增长，
     * 甚至一行就能占满整屏、触发数十次换行候选计算。
     * 截断到 1000 字符后，绝大多数行只需一次测量即可完成布局。
     */
    private const val CODE_LINE_CHAR_LIMIT = 1000

    /** 表格单元格字符上限（超长单元格会让整行 Row 的测量成本爆炸）。 */
    private const val CELL_CHAR_LIMIT = 300

    /**
     * 段落字符上限（超过即拆分为多个片段）。
     *
     * 2000 字符约等于 10~20 个屏幕行，单个 item 的高度可控；
     * 远小于此的段落不做拆分，保持渲染结构简单。
     */
    private const val PARAGRAPH_CHAR_LIMIT = 2000

    fun build(doc: DocumentModel): List<RenderItem> {
        val blocks = doc.allBlocks
        val out = ArrayList<RenderItem>(blocks.size * 2)
        var nextId = 0

        blocks.forEachIndexed { bi, block ->
            when (block) {
                is Block.CodeBlock -> {
                    val lines = block.code.split('\n')
                    val total = lines.size
                    val limit = minOf(total, CODE_LINE_LIMIT)
                    out.add(
                        RenderItem.CodeHeader(
                            id = nextId++,
                            blockIndex = bi,
                            language = block.language,
                            lineCount = total,
                            rawCode = block.code
                        )
                    )
                    for (i in 0 until limit) {
                        val raw = lines[i]
                        val shown = if (raw.length > CODE_LINE_CHAR_LIMIT) {
                            raw.substring(0, CODE_LINE_CHAR_LIMIT) + "  …[已截断]"
                        } else {
                            raw
                        }
                        out.add(
                            RenderItem.CodeLine(
                                id = nextId++,
                                blockIndex = bi,
                                numberText = (i + 1).toString(),
                                text = shown
                            )
                        )
                    }
                    out.add(RenderItem.CodeFooter(nextId++, bi, truncated = total > limit))
                }

                is Block.TableBlock -> {
                    val rows = block.rows
                    val limit = minOf(rows.size, TABLE_ROW_LIMIT)
                    for (r in 0 until limit) {
                        out.add(
                            RenderItem.TableRow(
                                id = nextId++,
                                blockIndex = bi,
                                rowIndex = r,
                                // 单元格同样做字符截断：单行超长会让整行 Row 测量爆炸
                                cells = rows[r].map {
                                    if (it.length > CELL_CHAR_LIMIT) it.take(CELL_CHAR_LIMIT) + "…" else it
                                },
                                isHeader = r == 0
                            )
                        )
                    }
                    out.add(RenderItem.TableFooter(nextId++, bi))
                }

                is Block.BulletList -> {
                    block.items.forEachIndexed { idx, item ->
                        out.add(
                            RenderItem.ListItemRow(
                                id = nextId++,
                                blockIndex = bi,
                                index = idx,
                                marker = if (block.ordered) "${idx + 1}." else "▸",
                                text = item
                            )
                        )
                    }
                }

                // 超长段落单独处理：拆成多个片段，避免单 item 测量爆炸
                is Block.Paragraph -> {
                    val chunks = splitParagraph(block.text)
                    if (chunks == null) {
                        out.add(RenderItem.SimpleBlock(nextId++, bi, block))
                    } else {
                        chunks.forEachIndexed { ci, chunk ->
                            out.add(
                                RenderItem.ParagraphChunk(
                                    id = nextId++,
                                    blockIndex = bi,
                                    text = chunk,
                                    isFirst = ci == 0,
                                    isLast = ci == chunks.lastIndex
                                )
                            )
                        }
                    }
                }

                else -> out.add(RenderItem.SimpleBlock(nextId++, bi, block))
            }
        }
        return out
    }

    /**
     * 拆分超长段落。
     *
     * @return null 表示无需拆分（走普通 SimpleBlock 渲染）；
     *         非 null 为拆分后的片段列表。
     *
     * 拆分策略（按优先级）：
     * 1. 段落不长 -> 不拆
     * 2. 含换行 -> 按换行切成若干段，再按 [PARAGRAPH_CHAR_LIMIT] 聚合成片段
     *    （按行聚合而非逐行成 item，避免把 10 万行日志变成 10 万个 item）
     * 3. 无换行的超长单行（minified 等）-> 按固定长度硬切
     */
    private fun splitParagraph(text: String): List<String>? {
        if (text.length <= PARAGRAPH_CHAR_LIMIT) return null

        val chunks = ArrayList<String>(text.length / PARAGRAPH_CHAR_LIMIT + 1)

        if (text.indexOf('\n') < 0) {
            // 情况 3：无换行的超长单行，按固定长度硬切
            var start = 0
            while (start < text.length) {
                val end = minOf(start + PARAGRAPH_CHAR_LIMIT, text.length)
                chunks.add(text.substring(start, end))
                start = end
            }
            return chunks
        }

        // 情况 2：按行聚合。逐行追加，累计长度超限就切一段。
        val sb = StringBuilder(PARAGRAPH_CHAR_LIMIT + 128)
        var lineStart = 0
        while (lineStart <= text.length) {
            val nl = text.indexOf('\n', lineStart)
            val lineEnd = if (nl < 0) text.length else nl
            val line = text.substring(lineStart, lineEnd)

            // 单行本身就超限：先把缓冲刷出，再硬切这一行
            if (line.length > PARAGRAPH_CHAR_LIMIT) {
                if (sb.isNotEmpty()) {
                    chunks.add(sb.toString())
                    sb.setLength(0)
                }
                var s = 0
                while (s < line.length) {
                    val e = minOf(s + PARAGRAPH_CHAR_LIMIT, line.length)
                    chunks.add(line.substring(s, e))
                    s = e
                }
            } else {
                if (sb.length + line.length + 1 > PARAGRAPH_CHAR_LIMIT && sb.isNotEmpty()) {
                    chunks.add(sb.toString())
                    sb.setLength(0)
                }
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }

            if (nl < 0) break
            lineStart = nl + 1
        }
        if (sb.isNotEmpty()) chunks.add(sb.toString())
        return chunks.ifEmpty { null }
    }

    // ---------------- block <-> item 映射（书签 / 搜索跳转用） ----------------

    /**
     * 建立 blockIndex -> 该 block 首个渲染 item 序号 的映射。
     *
     * 为什么需要：书签与搜索命中都以 **blockIndex** 为锚点（稳定，不受字号影响），
     * 而 LazyColumn 跳转需要 **item 序号**。两者之间的换算关系由 RenderPlan 决定，
     * 因此放在这里而不是 UI 层。
     */
    private fun blockToItemMap(doc: DocumentModel): IntArray {
        val blocks = doc.allBlocks
        val map = IntArray(blocks.size) { -1 }
        var itemIdx = 0
        blocks.forEachIndexed { bi, block ->
            map[bi] = itemIdx
            itemIdx += itemCountOf(block)
        }
        return map
    }

    /** 单个 block 展开后占用的 item 数量。 */
    private fun itemCountOf(block: Block): Int = when (block) {
        is Block.CodeBlock -> {
            val total = block.code.count { it == '\n' } + 1
            // header + 展开的行 + footer
            1 + minOf(total, CODE_LINE_LIMIT) + 1
        }
        is Block.TableBlock -> minOf(block.rows.size, TABLE_ROW_LIMIT) + 1
        is Block.BulletList -> block.items.size
        is Block.Paragraph -> if (block.text.length <= PARAGRAPH_CHAR_LIMIT) 1 else 2 // 至少 2，精确值不影响定位
        else -> 1
    }

    /**
     * 把 blockIndex 换算为渲染 item 序号。
     *
     * @return item 序号；blockIndex 越界返回 -1
     */
    fun itemIndexOfBlock(doc: DocumentModel, blockIndex: Int): Int {
        if (blockIndex < 0) return -1
        val map = blockToItemMap(doc)
        return if (blockIndex < map.size) map[blockIndex] else -1
    }

    /**
     * 把渲染 item 序号反查为 blockIndex（书签记录当前位置用）。
     *
     * @return blockIndex；无法定位返回 null
     */
    fun blockIndexOfItem(doc: DocumentModel, itemIndex: Int): Int? {
        if (itemIndex < 0) return null
        val map = blockToItemMap(doc)
        // 找最后一个 map[i] <= itemIndex 的 i
        var result: Int? = null
        for (i in map.indices) {
            if (map[i] <= itemIndex) result = i else break
        }
        return result
    }

    /** 取 block 的预览文本（书签列表展示用）。 */
    fun previewOfBlock(doc: DocumentModel, blockIndex: Int): String {
        val blocks = doc.allBlocks
        if (blockIndex < 0 || blockIndex >= blocks.size) return ""
        val raw = when (val b = blocks[blockIndex]) {
            is Block.Heading -> b.text
            is Block.Paragraph -> b.text
            is Block.CodeBlock -> b.code
            is Block.BulletList -> b.items.joinToString(" / ")
            is Block.TableBlock -> b.rows.firstOrNull()?.joinToString(" | ") ?: ""
            is Block.ImageBlock -> b.alt ?: b.uri
            is Block.Slide -> b.title ?: b.body.firstOrNull() ?: ""
        }
        return raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120) ?: ""
    }
}