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
 * - 额外记录 [Bookmark.label]（结构性标签，如"段落 #12"），
 *   **不含正文**，用于在书签列表里区分条目。
 *
 * 同时提供"阅读位置"（每文档一条，用于自动恢复上次位置）。
 */
object BookmarkStore {

    private const val PREF = "docreader_bookmarks"

    /**
     * 书签。
     *
     * **隐私约束**：不持久化文档正文。
     * 早期版本用 `preview.take(120)` 把正文片段写入 SharedPreferences，
     * 这会让文档内容落到磁盘上（违反"不持久化文档内容"原则）。
     * 现在只保存**结构性提示**（block 类型 + 序号），
     * 足以在书签列表中区分条目，且不泄露内容。
     */
    data class Bookmark(
        val blockIndex: Int,
        /** 结构性标签，如 "段落 #12"、"代码 #3"。不含正文。 */
        val label: String,
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
                    // 用 optJSONObject：单项损坏时跳过该项，而不是让整个列表变空
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        Bookmark(
                            blockIndex = o.optInt("b", -1),
                            label = o.optString("l", ""),
                            createdAt = o.optLong("t", 0L)
                        )
                    )
                }
            }.filter { it.blockIndex >= 0 }
        }.getOrDefault(emptyList())
    }

    /** 添加书签；同一 blockIndex 已存在则不重复添加。 */
    fun add(context: Context, docPath: String, blockIndex: Int, label: String): List<Bookmark> {
        val current = list(context, docPath)
        if (current.any { it.blockIndex == blockIndex }) return current
        val updated = (current + Bookmark(blockIndex, label.take(40), System.currentTimeMillis()))
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
                    put("l", b.label)
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

    /**
     * 路径 -> SharedPreferences key。
     *
     * **不能用 `hashCode()`**：`String.hashCode()` 只有 32 位，
     * 不同路径完全可能碰撞（如 `/a/b.txt` 与 `/c/d.txt`），
     * 一旦碰撞，两个文件的书签/阅读位置会**互相覆盖**。
     *
     * 这里改用 **SHA-256 前 16 字节的十六进制**：
     * - 碰撞概率可忽略（128 位）；
     * - 定长 32 字符，不含 `/` 等非法字符，安全用作 key。
     */
    private fun docKey(docPath: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(docPath.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (i in 0 until 16) {
            val v = digest[i].toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private fun keyBookmarks(docPath: String) = "bm_${docKey(docPath)}"
    private fun keyPosition(docPath: String) = "pos_${docKey(docPath)}"

    private val HEX = "0123456789abcdef".toCharArray()
}