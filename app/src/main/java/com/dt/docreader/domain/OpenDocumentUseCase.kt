package com.dt.docreader.domain

import com.dt.docreader.data.ReaderFactory
import com.dt.docreader.data.reader.FileSource
import com.dt.docreader.domain.model.DocumentModel

/** 打开文档用例：分发 -> 解析 -> 返回 DocumentModel。 */
class OpenDocumentUseCase {

    suspend fun execute(source: FileSource): Result<DocumentModel> = runCatching {
        if (!ReaderFactory.isSupported(source.fileName)) {
            throw UnsupportedFormatException(source.fileName)
        }
        val reader = ReaderFactory.resolve(source.kind)
            ?: throw UnsupportedFormatException(source.fileName)
        reader.read(source)
    }
}

class UnsupportedFormatException(fileName: String) :
    Exception("暂不支持的格式：$fileName")
