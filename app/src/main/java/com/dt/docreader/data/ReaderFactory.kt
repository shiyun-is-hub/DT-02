package com.dt.docreader.data

import com.dt.docreader.data.reader.CodeReader
import com.dt.docreader.data.reader.DocumentReader
import com.dt.docreader.data.reader.MarkdownReader
import com.dt.docreader.data.reader.PdfReader
import com.dt.docreader.data.reader.PptReader
import com.dt.docreader.data.reader.StructuredTextReader
import com.dt.docreader.data.reader.TxtReader
import com.dt.docreader.data.reader.WordReader
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

    /** `supportedCache` 的最大条目数，防止无限增长。 */
    private const val SUPPORTED_CACHE_MAX = 512

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
        "vb", "asm", "s", "pas", "pl", "pm", "groovy", "vue", "svelte",
        // Office 文档（P4/P5：docx / pptx 零依赖解析；doc/ppt 给出转换提示）
        "docx", "doc", "pptx", "ppt",
        // PDF（占位：提示使用专用查看器）
        "pdf"
    )

    /** 已注册的 Reader（顺序即优先级）。 */
    private val readers: List<DocumentReader> = listOf(
        TxtReader(),
        MarkdownReader(),
        CodeReader(),
        StructuredTextReader(),
        WordReader(),
        PptReader(),
        PdfReader()
    )

    /**
     * FileKind -> Reader 的查找缓存。
     *
     * 性能：把「每次遍历列表」变成「一次性构建 + O(1) 查表」。
     * 用 lazy 保证只构建一次，且不阻塞类加载。
     */
    private val kindCache: Map<FileKind, DocumentReader> by lazy {
        val map = HashMap<FileKind, DocumentReader>(readers.size * 2)
        for (reader in readers) {
            for (kind in FileKind.entries) {
                if (kind != FileKind.UNKNOWN && map[kind] == null && reader.supports(kind)) {
                    map[kind] = reader
                }
            }
        }
        map
    }

    /** 扩展名支持判定缓存（避免重复 lowercase/substring 计算）。
     *
     * 用 `ConcurrentHashMap`：`isSupported` 可能被 Compose 重组线程与
     * 后台解析线程并发调用，普通 `HashMap` 在并发写入时会损坏
     * （扩容期链表成环导致死循环，或读到错误值）。
     * 同时限制容量，避免长期运行下无上限增长。
     */
    private val supportedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>(64)

    /** 根据类型解析可用 Reader，找不到返回 null。 */
    fun resolve(kind: FileKind): DocumentReader? = kindCache[kind]

    /** 是否支持该文件名。 */
    fun isSupported(fileName: String): Boolean {
        // 缓存整个文件名 -> 结果，避免热路径反复做字符串切分
        supportedCache[fileName]?.let { return it }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val result = ext in SUPPORTED_EXTENSIONS
        // 超过上限时不再写入（退化为每次计算，正确性不受影响）
        if (supportedCache.size < SUPPORTED_CACHE_MAX) supportedCache[fileName] = result
        return result
    }

    /** 供 UI 展示：已支持格式的简要说明。 */
    fun supportSummary(): String =
        "支持：txt · md · 代码(kt/java/py/js…等 40+ 语言) · json/xml/html/csv/yaml/ini · docx/pptx（PDF 待专用查看器）"
}