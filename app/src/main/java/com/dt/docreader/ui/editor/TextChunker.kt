package com.dt.docreader.ui.editor

/**
 * 大文本分块。
 *
 * ## 为什么需要它
 *
 * `BasicTextField` 持有整个 `TextFieldValue`，每次按键都会触发
 * **全文本**的 diff 与重新布局测量，成本 O(N)。
 * 32MB 文本下单个按键即可能造成数百毫秒卡顿。
 *
 * 分块后，编辑区用 `LazyColumn` 只组合**可见块**，
 * 每次按键只重排当前块（[LINES_PER_CHUNK] 行），
 * 与文件总大小无关 —— 大文件编辑性能 ≈ 小文件编辑性能。
 *
 * ## 正确性保证
 *
 * - 切块**只按 `\n` 边界**，不破坏行；
 * - 块内保留结尾换行（除最后一块），因此
 *   `chunks.joinToString("")` 与原文**逐字节一致**；
 * - 切块与拼接都是纯函数，可单测。
 *
 * ## 已知限制（产品已确认接受）
 *
 * 光标/选择**不能跨越块边界**。块内完全自由。
 * 小文件（< [CHUNK_THRESHOLD_BYTES]）仍走单框编辑，无此限制。
 */
object TextChunker {

    /** 超过此字节数才启用分块编辑；以下走原单框编辑，体验不变。 */
    const val CHUNK_THRESHOLD_BYTES = 256 * 1024

    /** 每块的目标行数。500 行在 13sp 等宽字体下约 9500px，远超一屏。 */
    const val LINES_PER_CHUNK = 500

    /** 是否需要分块（按 UTF-8 字节数估算，避免为估算额外编码）。 */
    fun shouldChunk(text: String): Boolean =
        text.length >= CHUNK_THRESHOLD_BYTES / 2 // 中英混合下 length 约为字节数的一半

    /**
     * 按行切块。
     *
     * 每块包含至多 [LINES_PER_CHUNK] 行，**保留行尾 `\n`**（最后一块除外），
     * 使 `joinToString("")` 能无损还原原文。
     */
    fun chunk(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        if (!shouldChunk(text)) return listOf(text)

        val out = ArrayList<String>(text.length / 8192 + 1)
        var lineStart = 0
        var linesInChunk = 0
        var i = 0
        val n = text.length
        while (i < n) {
            if (text[i] == '\n') {
                linesInChunk++
                if (linesInChunk >= LINES_PER_CHUNK) {
                    // 含换行符一起切走，保证拼接无损
                    out.add(text.substring(lineStart, i + 1))
                    lineStart = i + 1
                    linesInChunk = 0
                }
            }
            i++
        }
        if (lineStart < n) out.add(text.substring(lineStart, n))
        return out.ifEmpty { listOf("") }
    }

    /** 无损拼接。 */
    fun join(chunks: List<String>): String {
        if (chunks.size == 1) return chunks[0]
        val total = chunks.sumOf { it.length }
        val sb = StringBuilder(total)
        chunks.forEach { sb.append(it) }
        return sb.toString()
    }

    /** 该块在全文中的起始行号（用于行号显示）。 */
    fun lineOffsetOf(chunks: List<String>, index: Int): Int {
        var lines = 0
        for (k in 0 until index.coerceAtMost(chunks.size)) {
            val c = chunks[k]
            for (ch in c) if (ch == '\n') lines++
        }
        return lines
    }
}