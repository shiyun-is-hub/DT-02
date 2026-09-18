package com.dt.docreader.domain.model

/** 文档元信息。 */
data class DocumentMeta(
    val fileName: String,
    val fileSize: Long = 0L,
    val kind: FileKind = FileKind.UNKNOWN,
    val encoding: String? = null,
    val pageCount: Int = 0,
    /** 语言显示名（代码/结构化文本用），如 "Kotlin"。 */
    val language: String? = null
)

/** 章节 / 页 / 幻灯片。 */
data class Section(
    val title: String? = null,
    val pageIndex: Int = 0,
    val blocks: List<Block> = emptyList()
)

/** 统一中间表示（IR）的内容块。 */
sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class BulletList(val items: List<String>, val ordered: Boolean = false) : Block
    data class CodeBlock(val language: String?, val code: String) : Block
    data class TableBlock(val rows: List<List<String>>) : Block
    data class ImageBlock(val uri: String, val alt: String? = null) : Block
    data class Slide(val title: String?, val body: List<String>) : Block
}

/** 解析后的文档模型（UI 只依赖它，与来源格式无关）。 */
data class DocumentModel(
    val meta: DocumentMeta,
    val sections: List<Section> = emptyList(),
    /**
     * 原始文本（仅文本家族填充；docx/pptx 等容器格式为 null）。
     *
     * 为什么需要它：
     * - **编辑功能**必须基于原文，而 blocks 是解析后的结构，无法无损还原原文
     *   （例如 Markdown 的内联标记在解析时已被剥离）。
     * - 搜索功能若基于 blocks，会漏掉被剥离的标记文本，基于原文更准确。
     *
     * 内存代价：文本家族的文件上限为 32MB，原文与解析结果同时存在，
     * 峰值内存约为文件大小的 2~3 倍，在移动设备可接受。
     */
    val rawText: String? = null
) {
    /** 便捷：拍平所有 block。 */
    val allBlocks: List<Block> get() = sections.flatMap { it.blocks }

    /** 是否可编辑（仅文本家族且保留了原文）。 */
    val isEditable: Boolean get() = rawText != null
}