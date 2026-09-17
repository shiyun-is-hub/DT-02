package com.dt.docreader.domain.model

/**
 * 文档类型枚举：与来源格式解耦的统一分类。
 *
 * 设计说明：
 * - 富文本/二进制类（PDF/WORD/PPT）走重型解析器（后续 P3–P5）。
 * - 文本大类（TXT/MARKDOWN/CODE/HTML/JSON/XML/CSV/YAML/CONFIG）本质上都是
 *   纯文本，区别只在「如何切分成 IR Block」与「用哪种语法高亮」，
 *   因此统归为文本家族，由各自 Reader 处理。
 */
enum class FileKind {
    TXT,
    MARKDOWN,
    CODE,
    HTML,
    XML,
    JSON,
    CSV,
    YAML,
    CONFIG,
    PDF,
    WORD_DOCX,
    WORD_DOC,
    PPT_PPTX,
    PPT_PPT,
    UNKNOWN;

    /** 是否属于「纯文本家族」（可由轻量 Reader 直接处理）。 */
    val isTextFamily: Boolean
        get() = this in setOf(TXT, MARKDOWN, CODE, HTML, XML, JSON, CSV, YAML, CONFIG)

    companion object {
        /** 根据扩展名判断类型。 */
        fun fromExtension(fileName: String): FileKind {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                // ---- 纯文本 ----
                "txt", "text", "log", "nfo", "readme" -> TXT

                // ---- Markdown ----
                "md", "markdown", "mdown", "mkd" -> MARKDOWN

                // ---- 网页 / 结构化标记 ----
                "html", "htm", "xhtml" -> HTML
                "xml", "xsd", "xsl", "xslt", "svg", "plist" -> XML
                "json", "jsonc", "json5" -> JSON

                // ---- 数据表 / 配置 ----
                "csv", "tsv" -> CSV
                "yaml", "yml" -> YAML
                "ini", "properties", "cfg", "conf", "toml", "env", "gradle",
                "gitignore", "editorconfig", "pro" -> CONFIG

                // ---- 编程语言（代码高亮） ----
                "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
                "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "rs", "go", "swift",
                "php", "rb", "sh", "bash", "zsh", "fish", "sql", "lua", "dart",
                "r", "scala", "clj", "ex", "exs", "erl", "hs", "m", "mm",
                "vb", "asm", "s", "pas", "pl", "pm", "groovy", "vue", "svelte" -> CODE

                // ---- 二进制 / 富文本（后续阶段） ----
                "pdf" -> PDF
                "docx" -> WORD_DOCX
                "doc" -> WORD_DOC
                "pptx" -> PPT_PPTX
                "ppt" -> PPT_PPT

                else -> UNKNOWN
            }
        }

        /**
         * 根据文件头 Magic Number 判断类型。
         * @param head 文件前若干字节（建议 >= 8）
         */
        fun fromMagicNumber(head: ByteArray): FileKind? {
            if (head.size >= 4) {
                // OOXML: PK\x03\x04 -> docx / pptx / xlsx（需扩展名区分）
                if (head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
                    head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
                ) return WORD_DOCX // 占位，实际由扩展名细化

                // PDF: %PDF
                if (head[0] == 0x25.toByte() && head[1] == 0x50.toByte() &&
                    head[2] == 0x44.toByte() && head[3] == 0x46.toByte()
                ) return PDF

                // OLE2: D0 CF 11 E0 -> doc / ppt（需扩展名区分）
                if (head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() &&
                    head[2] == 0x11.toByte() && head[3] == 0xE0.toByte()
                ) return WORD_DOC // 占位，实际由扩展名细化
            }
            return null
        }
    }
}
