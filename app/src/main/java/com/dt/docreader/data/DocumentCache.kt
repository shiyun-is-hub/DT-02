package com.dt.docreader.data

import com.dt.docreader.domain.model.DocumentModel
import java.io.File

/**
 * 已解析文档的内存缓存。
 *
 * ## 设计目标
 *
 * 同一文件反复打开（返回列表再点进来）时，避免重复解析 ——
 * 大文件解析成本 O(N)，30 万行 log 约 5 秒，缓存后是 O(1) 命中。
 *
 * ## 为什么是内存缓存
 *
 * - **进程存活期有效，进程被杀即清**（符合需求："用户删除后台时连带清除"）；
 * - 不落盘 → 不产生隐私残留，不占用用户存储；
 * - 无需手动清理逻辑，交给系统 GC / 进程回收。
 *
 * ## 失效判定
 *
 * key 里带上**文件最后修改时间 + 大小**：
 * 若文件被外部修改（编辑器保存、其他 App 写入），
 * `lastModified` / `length` 变化 → 自动视为未命中，重新解析。
 *
 * ## 容量控制
 *
 * 最多缓存 [MAX_ENTRIES] 个文档，超出按 **LRU**（最近最少使用）淘汰，
 * 避免连续打开多个大文件导致内存爆掉。
 * 用 `LinkedHashMap(accessOrder = true)` 天然实现 LRU。
 */
object DocumentCache {

    /** 最多缓存的文档数。大文件单个可能几十 MB，不宜过多。 */
    private const val MAX_ENTRIES = 4

    /** accessOrder=true 使其成为 LRU：get 会把条目移到队尾。 */
    private val cache = object : LinkedHashMap<String, DocumentModel>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DocumentModel>?): Boolean =
            size > MAX_ENTRIES
    }

    private val lock = Any()

    /**
     * 构造缓存 key。
     *
     * 包含修改时间与大小：文件被外部改动后 key 变化，自然失效。
     * 文件不存在时退化为仅用路径（调用方会自行报错）。
     */
    fun keyOf(path: String): String {
        val f = File(path)
        val stamp = if (f.exists()) "${f.lastModified()}:${f.length()}" else "absent"
        return "$path|$stamp"
    }

    /** 取缓存（命中会刷新 LRU 顺序）。 */
    fun get(key: String): DocumentModel? = synchronized(lock) { cache[key] }

    /** 存缓存。 */
    fun put(key: String, doc: DocumentModel) {
        synchronized(lock) { cache[key] = doc }
    }

    /** 清空（当前无调用方，保留给"手动刷新"等未来场景）。 */
    fun clear() = synchronized(lock) { cache.clear() }

    /** 当前缓存条目数（调试用）。 */
    fun size(): Int = synchronized(lock) { cache.size }
}