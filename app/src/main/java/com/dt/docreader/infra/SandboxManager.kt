package com.dt.docreader.infra

import android.content.Context
import java.io.File

/**
 * 运行时沙箱：应用私有目录隔离。
 * 工作区 sandbox/ 仅作设计参照，真正运行沙箱在此。
 */
class SandboxManager(private val context: Context) {

    /** 持久目录：filesDir/documents */
    val documentsDir: File get() = File(context.filesDir, "documents").apply { mkdirs() }

    /** 临时解析目录：cacheDir/parse （解析后可清理） */
    val parseTmpDir: File get() = File(context.cacheDir, "parse").apply { mkdirs() }

    /** 清理临时目录。 */
    fun clearTmp() {
        parseTmpDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
