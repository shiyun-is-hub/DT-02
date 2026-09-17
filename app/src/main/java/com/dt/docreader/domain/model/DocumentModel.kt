package com.dt.docreader.domain.model

/** 文档元信息。 */
data class DocumentMeta(
    val fileName: String,
    val fileSize: Long = 0L,
    val kind: FileKind = FileKind.UNKNOWN,
    val encoding: String? = null,
    val pageCount: Int = 0
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
    val sections: List<Section> = emptyList()
) {
    /** 便捷：拍平所有 block。 */
    val allBlocks: List<Block> get() = sections.flatMap { it.blocks }
}
