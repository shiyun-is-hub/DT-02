package com.dt.docreader.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 最近打开文件的持久化存储。
 *
 * 用 SharedPreferences + JSON 实现（轻量，无需 Room）。
 * 记录：绝对路径、显示名、大小、时间戳。
 * 最多保留 [MAX_ITEMS] 条，按时间倒序。
 */
object RecentStore {

    private const val TAG = "RecentStore"
    private const val PREF = "docreader_recent"
    private const val KEY = "items"
    private const val MAX_ITEMS = 50

    data class RecentItem(
        val path: String,
        val name: String,
        val size: Long,
        val timestamp: Long
    ) {
        /** 是否为 SAF `content://` 记录（而非文件系统路径）。 */
        val isContentUri: Boolean get() = path.startsWith("content://")
    }

    fun list(context: Context): List<RecentItem> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("path")
                    if (path.isNullOrEmpty()) continue
                    add(
                        RecentItem(
                            path = path,
                            name = o.optString("name", File(path).name),
                            size = o.optLong("size", 0L),
                            timestamp = o.optLong("timestamp", 0L)
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.w(TAG, "解析最近记录失败，已重置", e)
            emptyList()
        }
    }

    /** 记录一次打开（去重后置顶）。 */
    fun add(context: Context, path: String, name: String, size: Long) {
        val current = list(context).filterNot { it.path == path }
        val updated = buildList {
            add(RecentItem(path, name, size, System.currentTimeMillis()))
            addAll(current)
        }.take(MAX_ITEMS)
        persist(context, updated)
    }

    fun remove(context: Context, path: String) {
        persist(context, list(context).filterNot { it.path == path })
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /**
     * 过滤掉已经失效的记录。
     *
     * **注意**：不能简单地用 `File(path).exists()` 判定 ——
     * SAF `content://` 记录用 `File()` 永远不存在，会被**误删**。
     * 因此按记录类型分别判定：
     * - `content://`：检查是否仍持有可读权限（`checkUriPermission`）；
     * - 文件路径：检查 `File(path).exists()`。
     */
    fun listExisting(context: Context): List<RecentItem> {
        val items = list(context)
        val existing = items.filter { exists(context, it) }
        if (existing.size != items.size) persist(context, existing)
        return existing
    }

    /** 单条记录是否仍可用。 */
    private fun exists(context: Context, item: RecentItem): Boolean {
        return if (item.isContentUri) {
            // 权限被撤销 / 文档被删除 -> 无读权限，视为失效
            val uri = runCatching { Uri.parse(item.path) }.getOrNull() ?: return false
            context.checkUriPermission(
                uri,
                android.os.Process.myPid(),
                android.os.Process.myUid(),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            runCatching { File(item.path).exists() }.getOrDefault(false)
        }
    }

    private fun persist(context: Context, items: List<RecentItem>) {
        val arr = JSONArray()
        items.forEach { it ->
            arr.put(
                JSONObject().apply {
                    put("path", it.path)
                    put("name", it.name)
                    put("size", it.size)
                    put("timestamp", it.timestamp)
                }
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}