package com.dt.docreader.infra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * [EncodingDetector] 单元测试。
 *
 * 覆盖审计要求的编码：UTF-8 / UTF-8 BOM / UTF-16 LE / UTF-16 BE / GBK / GB18030。
 */
class EncodingDetectorTest {

    private fun bytesOf(text: String, charset: Charset) = text.toByteArray(charset)

    // ---------- BOM 判定 ----------

    @Test
    fun `UTF-8 BOM 被正确识别并剥离`() {
        val body = "你好，世界"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            bytesOf(body, StandardCharsets.UTF_8)

        val r = EncodingDetector.detectAndDecode(withBom)
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals("BOM 必须被剥离", body, r.text)
    }

    @Test
    fun `UTF-16 LE BOM 被正确识别`() {
        val body = "Hello 世界"
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            bytesOf(body, StandardCharsets.UTF_16LE)

        val r = EncodingDetector.detectAndDecode(withBom)
        assertEquals(StandardCharsets.UTF_16LE, r.charset)
        assertEquals(body, r.text)
    }

    @Test
    fun `UTF-16 BE BOM 被正确识别`() {
        val body = "Hello 世界"
        val withBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            bytesOf(body, StandardCharsets.UTF_16BE)

        val r = EncodingDetector.detectAndDecode(withBom)
        assertEquals(StandardCharsets.UTF_16BE, r.charset)
        assertEquals(body, r.text)
    }

    // ---------- 无 BOM ----------

    @Test
    fun `纯 UTF-8 无 BOM 被识别为 UTF-8`() {
        val text = "中文内容 with ASCII 混合 123"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals(text, r.text)
    }

    @Test
    fun `纯 ASCII 被识别为 UTF-8`() {
        val text = "plain ascii text 123 !@#"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals(text, r.text)
    }

