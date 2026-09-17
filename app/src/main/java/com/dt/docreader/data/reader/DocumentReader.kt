package com.dt.docreader.data.reader

import android.content.ContentResolver
import android.net.Uri
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.domain.model.FileKind
import java.io.InputStream

/** 文件来源抽象：屏蔽 content:// 与真实路径差异。 */
data class FileSource(
    val fileName: String,
    val mimeType: String?,
    val size: Long,
    val kind: FileKind,
    val openStream: () -> InputStream
)

/** 各类格式读取器统一接口。 */
interface DocumentReader {
    fun supports(kind: FileKind): Boolean
    suspend fun read(source: FileSource): DocumentModel
}

/** 从 Uri 构造 FileSource 的工厂。 */
object FileSourceFactory {
    fun fromUri(resolver: ContentResolver, uri: Uri): FileSource {
        var name = uri.lastPathSegment ?: "unknown"
        var size = 0L
        var mime: String? = null
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
        mime = resolver.getType(uri)
        val kind = FileKind.fromExtension(name)
        return FileSource(name, mime, size, kind) {
            resolver.openInputStream(uri) ?: throw IllegalStateException("无法打开文件流")
        }
    }
}
