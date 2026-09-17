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
        val uri: Uri get() = Uri.fromFile(File(path))
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

    /** 过滤掉已经不存在的文件。 */
    fun listExisting(context: Context): List<RecentItem> {
        val items = list(context)
        val existing = items.filter { File(it.path).exists() }
        if (existing.size != items.size) persist(context, existing)
        return existing
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