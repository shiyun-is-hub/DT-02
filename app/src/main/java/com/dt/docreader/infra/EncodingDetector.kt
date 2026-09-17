package com.dt.docreader.infra

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 编码探测与解码。
 *
 * 性能设计（相比初版的关键优化）：
 * 1. **真正的流式限长**：`readBytesLimited` 达到上限即停止读取，不再继续消费流。
 *    （初版超限后仍写入剩余字节，既没限流又浪费内存。）
 * 2. **UTF-8 早停校验**：`isValidUtf8` 遇到明显非法序列立即返回 false，
 *    不需要扫描完整文件即可判定非 UTF-8。
 * 3. **大文件保护**：默认上限 32MB，避免一次性把超大文件读进内存导致 OOM。
 * 4. **BOM 优先**：BOM 是确定性信号，命中即返回，零扫描成本。
 */
object EncodingDetector {

    /** 默认最大读取字节数（32MB）。超过部分截断，仅影响预览准确性而不致崩溃。 */
    const val DEFAULT_LIMIT_BYTES: Long = 32L * 1024 * 1024

    data class Result(val charset: Charset, val text: String, val truncated: Boolean = false)

    private val GBK: Charset? = runCatching { Charset.forName("GBK") }.getOrNull()

    fun detectAndDecode(bytes: ByteArray): Result {
        // 1) BOM 判定（确定性信号，优先）
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Result(
                StandardCharsets.UTF_8,
                String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            )
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Result(
                StandardCharsets.UTF_16LE,
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            )
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Result(
                StandardCharsets.UTF_16BE,
                String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            )
        }

        // 2) 严格 UTF-8 校验（命中即用 UTF-8，这是绝大多数现代文件的情况）
        if (isValidUtf8(bytes)) {
            return Result(StandardCharsets.UTF_8, String(bytes, StandardCharsets.UTF_8))
        }

        // 3) 回退 GBK（中文旧文件常见）
        if (GBK != null) {
            return Result(GBK, String(bytes, GBK))
        }

        // 4) 最终兜底：UTF-8（替换非法字节）
        return Result(StandardCharsets.UTF_8, String(bytes, StandardCharsets.UTF_8))
    }

    /**
     * 从流中读取并解码。
     *
     * @param limitBytes 最多读取的字节数；超过则截断并标记 truncated=true。
     */
    fun readAllText(input: InputStream, limitBytes: Long = DEFAULT_LIMIT_BYTES): Result {
        val (bytes, truncated) = input.readBytesLimited(limitBytes)
        val decoded = detectAndDecode(bytes)
        return if (truncated) decoded.copy(truncated = true) else decoded
    }

    /**
     * 真正的限长读取：达到上限立即停止，不再继续消费输入流。
     * 返回 (数据, 是否被截断)。
     */
    private fun InputStream.readBytesLimited(limit: Long): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream(minOf(limit, 64 * 1024).toInt())
        val buf = ByteArray(16 * 1024)
        var total = 0L
        var truncated = false

        while (total < limit) {
            val remaining = (limit - total).coerceAtMost(buf.size.toLong()).toInt()
            val n = read(buf, 0, remaining)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        // 若刚好填满上限，探测是否还有剩余数据
        if (total >= limit) {
            truncated = read() >= 0
        }
        return out.toByteArray() to truncated
    }

    /**
     * UTF-8 合法性校验。
     *
     * 优化点：遇到非法首字节或非法续字节**立即返回 false**（早停），
     * 而不是继续扫描。对 GBK 等非 UTF-8 文件，通常前几 KB 内即可判定，
     * 避免了对整个大文件的无效扫描。
     */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val size = bytes.size
        while (i < size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i += 1

                b in 0xC2..0xDF -> {
                    if (i + 1 >= size) return false
                    if (!isContinuation(bytes[i + 1])) return false
                    i += 2
                }

                b in 0xE0..0xEF -> {
                    if (i + 2 >= size) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    // 禁止过长的三字节（overlong）与代理区
                    if (b == 0xE0 && b1 < 0xA0) return false
                    if (b == 0xED && b1 >= 0xA0) return false
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2])) return false
                    i += 3
                }

                b in 0xF0..0xF4 -> {
                    if (i + 3 >= size) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b == 0xF0 && b1 < 0x90) return false
                    if (b == 0xF4 && b1 >= 0x90) return false
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2]) ||
                        !isContinuation(bytes[i + 3])
                    ) return false
                    i += 4
                }

                else -> return false
            }
        }
        return true
    }

    private fun isContinuation(b: Byte): Boolean {
        val v = b.toInt() and 0xFF
        return v in 0x80..0xBF
    }
}