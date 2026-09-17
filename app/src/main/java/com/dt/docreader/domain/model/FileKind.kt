package com.dt.docreader.domain.model

/** 文档类型枚举：与来源格式解耦的统一分类。 */
enum class FileKind {
    TXT, MARKDOWN, PDF, WORD_DOCX, WORD_DOC, PPT_PPTX, PPT_PPT, CODE, UNKNOWN;

    companion object {
        /** 根据扩展名判断类型。 */
        fun fromExtension(fileName: String): FileKind {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "txt" -> TXT
                "md", "markdown" -> MARKDOWN
                "pdf" -> PDF
                "docx" -> WORD_DOCX
                "doc" -> WORD_DOC
                "pptx" -> PPT_PPTX
                "ppt" -> PPT_PPT
                "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h", "rs", "go", "json", "xml", "html", "css" -> CODE
                else -> UNKNOWN
            }
        }

        /**
         * 根据文件头 Magic Number 判断类型。
         * @param head 文件前若干字节（建议 >= 8）
         */
        fun fromMagicNumber(head: ByteArray): FileKind? {
            if (head.size >= 4) {
                // OOXML: PK\x03\x04 -> docx / pptx（需扩展名区分）
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
