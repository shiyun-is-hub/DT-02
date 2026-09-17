package com.dt.docreader.data

import com.dt.docreader.data.reader.DocumentReader
import com.dt.docreader.data.reader.TxtReader
import com.dt.docreader.domain.model.FileKind

/**
 * 分发器：根据 FileKind 返回对应 Reader。
 * 对标 OpenKB converter.py 的分发逻辑，白名单集中在此处。
 */
object ReaderFactory {

    /** 支持的扩展名白名单（比 OpenKB 扩充了 .doc/.ppt/.kt 等）。 */
    val SUPPORTED_EXTENSIONS = setOf(
        "txt", "md", "markdown", "pdf", "doc", "docx", "ppt", "pptx",
        "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go",
        "json", "xml", "html", "css"
    )

    private val readers: List<DocumentReader> = listOf(
        TxtReader()
        // P2+: MarkdownReader(), CodeReader()
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
}
