package com.dt.docreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.R
import com.dt.docreader.data.BookmarkStore
import com.dt.docreader.data.ReaderSettings
import com.dt.docreader.domain.SearchDocumentUseCase
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermError
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

/** 侧边栏面板类型。 */
enum class SidebarPanel(val label: String) {
    FONT("字号"),
    SEARCH("搜索"),
    BOOKMARK("书签"),
    EDIT("编辑")
}

/**
 * 阅读页侧边栏内容。
 *
 * 顶部是面板切换标签，下面是各面板。
 * 侧边栏是**独立于阅读页**的覆盖层，其内部状态（如搜索词）不影响下层。
 */
@Composable
fun SidebarContent(
    panel: SidebarPanel,
    onPanelChange: (SidebarPanel) -> Unit,
    settings: ReaderSettings.Snapshot,
    onSettingsChange: (ReaderSettings.Snapshot) -> Unit,
    onResetSettings: () -> Unit,
    // 搜索
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SearchDocumentUseCase.Hit>,
    onSearchResultClick: (SearchDocumentUseCase.Hit) -> Unit,
    searching: Boolean,
    // 书签
    bookmarks: List<BookmarkStore.Bookmark>,
    onAddBookmark: () -> Unit,
    onBookmarkClick: (BookmarkStore.Bookmark) -> Unit,
    onBookmarkDelete: (BookmarkStore.Bookmark) -> Unit,
    // 编辑
    editable: Boolean,
    onEdit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TermSurface)
            .border(1.dp, TermGreenDim)
    ) {
        // ---- 标题栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "panel@local:~$",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermGreenBright,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = TermGreen, fontSize = 15.sp)
            }
        }
        HorizontalDivider(color = TermGreenDim)

        // ---- 面板切换 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SidebarPanel.entries.forEach { p ->
                val selected = p == panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) TermGreenDeep else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) TermGreen else TermGreenDim,
                            MaterialTheme.shapes.small
                        )
                        .clickable { onPanelChange(p) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        p.label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) TermGreenBright else TermOnBgVariant
                    )
                }
            }
        }
        HorizontalDivider(color = TermGreenDim)

        // ---- 面板内容 ----
        Box(Modifier.fillMaxSize()) {
            when (panel) {
                SidebarPanel.FONT -> FontPanel(settings, onSettingsChange, onResetSettings)
                SidebarPanel.SEARCH -> SearchPanel(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    results = searchResults,
                    onClick = onSearchResultClick,
                    searching = searching
                )
                SidebarPanel.BOOKMARK -> BookmarkPanel(
                    bookmarks = bookmarks,
                    onAdd = onAddBookmark,
                    onClick = onBookmarkClick,
                    onDelete = onBookmarkDelete
                )
                SidebarPanel.EDIT -> EditPanel(editable, onEdit)
            }
        }
    }
}

// ---------------- 字号面板 ----------------

@Composable
private fun FontPanel(
    settings: ReaderSettings.Snapshot,
    onChange: (ReaderSettings.Snapshot) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SliderRow(
            label = "正文字号",
            valueText = "%.1f sp".format(settings.bodyFontSize),
            value = settings.bodyFontSize,
            range = ReaderSettings.BODY_MIN..ReaderSettings.BODY_MAX,
            steps = 15,
            onValueChange = { onChange(settings.copy(bodyFontSize = it)) }
        )
        SliderRow(
            label = "代码字号",
            valueText = "%.1f sp".format(settings.codeFontSize),
            value = settings.codeFontSize,
            range = ReaderSettings.CODE_MIN..ReaderSettings.CODE_MAX,
            steps = 12,
            onValueChange = { onChange(settings.copy(codeFontSize = it)) }
        )
        SliderRow(
            label = "行距",
            valueText = "%.2f x".format(settings.lineHeightScale),
            value = settings.lineHeightScale,
            range = ReaderSettings.LINE_MIN..ReaderSettings.LINE_MAX,
            steps = 9,
            onValueChange = { onChange(settings.copy(lineHeightScale = it)) }
        )

        // 预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(TermGreenDeep)
                .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
                .padding(12.dp)
        ) {
            Text(
                "预览：谛听文档 DocReader 0123",
                fontFamily = FontFamily.Monospace,
                fontSize = settings.bodyFontSize.sp,
                lineHeight = (settings.bodyFontSize * settings.lineHeightScale).sp,
                color = TermCodeText
            )
        }

        ActionButton("恢复默认", TermGreen, onReset)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermGreenBright,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermGreen
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = TermGreenBright,
                activeTrackColor = TermGreen,
                inactiveTrackColor = TermGreenDim
            )
        )
    }
}

