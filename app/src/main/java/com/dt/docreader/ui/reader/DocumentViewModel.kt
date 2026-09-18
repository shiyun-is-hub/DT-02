package com.dt.docreader.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dt.docreader.data.DocumentCache
import com.dt.docreader.data.reader.FileSourceFactory
import com.dt.docreader.domain.OpenDocumentUseCase
import com.dt.docreader.domain.model.DocumentModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * 当前打开文档的路径（书签/阅读位置以此为 key）。
     *
     * 用普通字段而非 StateFlow：它只在加载时写入，
     * UI 读取时机是"文档已加载后"，不需要触发重组。
     */
    var currentPath: String = ""
        private set

    fun load(uri: Uri) {
        currentPath = uri.toString()
        _state.value = UiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                val source = FileSourceFactory.fromUri(getApplication<Application>().contentResolver, uri)
                useCase.execute(source).getOrThrow()
            }
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "打开失败") }
            )
        }
    }

    /** 直接从文件路径加载（文件管理器 / 最近列表）。 */
    fun loadFile(path: String) {
        currentPath = path
        _state.value = UiState.Loading
        viewModelScope.launch {
            // 1) 先查缓存：命中则跳过解析（key 含 mtime+size，文件被改动会自动失效）
            val cacheKey = DocumentCache.keyOf(path)
            DocumentCache.get(cacheKey)?.let { cached ->
                _state.value = UiState.Success(cached)
                return@launch
            }

            // 2) 未命中：解析并写入缓存
            val result = runCatching {
                val file = java.io.File(path)
                if (!file.exists()) throw java.io.FileNotFoundException("文件不存在：$path")
                if (!file.canRead()) throw java.io.IOException("无读取权限：$path")
                val source = FileSourceFactory.fromFile(file)
                useCase.execute(source).getOrThrow()
            }
            result.onSuccess { DocumentCache.put(cacheKey, it) }
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "打开失败") }
            )
        }
    }
}