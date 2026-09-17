package com.dt.docreader.infra

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** 简易编码探测：BOM 优先 -> UTF-8 严格验证 -> 回退 GBK。 */
object EncodingDetector {

    data class Result(val charset: Charset, val text: String)

    fun detectAndDecode(bytes: ByteArray): Result {
        // 1) BOM 判定
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Result(StandardCharsets.UTF_8, String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Result(StandardCharsets.UTF_16LE, String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Result(StandardCharsets.UTF_16BE, String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE))
        }
        // 2) 严格 UTF-8 验证
        if (isValidUtf8(bytes)) {
            return Result(StandardCharsets.UTF_8, String(bytes, StandardCharsets.UTF_8))
        }
        // 3) 回退 GBK
        val gbk = runCatching { Charset.forName("GBK") }.getOrNull()
            ?: return Result(StandardCharsets.UTF_8, String(bytes, StandardCharsets.UTF_8))
        return Result(gbk, String(bytes, gbk))
    }

    fun readAllText(input: InputStream, limitBytes: Long = 32L * 1024 * 1024): Result {
        val bytes = input.readBytesLimited(limitBytes)
        return detectAndDecode(bytes)
    }

    private fun InputStream.readBytesLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) { out.write(buf, 0, n); break }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val n = when {
                b < 0x80 -> 1
                b in 0xC2..0xDF -> 2
                b in 0xE0..0xEF -> 3
                b in 0xF0..0xF4 -> 4
                else -> return false
            }
            if (i + n > bytes.size) return false
            for (k in 1 until n) {
                val c = bytes[i + k].toInt() and 0xFF
                if (c !in 0x80..0xBF) return false
            }
            i += n
        }
        return true
    }
}
