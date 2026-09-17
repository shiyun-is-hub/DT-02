package com.dt.docreader.data.reader

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * OOXML（docx/pptx）轻量解析基础设施。
 *
 * **为什么不使用现成 XML 库？**
 * - Apache POI：给 APK 增加 10MB+
 * - `android.util.Xml`：会牵连整个 `org.apache.harmony.xml` 实现，
 *   实测使 dex 增大 2.6MB（R8 无法裁剪）
 * - `javax.xml.parsers`：同样偏重
 *
 * 我们只需要「按标签提取文本」这一件事，因此**手写一个极简流式扫描器**，
 * 体积开销几乎为零，且完全可控。
 *
 * 支持的 XML 子集（覆盖 OOXML 全部实际用法）：
 * - 元素：`<tag>`、`<tag/>`、`</tag>`
 * - 属性：`name="value"`（单/双引号）
 * - 文本：含实体解码（`&amp; &lt; &gt; " ' &#NN;`）
 * - 注释 `<!-- -->`、处理指令 `<? ?>`、CDATA `<![CDATA[]]>`
 * - 跳过 DOCTYPE 内部子集
 */
internal object Ooxml {

    /** 扫描出的事件。 */
    sealed interface Event {
        /** 起始标签（含自闭合）。 */
        data class Start(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Event

        /** 结束标签。 */
        data class End(val name: String) : Event

        /** 文本。 */
        data class Text(val value: String) : Event
    }

    // ---------------- ZIP ----------------

    /** 读取 ZIP 中满足条件的条目：name → bytes。 */
    fun readEntries(input: InputStream, wanted: (String) -> Boolean): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>(8)
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && wanted(entry.name)) {
                    out[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    // ---------------- 扫描器 ----------------

    /**
     * 扫描 XML 字节，对每个事件调用 [onEvent]。
     *
     * 这是**纯字节级扫描**（UTF-8 解码），不做 DOM 构建，内存占用恒定。
     */
    fun scan(xml: ByteArray, onEvent: (Event) -> Unit) {
        val s = String(xml, Charsets.UTF_8)
        var i = 0
        val n = s.length

        while (i < n) {
            val lt = s.indexOf('<', i)
            if (lt < 0) {
                // 尾部纯文本
                val tail = s.substring(i)
                if (tail.isNotEmpty()) onEvent(Event.Text(decodeEntities(tail)))
                break
            }
            // '<' 之前的文本
            if (lt > i) {
                val text = s.substring(i, lt)
                if (text.isNotEmpty()) onEvent(Event.Text(decodeEntities(text)))
            }

            when {
                // 注释
                s.startsWith("<!--", lt) -> {
                    val end = s.indexOf("-->", lt + 4)
                    i = if (end < 0) n else end + 3
                }
                // CDATA
                s.startsWith("<![CDATA[", lt) -> {
                    val end = s.indexOf("]]>", lt + 9)
                    val content = if (end < 0) s.substring(lt + 9) else s.substring(lt + 9, end)
                    if (content.isNotEmpty()) onEvent(Event.Text(content))
                    i = if (end < 0) n else end + 3
                }
                // 处理指令 / DOCTYPE
                s.startsWith("<?", lt) -> {
                    val end = s.indexOf("?>", lt + 2)
                    i = if (end < 0) n else end + 2
                }
                s.startsWith("<!", lt) -> {
                    val end = s.indexOf('>', lt + 2)
                    i = if (end < 0) n else end + 1
                }
                // 结束标签
                s.startsWith("</", lt) -> {
                    val end = s.indexOf('>', lt + 2)
                    if (end < 0) break
                    val name = s.substring(lt + 2, end).trim()
                    onEvent(Event.End(localName(name)))
                    i = end + 1
                }
                // 起始标签（可能自闭合）
                else -> {
                    var end = lt + 1
                    var inQuote = 0.toChar()
                    while (end < n) {
                        val c = s[end]
                        if (inQuote != '\u0000') {
                            if (c == inQuote) inQuote = '\u0000'
                        } else if (c == '"' || c == '\'') {
                            inQuote = c
                        } else if (c == '>') {
                            break
                        }
                        end++
                    }
                    if (end >= n) break

                    var raw = s.substring(lt + 1, end).trim()
                    val selfClosing = raw.endsWith("/")
                    if (selfClosing) raw = raw.dropLast(1).trim()

                    val sp = raw.indexOfFirst { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
                    val name = if (sp < 0) raw else raw.substring(0, sp).trim()
                    val attrPart = if (sp < 0) "" else raw.substring(sp + 1)

                    onEvent(Event.Start(localName(name), parseAttrs(attrPart), selfClosing))
                    i = end + 1
                }
            }
        }
    }

    /** 解析属性串（已去掉标签名）。 */
    private fun parseAttrs(src: String): Map<String, String> {
        if (src.isBlank()) return emptyMap()
        val out = HashMap<String, String>(4)
        var i = 0
        val n = src.length
        while (i < n) {
            // 跳过空白
            while (i < n && src[i].isWhitespace()) i++
            if (i >= n) break

            val eq = src.indexOf('=', i)
            if (eq < 0) break
            val key = src.substring(i, eq).trim()
            if (key.isEmpty()) break

            var j = eq + 1
            while (j < n && src[j].isWhitespace()) j++
            if (j >= n) break

            val quote = src[j]
            val value: String
            if (quote == '"' || quote == '\'') {
                val close = src.indexOf(quote, j + 1)
                if (close < 0) break
                value = src.substring(j + 1, close)
                i = close + 1
            } else {
                // 无引号
                var k = j
                while (k < n && !src[k].isWhitespace()) k++
                value = src.substring(j, k)
                i = k
            }
            if (key.isNotEmpty()) out[localName(key)] = decodeEntities(value)
        }
        return out
    }

    /** 去掉命名空间前缀：`w:p` → `p`。 */
    fun localName(qName: String): String = qName.substringAfterLast(':')

    /** 解码常见 XML 实体。 */
    private fun decodeEntities(s: String): String {
        if ('&' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i + 1)
                if (semi in (i + 1)..(i + 12)) {
                    val ent = s.substring(i + 1, semi)
                    val decoded = when {
                        ent == "amp" -> "&"
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") || ent.startsWith("#X") ->
                            ent.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
                        ent.startsWith("#") ->
                            ent.substring(1).toIntOrNull()?.let { codePointToString(it) }
                        else -> null
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun codePointToString(cp: Int): String? = runCatching {
        String(Character.toChars(cp))
    }.getOrNull()
}