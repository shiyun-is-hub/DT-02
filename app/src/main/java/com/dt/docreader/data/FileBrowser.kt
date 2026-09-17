package com.dt.docreader.data

import java.io.File
import java.util.Locale

/**
 * 文件浏览器：直接基于 [java.io.File] 遍历存储。
 *
 * 依赖 MANAGE_EXTERNAL_STORAGE 权限；无权限时只能看到 App 私有目录。
 */
object FileBrowser {

    /** 条目类型 */
    enum class EntryType { DIR, FILE, UNSUPPORTED }

    data class Entry(
        val file: File,
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastModified: Long,
        val type: EntryType
    ) {
        val path: String get() = file.absolutePath
    }

    /** 支持的文本类扩展名（小写，含点号前缀不带点） */
    private val TEXT_EXT = setOf(
        "txt", "text", "log", "nfo", "md", "markdown", "mdown", "mkd",
        "kt", "kts", "java", "py", "js", "mjs", "ts", "jsx", "tsx",
        "c", "h", "cpp", "cc", "cxx", "hpp", "rs", "go", "rb", "php",
        "swift", "cs", "scala", "sh", "bash", "zsh", "bat", "cmd",
        "json", "json5", "xml", "html", "htm", "xhtml", "svg",
        "csv", "tsv", "yaml", "yml", "toml", "ini", "properties",
        "env", "cfg", "conf", "gradle", "pro", "sql", "gitignore",
        "dockerfile", "makefile", "lua", "r", "dart", "vue", "svelte"
    )

    /**
     * 读取目录内容。
     *
     * @param dir 目标目录
     * @param showHidden 是否显示隐藏文件
     * @param textOnly 只显示可阅读的文本类文件
     */
    fun list(dir: File, showHidden: Boolean = false, textOnly: Boolean = true): List<Entry> {
        val children = dir.listFiles() ?: return emptyList()
        return children
            .asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .mapNotNull { f ->
                val isDir = f.isDirectory
                if (!isDir && f.length() == 0L && !f.exists()) return@mapNotNull null
                val ext = f.extension.lowercase(Locale.ROOT)
                // 无扩展名的文件（如 README、Dockerfile）也按文本处理
                val isText = isDir || ext in TEXT_EXT || (ext.isEmpty() && f.isFile)
                Entry(
                    file = f,
                    name = f.name,
                    isDir = isDir,
                    size = if (isDir) 0L else f.length(),
                    lastModified = f.lastModified(),
                    type = when {
                        isDir -> EntryType.DIR
                        isText && !textOnly -> EntryType.FILE
                        isText -> EntryType.FILE
                        else -> EntryType.UNSUPPORTED
                    }
                )
            }
            .filter { !textOnly || it.type != EntryType.UNSUPPORTED }
            .sortedWith(
                compareByDescending<Entry> { it.isDir }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            .toList()
    }

    /** 最常见的存储根目录候选 */
    fun storageRoots(): List<File> =
        listOf(
            File("/storage/emulated/0"),
            File("/sdcard")
        ).distinctBy { it.absolutePath }.filter { it.exists() }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }
}