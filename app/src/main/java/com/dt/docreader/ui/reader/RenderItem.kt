package com.dt.docreader.ui.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

/**
 * 扁平化渲染单元（性能核心）。
 *
 * 背景：初版把「整个代码块」当成 LazyColumn 的**一个 item**，
 *      内部再用 for 循环创建数百个 Text。结果：
 *      - Compose 无法懒加载，必须一次性测量/布局整个巨型节点树；
 *      - 滚动时整棵树反复重新布局，直接掉帧。
 *
 * 解决：把 block 展开成**行级/项级**的扁平列表，全部交给 LazyColumn。
 *      这样只有可见的十几行会被组合与测量，滚动时也能复用节点。
 */
sealed interface RenderItem {
    /** 代码块头部（语言 + 行数 + 复制按钮）。 */
    data class CodeHeader(
        val blockIndex: Int,
        val language: String?,
        val lineCount: Int,
        /** 整块原始代码，供复制按钮使用。 */
        val rawCode: String
    ) : RenderItem

    /** 单行代码。 */
    data class CodeLine(
        val blockIndex: Int,
        val lineNo: Int,
        val numberText: String,
        val text: String
    ) : RenderItem

    /** 代码块之间的间隔（保证块间距）。 */
    data class CodeFooter(val blockIndex: Int) : RenderItem

    /** 表格一行。 */
    data class TableRow(
        val blockIndex: Int,
        val rowIndex: Int,
        val cells: List<String>,
        val isHeader: Boolean
    ) : RenderItem

    /** 表格前后间隔。 */
    data class TableFooter(val blockIndex: Int) : RenderItem

    /** 列表项（逐项渲染，避免长列表一次性构建）。 */
    data class ListItemRow(
        val blockIndex: Int,
        val index: Int,
        val marker: String,
        val text: String
    ) : RenderItem

    /** 其他块：标题、段落、图片、幻灯片（整体一个 item）。 */
    data class SimpleBlock(val blockIndex: Int, val block: Block) : RenderItem
}

/**
 * 把 DocumentModel 展开为扁平渲染单元。
 *
 * 保持在 IO/准备阶段做一次，UI 层直接消费，避免在组合期间做重活。
 */
object RenderPlan {

    /** 表格单块最多渲染的行数（避免超大 CSV 造成首屏卡顿）。 */
    private const val TABLE_ROW_LIMIT = 500

    fun build(doc: DocumentModel): List<RenderItem> {
        val blocks = doc.allBlocks
        val out = ArrayList<RenderItem>(blocks.size * 2)

        blocks.forEachIndexed { bi, block ->
            when (block) {
                is Block.CodeBlock -> {
                    val lines = block.code.split('\n')
                    out.add(RenderItem.CodeHeader(bi, block.language, lines.size, block.code))
                    // 行号预格式化，避免绘制期重复计算
                    for (i in lines.indices) {
                        out.add(
                            RenderItem.CodeLine(
                                blockIndex = bi,
                                lineNo = i + 1,
                                numberText = (i + 1).toString(),
                                text = lines[i].ifEmpty { " " }
                            )
                        )
                    }
                    out.add(RenderItem.CodeFooter(bi))
                }

                is Block.TableBlock -> {
                    val rows = block.rows
                    val limit = minOf(rows.size, TABLE_ROW_LIMIT)
                    for (r in 0 until limit) {
                        out.add(
                            RenderItem.TableRow(
                                blockIndex = bi,
                                rowIndex = r,
                                cells = rows[r],
                                isHeader = r == 0
                            )
                        )
                    }
                    out.add(RenderItem.TableFooter(bi))
                }

                is Block.BulletList -> {
                    block.items.forEachIndexed { idx, item ->
                        out.add(
                            RenderItem.ListItemRow(
                                blockIndex = bi,
                                index = idx,
                                marker = if (block.ordered) "${idx + 1}." else "▸",
                                text = item
                            )
                        )
                    }
                }

                else -> out.add(RenderItem.SimpleBlock(bi, block))
            }
        }
        return out
    }
}