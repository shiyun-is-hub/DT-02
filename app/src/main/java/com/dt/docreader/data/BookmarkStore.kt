package com.dt.docreader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 书签存储。
 *
 * 设计要点（为什么用 blockIndex 而不是 item index）：
 * - RenderPlan 产出的 item 索引会随**字号变化**、**是否拆分超长段落**而改变，
 *   用它做锚点，用户调一次字号书签就全错位了。
 * - `blockIndex` 指向 DocumentModel 中 block 的稳定序号，与渲染细节无关。
 * - 额外记录 [preview] 文本，用于在书签列表里显示上下文，
 *   即使文档被外部修改也能让用户辨认。
 *
 * 同时提供"阅读位置"（每文档一条，用于自动恢复上次位置）。
 */
object BookmarkStore {

    private const val PREF = "docreader_bookmarks"

    data class Bookmark(
        val blockIndex: Int,
        val preview: String,
        val createdAt: Long
    )

    /** 每文档最多保存的书签数，避免单文件无限增长。 */
    private const val MAX_PER_DOC = 100

    // ---------------- 书签 ----------------

    fun list(context: Context, docPath: String): List<Bookmark> {
        val raw = prefs(context).getString(keyBookmarks(docPath), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Bookmark(
                            blockIndex = o.optInt("b", -1),
                            preview = o.optString("p", ""),
                            createdAt = o.optLong("t", 0L)
                        )
                    )
                }
            }.filter { it.blockIndex >= 0 }
        }.getOrDefault(emptyList())
    }

    /** 添加书签；同一 blockIndex 已存在则不重复添加。 */
    fun add(context: Context, docPath: String, blockIndex: Int, preview: String): List<Bookmark> {
        val current = list(context, docPath)
        if (current.any { it.blockIndex == blockIndex }) return current
        val updated = (current + Bookmark(blockIndex, preview.take(120), System.currentTimeMillis()))
            .sortedBy { it.blockIndex }
            .take(MAX_PER_DOC)
        persist(context, docPath, updated)
        return updated
    }

    fun remove(context: Context, docPath: String, blockIndex: Int): List<Bookmark> {
        val updated = list(context, docPath).filterNot { it.blockIndex == blockIndex }
        persist(context, docPath, updated)
        return updated
    }

    fun clear(context: Context, docPath: String): List<Bookmark> {
        prefs(context).edit().remove(keyBookmarks(docPath)).apply()
        return emptyList()
    }

    private fun persist(context: Context, docPath: String, items: List<Bookmark>) {
        val arr = JSONArray()
        items.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("b", b.blockIndex)
                    put("p", b.preview)
                    put("t", b.createdAt)
                }
            )
        }
        prefs(context).edit().putString(keyBookmarks(docPath), arr.toString()).apply()
    }

    // ---------------- 阅读位置（自动恢复） ----------------

    fun loadPosition(context: Context, docPath: String): Int {
        return prefs(context).getInt(keyPosition(docPath), -1)
    }

    fun savePosition(context: Context, docPath: String, blockIndex: Int) {
        prefs(context).edit().putInt(keyPosition(docPath), blockIndex).apply()
    }

    // ---------------- 内部 ----------------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 路径含 `/` 等字符，用 hashCode 做 key，避免 SharedPreferences key 过长/非法。 */
    private fun keyBookmarks(docPath: String) = "bm_${docPath.hashCode()}"
    private fun keyPosition(docPath: String) = "pos_${docPath.hashCode()}"
}