package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentMeta
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import com.dt.docreader.domain.model.Section
import com.dt.docreader.infra.EncodingDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** txt 读取器：编码探测 + 段落切分。 */
class TxtReader : DocumentReader {

    override fun supports(kind: FileKind): Boolean = kind == FileKind.TXT

    override suspend fun read(source: FileSource): DocumentModel = withContext(Dispatchers.IO) {
        val result = source.openStream().use { EncodingDetector.readAllText(it) }
        val text = result.text

        val blocks = mutableListOf<Block>()
        val buffer = StringBuilder()
        fun flushBuffer() {
            if (buffer.isNotBlank()) {
                blocks.add(Block.Paragraph(buffer.toString().trim()))
            }
            buffer.clear()
        }

        text.split("\n").forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushBuffer()
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                    flushBuffer()
                    blocks.add(Block.Heading(level, trimmed.drop(level).trim()))
                }
                else -> {
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(trimmed)
                }
            }
        }
        flushBuffer()

        DocumentModel(
            meta = DocumentMeta(
                fileName = source.fileName,
                fileSize = source.size,
                kind = FileKind.TXT,
                encoding = result.charset.name()
            ),
            sections = listOf(Section(title = null, pageIndex = 0, blocks = blocks))
        )
    }
}
