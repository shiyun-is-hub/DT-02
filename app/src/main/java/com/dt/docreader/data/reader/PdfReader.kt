package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section

/**
 * PDF 读取器 —— **占位实现（暂不支持）**。
 *
 * 说明：PDF 需要专门的页面渲染/缩放/文本选择架构，
 * 计划由**独立的 PDF 查看器**承担，因此这里只给出明确提示，
 * 不做半成品的文本提取（避免用户误以为"能打开"）。
 */
class PdfReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean = kind == FileKind.PDF

    override suspend fun read(source: FileSource): DocumentModel = DocumentModel(
        meta = DocumentMeta(
            fileName = source.fileName,
            fileSize = source.size,
            kind = FileKind.PDF,
            language = "PDF",
            pageCount = 0
        ),
        sections = listOf(
            Section(
                title = "PDF 暂不支持",
                pageIndex = 0,
                blocks = listOf(
                    Block.Heading(2, "PDF 请使用专用查看器"),
                    Block.Paragraph(
                        "本阅读器当前专注于**文本类文档**（txt / md / 代码 / json / csv 等）" +
                            "与 **Office 文档**（Word / PPT）。\n\n" +
                            "PDF 需要页面渲染、缩放、文本选择等专门能力，" +
                            "将由独立的 PDF 查看器提供，不在此处实现。\n\n" +
                            "建议：使用系统自带或专门的 PDF 阅读 App 打开此文件。"
                    )
                )
            )
        )
    )
}