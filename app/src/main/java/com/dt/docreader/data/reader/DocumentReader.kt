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

    /**
     * 直接从绝对路径构造（文件管理器场景）。
     *
     * 优点：不经过 ContentResolver，性能更好，且适用于 /sdcard 任意文件。
     */
    fun fromFile(file: java.io.File): FileSource {
        val name = file.name
        val kind = FileKind.fromExtension(name).let {
            // 无扩展名的文件按纯文本处理（README / Dockerfile / LICENSE …）
            if (it == FileKind.UNKNOWN && !name.contains('.')) FileKind.TXT else it
        }
        return FileSource(
            fileName = name,
            mimeType = null,
            size = file.length(),
            kind = kind
        ) {
            java.io.FileInputStream(file)
        }
    }

    fun fromUri(resolver: ContentResolver, uri: Uri): FileSource {
        var name = uri.lastPathSegment ?: "unknown"
        var size = 0L
        // 部分 provider 不支持 query（会抛异常），不能让整个打开流程失败：
        // 拿不到 DISPLAY_NAME / SIZE 时退化为用 URI 末段当文件名。
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                }
            }
        }
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val kind = FileKind.fromExtension(name).let {
            // 与 fromFile 保持一致：无扩展名按纯文本处理
            if (it == FileKind.UNKNOWN && !name.contains('.')) FileKind.TXT else it
        }
        return FileSource(name, mime, size, kind) {
            resolver.openInputStream(uri) ?: throw IllegalStateException("无法打开文件流")
        }
    }
}