// ---------------- 搜索面板 ----------------

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchDocumentUseCase.Hit>,
    onClick: (SearchDocumentUseCase.Hit) -> Unit,
    searching: Boolean
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            placeholder = {
                Text("搜索文档内容…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TermCodeText
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TermGreen,
                unfocusedBorderColor = TermGreenDim,
                cursorColor = TermGreenBright,
                focusedTextColor = TermCodeText,
                unfocusedTextColor = TermCodeText
            )
        )

        when {
            query.isEmpty() -> Hint("输入关键字开始搜索")
            searching -> Hint("搜索中…")
            results.isEmpty() -> Hint("无匹配结果")
            else -> {
                Text(
                    "── ${results.size} 处匹配 ──",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TermGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results.size) { i ->
                        val hit = results[i]
                        SearchResultRow(hit, query) { onClick(hit) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    hit: SearchDocumentUseCase.Hit,
    query: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = buildHighlighted(hit.lineText, hit.matchStartInLine, hit.matchLength),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            color = TermCodeText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "偏移 ${hit.offset}" + if (hit.blockIndex >= 0) " · 可跳转" else " · 无法定位",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            color = if (hit.blockIndex >= 0) TermGreen else TermOnBgVariant
        )
    }
    HorizontalDivider(color = TermGreenDim.copy(alpha = 0.4f))
}

/** 构造带高亮的文本。 */
private fun buildHighlighted(
    text: String,
    start: Int,
    length: Int
): AnnotatedString = buildAnnotatedString {
    val s = start.coerceIn(0, text.length)
    val e = (start + length).coerceIn(s, text.length)
    append(text.substring(0, s))
    withStyle(
        SpanStyle(
            color = Color.Black,
            background = TermGreenBright,
            fontWeight = FontWeight.Bold
        )
    ) { append(text.substring(s, e)) }
    append(text.substring(e))
}

// ---------------- 书签面板 ----------------

@Composable
private fun BookmarkPanel(
    bookmarks: List<BookmarkStore.Bookmark>,
    onAdd: () -> Unit,
    onClick: (BookmarkStore.Bookmark) -> Unit,
    onDelete: (BookmarkStore.Bookmark) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(12.dp)) {
            ActionButton("＋ 添加当前位置为书签", TermGreenBright, onAdd)
        }
        HorizontalDivider(color = TermGreenDim)

        if (bookmarks.isEmpty()) {
            Hint("暂无书签")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(bookmarks.size) { i ->
                    val b = bookmarks[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(b) }
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.preview.ifBlank { "（无预览）" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = TermCodeText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "block #${b.blockIndex}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                color = TermGreen
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onDelete(b) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = TermError, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = TermGreenDim.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ---------------- 编辑面板 ----------------

@Composable
private fun EditPanel(editable: Boolean, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (editable) {
            Text(
                "以纯文本方式编辑当前文档。",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = TermCodeText
            )
            Text(
                "# 保存前会自动备份为 <文件名>.bak",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = TermGreen
            )
            Spacer(Modifier.height(4.dp))
            ActionButton("✎ 进入编辑模式", TermGreenBright, onEdit)
        } else {
            Text(
                "当前格式不支持编辑。",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TermError
            )
            Text(
                "# 仅文本类文档（txt / md / 代码 / json / csv 等）可编辑；\n" +
                    "# docx / pptx 属于容器格式，需要重新打包，暂不支持。",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = TermOnBgVariant
            )
        }
    }
}

// ---------------- 复用组件 ----------------

@Composable
private fun Hint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TermOnBgVariant
        )
    }
}

@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(TermGreenDeep)
            .border(1.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}