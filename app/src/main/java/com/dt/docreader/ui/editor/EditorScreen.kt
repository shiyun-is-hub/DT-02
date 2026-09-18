package com.dt.docreader.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermError
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermLineNumber
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

/**
 * 全屏纯文本编辑器。
 *
 * 结构：
 * ```
 * ┌─────────────────────────────┐
 * │ 顶栏：文件名 / 保存 / 退出    │
 * ├─────────────────────────────┤
 * │                             │
 * │        文本编辑区            │  ← 滚动（小文件单框 / 大文件分块）
 * │                             │
 * ├─────────────────────────────┤
 * │  符号底栏（横向滚动）         │  ← 始终贴在软键盘上方
 * └─────────────────────────────┘
 * ```
 *
 * ## 大文件性能（P1 优化）
 *
 * 单框 `BasicTextField` 每次按键都做全文本重排，成本 O(N)。
 * 因此当文本超过 [TextChunker.CHUNK_THRESHOLD_BYTES] 时切到**分块模式**：
 * 用 `LazyColumn` 只组合可见块，每块独立 `BasicTextField`，
 * 按键只重排当前块 —— 性能与文件总大小无关。
 *
 * 保存时通过 [onSave] 的 `getContent` 惰性拼接全文，
 * 编辑过程中**零全文拷贝**。
 *
 * ## 符号底栏如何"永远位于软键盘上方"
 *
 * 用 [Modifier.imePadding] 给整个 Scaffold 的底部内容加 IME 高度内边距：
 * 当软键盘弹出时，Compose 会把 IME 高度作为 insets 下发，
 * [imePadding] 据此把符号栏顶到键盘正上方；键盘收起时内边距归零。
 * **不依赖任何 Android View 层的 hack**。
 *
 * 注意：需要在 AndroidManifest 中为该 Activity 设置
 * `android:windowSoftInputMode="adjustResize"`，否则 IME insets 不会下发。
 *
 * @param onDirtyChange 脏标记变化回调（编辑中只传布尔，不传全文）
 * @param onSave 保存；`getContent()` 在保存瞬间才拼接全文
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fileName: String,
    initialText: String,
    dirty: Boolean,
    saving: Boolean,
    message: String?,
    onDirtyChange: (Boolean) -> Unit,
    onSave: (getContent: () -> String) -> Unit,
    onExit: () -> Unit
) {
    // 分块模式判定（只算一次；initialText 在编辑期间不变）
    val chunked = remember(initialText) { TextChunker.shouldChunk(initialText) }

    // ---- 分块模式状态 ----
    val chunks = remember(initialText) {
        if (chunked) TextChunker.chunk(initialText) else listOf(initialText)
    }
    // 每块用 TextFieldValue 保存，才能精确控制光标（插入符号后光标跟随）
    val chunkState = remember(initialText) {
        chunks.map { mutableStateOf(TextFieldValue(it)) }
    }
    // 只记录"被改过的块"，避免每次按键做全量字符串比较（O(块数×块长)）
    val dirtyChunks = remember(initialText) {
        mutableStateMapOf<Int, Unit>()
    }
    // 当前聚焦的块索引（符号栏插入定位用）
    // 用 mutableIntStateOf 避免 Int 装箱（AutoboxingStateCreation）
    var focusedChunk by remember(initialText) { mutableIntStateOf(0) }

    // ---- 单框模式状态 ----
    var field by remember(initialText) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0)))
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    /** 当前全文（仅在保存时调用，O(N) 一次）。 */
    fun currentContent(): String =
        if (chunked) TextChunker.join(chunkState.map { it.value.text }) else field.text

    /** 是否有未保存改动。只查"脏块集合"，O(1)。 */
    fun recomputeDirty() {
        val d = if (chunked) dirtyChunks.isNotEmpty() else field.text != initialText
        onDirtyChange(d)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = TermBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TermSurface,
                    titleContentColor = TermGreenBright
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName + if (dirty) " *" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (chunked) {
                            Text(
                                text = "  分块模式",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TermLineNumber
                            )
                        }
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onExit() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = TermGreenBright, fontSize = 16.sp)
                    }
                },
                actions = {
                    Text(
                        text = if (saving) "保存中…" else "保存",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (saving) TermOnBgVariant else TermGreenBright,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = !saving) { onSave { currentContent() } }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- 提示条 ----
            if (message != null) {
                Text(
                    text = message,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (message.startsWith("!")) TermError else TermGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermGreenDeep)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            // ---- 编辑区 ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TermBg)
            ) {
                if (chunked) {
                    ChunkedEditor(
                        chunkState = chunkState,
                        onChanged = { idx -> dirtyChunks[idx] = Unit; recomputeDirty() },
                        onFocusChange = { idx -> focusedChunk = idx }
                    )
                } else {
                    BasicTextField(
                        value = field,
                        onValueChange = { v ->
                            field = v
                            recomputeDirty()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = EDIT_TEXT_STYLE,
                        cursorBrush = SolidColor(TermGreenBright),
                        keyboardOptions = EDIT_KEYBOARD_OPTIONS
                    )
                }
            }

            HorizontalDivider(color = TermGreenDim)

            // ---- 符号底栏（随 IME 上移） ----
            SymbolBar(
                onInsert = { sym ->
                    if (chunked) {
                        // 分块模式：插入到当前聚焦块的光标处。
                        // 点符号栏会让输入框失焦，但 TextFieldValue 里保存了
                        // 最后一次的光标位置，因此插入点仍然正确。
                        val idx = focusedChunk.coerceIn(0, chunkState.lastIndex)
                        val state = chunkState[idx]
                        val v = state.value
                        val text = v.text
                        val start = v.selection.start.coerceIn(0, text.length)
                        val end = v.selection.end.coerceIn(0, text.length)
                        val newText = text.substring(0, start) + sym + text.substring(end)
                        val cursor = start + sym.length
                        state.value = TextFieldValue(newText, TextRange(cursor))
                        dirtyChunks[idx] = Unit
                        recomputeDirty()
                    } else {
                        val text = field.text
                        val start = field.selection.start.coerceIn(0, text.length)
                        val end = field.selection.end.coerceIn(0, text.length)
                        val newText = text.substring(0, start) + sym + text.substring(end)
                        val cursor = start + sym.length
                        field = TextFieldValue(newText, TextRange(cursor))
                        recomputeDirty()
                    }
                }
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 分块编辑区。
 *
 * 用 `LazyColumn` 只组合可见块；每个块一个独立 `BasicTextField`，
 * 按键只重排当前块，与文件总大小无关。
 */
@Composable
private fun ChunkedEditor(
    chunkState: List<androidx.compose.runtime.MutableState<TextFieldValue>>,
    onChanged: (Int) -> Unit,
    onFocusChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = chunkState,
            key = { index, _ -> index }
        ) { index, state ->
            BasicTextField(
                value = state.value,
                onValueChange = {
                    state.value = it
                    onChanged(index)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusChange(index) }
                    .padding(horizontal = 12.dp),
                textStyle = EDIT_TEXT_STYLE,
                cursorBrush = SolidColor(TermGreenBright),
                keyboardOptions = EDIT_KEYBOARD_OPTIONS
            )
        }
    }
}

private val EDIT_TEXT_STYLE = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    color = TermCodeText,
    lineHeight = 19.sp
)

