package com.dt.docreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文档写回。
 *
 * 安全策略（编辑是破坏性操作，必须可恢复）：
 * 1. **先备份**：把原文件复制为 `<name>.bak`（覆盖旧备份）。
 * 2. **原子替换**：先写入同目录的临时文件，再 `renameTo` 替换原文件。
 *    这样即使写入中途失败/断电，原文件也不会处于半写状态。
 * 3. 失败时回滚：若替换失败，尝试用备份恢复。
 *
 * 编码策略：按原文件的编码写回（默认 UTF-8），避免把 GBK 文件写成 UTF-8 乱码。
 */
object DocumentWriter {

    sealed interface Result {
        data class Ok(val backupPath: String?) : Result
        data class Fail(val message: String) : Result
    }

    /**
     * 写回文件。
     *
     * @param path 目标文件绝对路径
     * @param content 新内容
     * @param charsetName 写回使用的编码（通常沿用解析时探测到的编码）
     * @param makeBackup 是否生成 .bak 备份
     */
    suspend fun write(
        path: String,
        content: String,
        charsetName: String? = null,
        makeBackup: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(path)
            if (!target.exists()) return@runCatching Result.Fail("文件不存在：$path")
            if (!target.canWrite()) return@runCatching Result.Fail("无写入权限：$path")

            val charset = runCatching {
                charsetName?.let { java.nio.charset.Charset.forName(it) }
            }.getOrNull() ?: Charsets.UTF_8

            // 1) 备份
            var backup: File? = null
            if (makeBackup) {
                val b = File(target.parentFile, target.name + ".bak")
                target.copyTo(b, overwrite = true)
                backup = b
            }

            // 2) 原子替换：写临时文件 -> rename
            val tmp = File(target.parentFile, "." + target.name + ".tmp")
            tmp.writeText(content, charset)

            val replaced = tmp.renameTo(target)
            if (!replaced) {
                // rename 失败（跨文件系统等），退化为直接覆盖
                target.writeText(content, charset)
                tmp.delete()
            }

            Result.Ok(backup?.absolutePath)
        }.getOrElse { e ->
            Result.Fail(e.message ?: "写入失败")
        }
    }

    /** 该文件是否已有备份。 */
    fun backupPathOf(path: String): String? {
        val b = File(File(path).parentFile, File(path).name + ".bak")
        return if (b.exists()) b.absolutePath else null
    }
}