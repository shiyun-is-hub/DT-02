package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section

/** CodeReader 占位实现（后续阶段实现，当前仅保证分发链路完整）。 */
class CodeReader : DocumentReader {
    override fun supports(kind: FileKind): Boolean = false

    override suspend fun read(source: FileSource): DocumentModel = DocumentModel(
        meta = DocumentMeta(
            fileName = source.fileName,
            fileSize = source.size,
            kind = source.kind
        ),
        sections = listOf(Section(title = "暂未实现", blocks = emptyList()))
    )
}
