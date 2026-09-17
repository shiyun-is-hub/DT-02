package com.dt.docreader.ui.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

/**
 * 扁平化渲染单元（性能核心）。
 *
 * 每项都带一个**预先分配好的 Int id**，直接用作 LazyColumn 的 key：
 * - 避免在组合期做字符串拼接生成 key（那是每帧的热点，会造成 UI 线程压力与 GC）；
 * - Int key 的 hashCode/equals 是零成本。
 */
sealed interface RenderItem {
    /** LazyColumn 使用的稳定数字 key（构建期一次性分配）。 */
    val id: Int

    data class CodeHeader(
        override val id: Int,
        val blockIndex: Int,
        val language: String?,
        val lineCount: Int,
        /** 整块原始代码，供复制按钮使用。 */
        val rawCode: String
    ) : RenderItem

    data class CodeLine(
        override val id: Int,
        val blockIndex: Int,
        val numberText: String,
        val text: String
    ) : RenderItem

    data class CodeFooter(override val id: Int, val blockIndex: Int) : RenderItem

    data class TableRow(
        override val id: Int,
        val blockIndex: Int,
        val rowIndex: Int,
        val cells: List<String>,
        val isHeader: Boolean
    ) : RenderItem

    data class TableFooter(override val id: Int, val blockIndex: Int) : RenderItem

    data class ListItemRow(
        override val id: Int,
        val blockIndex: Int,
        val index: Int,
        val marker: String,
        val text: String
    ) : RenderItem

    data class SimpleBlock(
        override val id: Int,
        val blockIndex: Int,
        val block: Block
    ) : RenderItem
}

/**
 * 把 DocumentModel 展开为扁平渲染单元。
 *
 * 这是一次性准备工作，应在进入 UI 前完成（构建期分配 id，渲染期零计算）。
 */
object RenderPlan {

    /** 表格单块最多渲染的行数（避免超大 CSV 造成首屏卡顿）。 */
    private const val TABLE_ROW_LIMIT = 500

    fun build(doc: DocumentModel): List<RenderItem> {
        val blocks = doc.allBlocks
        val out = ArrayList<RenderItem>(blocks.size * 2)
        var nextId = 0

        blocks.forEachIndexed { bi, block ->
            when (block) {
                is Block.CodeBlock -> {
                    val lines = block.code.split('\n')
                    out.add(
                        RenderItem.CodeHeader(
                            id = nextId++,
                            blockIndex = bi,
                            language = block.language,
                            lineCount = lines.size,
                            rawCode = block.code
                        )
                    )
                    for (i in lines.indices) {
                        out.add(
                            RenderItem.CodeLine(
                                id = nextId++,
                                blockIndex = bi,
                                numberText = (i + 1).toString(),
                                text = lines[i]
                            )
                        )
                    }
                    out.add(RenderItem.CodeFooter(nextId++, bi))
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
                                cells = rows[r],
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

                else -> out.add(RenderItem.SimpleBlock(nextId++, bi, block))
            }
        }
        return out
    }
}