    @Test
    fun `GBK 中文被正确解码（不回退为乱码 UTF-8）`() {
        val gbk = Charset.forName("GBK")
        val text = "中文测试内容"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gbk))
        // 不应是 UTF-8（否则会乱码）
        assertTrue(
            "GBK 内容不应被判为 UTF-8",
            r.charset != StandardCharsets.UTF_8
        )
        assertEquals("GBK 解码结果必须与原文一致", text, r.text)
    }

    @Test
    fun `GB18030 生僻字被正确解码`() {
        val gb18030 = runCatching { Charset.forName("GB18030") }.getOrNull() ?: return
        // 含 GBK 之外的字符（如 𠀀，U+20000）
        val text = "生僻字测试𠀀"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gb18030))
        assertEquals(text, r.text)
    }

    // ---------- UTF-8 校验器边界 ----------

    @Test
    fun `非法 UTF-8 字节序列被拒绝`() {
        // 0xFF 不是合法的 UTF-8 起始字节
        val bad = byteArrayOf(0x41, 0xFF.toByte(), 0x42)
        val r = EncodingDetector.detectAndDecode(bad)
        assertTrue("含非法字节时不应判为 UTF-8", r.charset != StandardCharsets.UTF_8)
    }

    @Test
    fun `overlong 编码被拒绝（0xC0 0x80）`() {
        val overlong = byteArrayOf(0xC0.toByte(), 0x80.toByte())
        val r = EncodingDetector.detectAndDecode(overlong)
        assertTrue(r.charset != StandardCharsets.UTF_8)
    }

    @Test
    fun `代理区编码被拒绝（0xED 0xA0 0x80）`() {
        val surrogate = byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())
        val r = EncodingDetector.detectAndDecode(surrogate)
        assertTrue(r.charset != StandardCharsets.UTF_8)
    }

    @Test
    fun `空字节数组不崩溃`() {
        val r = EncodingDetector.detectAndDecode(ByteArray(0))
        assertEquals("", r.text)
        assertFalse(r.truncated)
    }

    // ---------- 多语言内容（审计要求：中文/English/日本語/Русский/emoji） ----------

    @Test
    fun `多语言混合内容 UTF-8 解码无损`() {
        val text = "中文 English 日本語 Русский emoji😀🎉 café"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals("多语言内容不得损坏", text, r.text)
    }

    @Test
    fun `emoji（4 字节 UTF-8）解码无损`() {
        val text = "😀🎉🚀👨‍👩‍👧‍👦"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals(text, r.text)
    }

    @Test
    fun `日文 UTF-8 解码无损`() {
        val text = "こんにちは世界 カタカナ ひらがな 漢字"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals(text, r.text)
    }

    @Test
    fun `俄文 UTF-8 解码无损`() {
        val text = "Привет мир Кириллица"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, StandardCharsets.UTF_8))
        assertEquals(StandardCharsets.UTF_8, r.charset)
        assertEquals(text, r.text)
    }

    @Test
    fun `中文 GBK 解码无损`() {
        val gbk = Charset.forName("GBK")
        val text = "中文测试内容繁體字"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gbk))
        assertEquals("GBK 内容必须无损解码", text, r.text)
    }

    @Test
    fun `日文 GBK 解码无损`() {
        val gbk = Charset.forName("GBK")
        val text = "日本語のテキスト"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gbk))
        assertEquals(text, r.text)
    }

    @Test
    fun `俄文 GBK 解码无损`() {
        val gbk = Charset.forName("GBK")
        val text = "Русский текст"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gbk))
        assertEquals(text, r.text)
    }

    @Test
    fun `多语言混合 GBK 解码无损`() {
        val gbk = Charset.forName("GBK")
        val text = "中文 English 日本語 Русский"
        val r = EncodingDetector.detectAndDecode(bytesOf(text, gbk))
        assertEquals(text, r.text)
    }

    @Test
    fun `多语言 UTF-16 LE 解码无损`() {
        val text = "中文 English 日本語 Русский 😀"
        val withBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            bytesOf(text, StandardCharsets.UTF_16LE)
        val r = EncodingDetector.detectAndDecode(withBom)
        assertEquals(text, r.text)
    }

    // ---------- 流式读取与截断 ----------

    @Test
    fun `readAllText 读取小文件不标记截断`() {
        val text = "小文件内容"
        val r = EncodingDetector.readAllText(
            ByteArrayInputStream(bytesOf(text, StandardCharsets.UTF_8))
        )
        assertFalse(r.truncated)
        assertEquals(text, r.text)
    }

    @Test
    fun `readAllText 超限时标记截断`() {
        val text = "x".repeat(1000)
        val r = EncodingDetector.readAllText(
            ByteArrayInputStream(bytesOf(text, StandardCharsets.UTF_8)),
            limitBytes = 100
        )
        assertTrue("超过上限必须标记 truncated", r.truncated)
        assertEquals(100, r.text.length)
    }

    @Test
    fun `readAllText 截断在多字节字符中间时不误判为 GBK`() {
        // 构造 UTF-8 文本：每个汉字 3 字节
        val text = "测试".repeat(100) // 600 字节
        val full = bytesOf(text, StandardCharsets.UTF_8)
        // 关键：limit 设为 599，但流里还有第 600 字节，
        // 这样 readBytesLimited 才会标记 truncated=true，
        // 从而触发 trimIncompleteTail（裁掉尾部不完整的 3 字节序列）。
        val r = EncodingDetector.readAllText(
            ByteArrayInputStream(full),
            limitBytes = 599
        )
        assertTrue("应被标记为截断", r.truncated)
        // 核心断言：不应因为尾部不完整而整体判为 GBK
        assertEquals(
            "截断不应导致编码误判",
            StandardCharsets.UTF_8,
            r.charset
        )
    }

    @Test
    fun `readAllText 截断后文本仍可正常解码（无乱码）`() {
        val text = "中文测试".repeat(50) // 每字 3 字节，共 600 字节
        val full = bytesOf(text, StandardCharsets.UTF_8)

        val r = EncodingDetector.readAllText(ByteArrayInputStream(full), limitBytes = 598)
        assertTrue(r.truncated)
        // 裁掉尾部不完整序列后，解码结果应是原文的前缀
        assertTrue(
            "解码结果应为原文前缀，实际=${r.text.takeLast(10)}",
            text.startsWith(r.text)
        )
    }
}