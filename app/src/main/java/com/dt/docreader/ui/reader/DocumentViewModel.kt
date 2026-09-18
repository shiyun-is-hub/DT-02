package com.dt.docreader.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dt.docreader.data.DocumentCache
import com.dt.docreader.data.reader.FileSourceFactory
import com.dt.docreader.domain.OpenDocumentUseCase
import com.dt.docreader.domain.model.DocumentModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val doc: DocumentModel) : UiState
    data class Error(val message: String) : UiState
}

class DocumentViewModel(app: Application) : AndroidViewModel(app) {

    private val useCase = OpenDocumentUseCase()
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 当前加载任务的 Job。
     *
     * **必须显式取消上一个任务**：否则快速切换文档时，
     * 慢的旧任务会在新任务之后完成，把 `_state` 覆盖成**旧文档**，
     * 用户看到的是上一个文件的内容（stale state）。
     */
    private var loadJob: Job? = null

    /**
     * 当前打开文档的路径（书签/阅读位置以此为 key）。
     *
     * 用普通字段而非 StateFlow：它只在加载时写入，
     * UI 读取时机是"文档已加载后"，不需要触发重组。
     */
    var currentPath: String = ""
        private set

    fun load(uri: Uri) {
        val path = uri.toString()
        currentPath = path
        startLoad(path) {
            val source = FileSourceFactory.fromUri(getApplication<Application>().contentResolver, uri)
            useCase.execute(source).getOrThrow()
        }
    }

    /** 直接从文件路径加载（文件管理器 / 最近列表）。 */
    fun loadFile(path: String) {
        currentPath = path
        startLoad(path) {
            // 1) 先查缓存：命中则跳过解析（key 含 mtime+size，文件被改动会自动失效）
            val cacheKey = DocumentCache.keyOf(path)
            DocumentCache.get(cacheKey)?.let { return@startLoad it }

            // 2) 未命中：解析并写入缓存
            val file = java.io.File(path)
            if (!file.exists()) throw java.io.FileNotFoundException("文件不存在：$path")
            if (!file.canRead()) throw java.io.IOException("无读取权限：$path")
            val source = FileSourceFactory.fromFile(file)
            val doc = useCase.execute(source).getOrThrow()
            DocumentCache.put(cacheKey, doc)
            doc
        }
    }

    /**
     * 统一的加载入口：取消旧任务 -> 置 Loading -> 解析 -> 写结果。
     *
     * 取消语义：`loadJob?.cancel()` 后，旧协程在挂起点抛 CancellationException，
     * 不会走到 `_state.value = ...`，因此**不可能覆盖新文档的状态**。
     */
    private fun startLoad(path: String, parse: suspend () -> DocumentModel) {
        loadJob?.cancel()
        _state.value = UiState.Loading
        loadJob = viewModelScope.launch {
            val result = runCatching { parse() }
            // 若已被取消（切换了文档），直接放弃写入，避免 stale state
            if (!isActive) return@launch
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "打开失败") }
            )
        }
    }
}