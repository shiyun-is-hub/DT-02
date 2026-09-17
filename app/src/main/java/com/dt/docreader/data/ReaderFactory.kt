package com.dt.docreader.data

import com.dt.docreader.data.reader.CodeReader
import com.dt.docreader.data.reader.DocumentReader
import com.dt.docreader.data.reader.MarkdownReader
import com.dt.docreader.data.reader.StructuredTextReader
import com.dt.docreader.data.reader.TxtReader
import com.dt.docreader.domain.model.FileKind

/**
 * 分发器：根据 FileKind 返回对应 Reader。
 *
 * 对标 OpenKB converter.py 的分发逻辑，把「格式白名单」集中在此处：
 * - 已实现（P1/P2）：TXT、MARKDOWN、CODE、JSON/XML/HTML/CSV/YAML/CONFIG
 * - 待实现（P3–P5）：PDF、WORD、PPT
 *
 * 分发采用「谁支持谁处理」策略，新增格式只需实现 DocumentReader 并注册到列表。
 */
object ReaderFactory {

    /** 已支持的全部扩展名（用于 UI 侧的文件选择器过滤与提示）。 */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        // 纯文本
        "txt", "text", "log", "nfo", "readme",
        // Markdown
        "md", "markdown", "mdown", "mkd",
        // 网页 / 结构化
        "html", "htm", "xhtml", "xml", "xsd", "xsl", "xslt", "svg", "plist",
        "json", "jsonc", "json5",
        // 数据 / 配置
        "csv", "tsv", "yaml", "yml", "ini", "properties", "cfg", "conf",
        "toml", "env", "gradle", "gitignore", "editorconfig", "pro",
        // 代码
        "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "rs", "go", "swift",
        "php", "rb", "sh", "bash", "zsh", "fish", "sql", "lua", "dart",
        "r", "scala", "clj", "ex", "exs", "erl", "hs", "m", "mm",
        "vb", "asm", "s", "pas", "pl", "pm", "groovy", "vue", "svelte"
    )

    /** 已注册的 Reader（顺序即优先级）。 */
    private val readers: List<DocumentReader> = listOf(
        TxtReader(),
        MarkdownReader(),
        CodeReader(),
        StructuredTextReader()
        // P3:  PdfReader()
        // P4:  WordReader()
        // P5:  PptReader()
    )

    /** 根据类型解析可用 Reader，找不到返回 null。 */
    fun resolve(kind: FileKind): DocumentReader? =
        readers.firstOrNull { it.supports(kind) }

    /** 是否支持该文件名。 */
    fun isSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_EXTENSIONS
    }

    /** 供 UI 展示：已支持格式的简要说明。 */
    fun supportSummary(): String =
        "支持：txt · md · 代码(kt/java/py/js…等 40+ 语言) · json/xml/html/csv/yaml/ini 配置"
}