private val EDIT_KEYBOARD_OPTIONS = KeyboardOptions(
    keyboardType = KeyboardType.Text,
    autoCorrectEnabled = false
)

/**
 * 符号底栏。
 *
 * 横向可滚动，容纳较多符号；每个按钮尽量大，便于手指点按。
 */
@Composable
private fun SymbolBar(onInsert: (String) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SYMBOLS.forEach { s ->
                SymbolKey(s) { onInsert(s) }
            }
        }
    }
}

@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .width(if (label.length > 1) 56.dp else 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = if (label.length > 1) 11.sp else 14.sp,
            color = TermGreenBright
        )
    }
}

/**
 * 符号表。
 *
 * 多字符项（如 `Tab`、`->`、`=>`）会插入对应文本；
 * 需要缩进的场景用 `Tab` 插入 4 个空格（比制表符更可控）。
 */
private val SYMBOLS: List<String> = listOf(
    "Tab", "  ", "\t",
    "{", "}", "[", "]", "(", ")",
    "<", ">", "\"", "'", "`",
    "\\", "/", "|", "&", "~", "^",
    "#", "-", "_", "=", "+", "*", "%",
    ":", ";", ",", ".", "!", "?", "@", "$",
    "->", "=>", "::", "//", "/*", "*/",
    "&&", "||", "!=", "==", "<=", ">=",
    "\n", "\n\n"
)