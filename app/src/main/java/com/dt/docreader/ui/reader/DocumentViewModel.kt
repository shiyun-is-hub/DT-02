package com.dt.docreader.ui.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    fun load(uri: Uri) {
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
        _state.value = UiState.Loading
        viewModelScope.launch {
            val result = runCatching {
                val file = java.io.File(path)
                if (!file.exists()) throw java.io.FileNotFoundException("文件不存在：$path")
                if (!file.canRead()) throw java.io.IOException("无读取权限：$path")
                val source = FileSourceFactory.fromFile(file)
                useCase.execute(source).getOrThrow()
            }
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "打开失败") }
            )
        }
    }
}