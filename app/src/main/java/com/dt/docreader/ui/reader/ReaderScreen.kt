package com.dt.docreader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dt.docreader.R
import com.dt.docreader.data.BookmarkStore
import com.dt.docreader.data.ReaderSettings
import com.dt.docreader.domain.SearchDocumentUseCase
import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermLineNumber
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

/** 单行代码最多显示的行数（超长行软换行后的上限）。 */
private const val CODE_MAX_LINES = 3

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    viewModel: DocumentViewModel,
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // ---- 阅读设置（字号 / 行距） ----
    var settings by remember { mutableStateOf(ReaderSettings.load(context)) }

    // ---- 侧边栏 ----
    var sidebarOpen by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(SidebarPanel.FONT) }

    // ---- 搜索 ----
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchDocumentUseCase.Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // ---- 书签 ----
    val docPath = (state as? UiState.Success)?.doc?.meta?.let { viewModel.currentPath } ?: ""
    var bookmarks by remember { mutableStateOf<List<BookmarkStore.Bookmark>>(emptyList()) }

    val listState = rememberLazyListState()

    // 文档变化时加载书签
    LaunchedEffect(docPath) {
        if (docPath.isNotEmpty()) bookmarks = BookmarkStore.list(context, docPath)
    }

    // 搜索：输入防抖后执行
    LaunchedEffect(searchQuery, state) {
        val doc = (state as? UiState.Success)?.doc
        if (doc == null || searchQuery.isEmpty()) {
            searchResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(180) // 防抖：避免每个字符都全量扫描
        searchResults = withContext(Dispatchers.Default) {
            SearchDocumentUseCase.search(doc, searchQuery)
        }
        searching = false
    }

    // 阅读位置自动保存（滚动停止后）
    LaunchedEffect(listState, docPath) {
        if (docPath.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(600)
            .collect { idx ->
                // 保存的是"可见项序号"，恢复时按 item 序号定位
                BookmarkStore.savePosition(context, docPath, idx)
            }
    }

    val doc = (state as? UiState.Success)?.doc

    ReaderScaffoldWithSidebar(
        open = sidebarOpen,
        onOpenChange = { sidebarOpen = it },
        sidebar = {
            SidebarContent(
                panel = panel,
                onPanelChange = { panel = it },
                settings = settings,
                onSettingsChange = {
                    settings = it
                    ReaderSettings.save(context, it)
                },
                onResetSettings = { settings = ReaderSettings.reset(context) },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchResults = searchResults,
                onSearchResultClick = { hit ->
                    // 跳转：把 blockIndex 映射为渲染 item 序号
                    val target = doc?.let { RenderPlan.itemIndexOfBlock(it, hit.blockIndex) }
                    if (target != null && target >= 0) {
                        scope.launch { listState.animateScrollToItem(target + 1) } // +1 跳过 meta 头
                        sidebarOpen = false
                    } else {
                        Toast.makeText(context, "无法定位该结果", Toast.LENGTH_SHORT).show()
                    }
                },
                searching = searching,
                bookmarks = bookmarks,
                onAddBookmark = {
                    val d = doc
                    if (d != null && docPath.isNotEmpty()) {
                        val bi = RenderPlan.blockIndexOfItem(d, listState.firstVisibleItemIndex - 1)
                        val preview = bi?.let { RenderPlan.previewOfBlock(d, it) } ?: ""
                        if (bi != null) {
                            bookmarks = BookmarkStore.add(context, docPath, bi, preview)
                            Toast.makeText(context, "已添加书签", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onBookmarkClick = { b ->
                    val target = doc?.let { RenderPlan.itemIndexOfBlock(it, b.blockIndex) }
                    if (target != null && target >= 0) {
                        scope.launch { listState.animateScrollToItem(target + 1) }
                        sidebarOpen = false
                    }
                },
                onBookmarkDelete = { b ->
                    if (docPath.isNotEmpty()) {
                        bookmarks = BookmarkStore.remove(context, docPath, b.blockIndex)
                    }
                },
                editable = doc?.isEditable == true,
                onEdit = {
                    val d = doc
                    if (d?.rawText != null && docPath.isNotEmpty()) {
                        onEdit(docPath, d.rawText)
                    }
                },
                onClose = { sidebarOpen = false }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = TermBg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TermSurface,
                        titleContentColor = TermGreenBright,
                        navigationIconContentColor = TermGreenBright
                    ),
                    title = {
                        val name = doc?.meta?.fileName ?: "reader"
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left),
                                contentDescription = "返回",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 侧边栏入口：阅读页右侧的竖线把手（见 ReaderScaffoldWithSidebar）
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (val s = state) {
                    is UiState.Idle -> CenterText("> 请返回选择文件")
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TermGreenBright)
                            Spacer(Modifier.height(12.dp))
                            Text("> 解析中…", color = TermGreen, fontFamily = FontFamily.Monospace)
                        }
                    }
                    is UiState.Error -> CenterText("> 错误: ${s.message}")
                    is UiState.Success -> DocumentContent(
                        doc = s.doc,
                        settings = settings,
                        listState = listState
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            text,
            modifier = Modifier.padding(24.dp),
            color = TermOnBgVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 文档渲染。
 *
 * 性能关键：
 * - 把 DocumentModel 一次性展开为**扁平 RenderItem 列表**（代码块逐行、表格逐行）。
 * - 全部交给 LazyColumn，只有可见项会被组合/测量，滚动时复用节点。
 * - 展开结果用 remember(doc) 缓存，重组时不重算。
 * - key 使用**预先分配的 Int id**，避免每帧拼接字符串。
 */
@Composable
private fun DocumentContent(
    doc: DocumentModel,
    settings: ReaderSettings.Snapshot,
    listState: LazyListState
) {
    // 字号变化会影响行高与换行，但**不影响 item 结构**（RenderPlan 只按内容拆分），
    // 因此这里仍以 doc 为缓存键，避免调字号时重算整个渲染计划。
    val items = remember(doc) { RenderPlan.build(doc) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item(key = "__meta__") {
            MetaHeader(doc, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(color = TermGreenDim, modifier = Modifier.padding(horizontal = 12.dp))
        }

        items(
            items = items,
            key = { it.id },        // Int key：零分配、零 hash 成本
            contentType = { it.contentType }  // 按类型复用节点，避免不同类型互相顶替
        ) { item ->
            RenderItemView(item, settings)
        }

        item(key = "__tail__") { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun RenderItemView(item: RenderItem, settings: ReaderSettings.Snapshot) {
    when (item) {
        is RenderItem.CodeHeader -> CodeHeaderRow(item)
        is RenderItem.CodeLine -> CodeLineRow(item, settings)
        is RenderItem.CodeFooter -> CodeFooterView(item)

        is RenderItem.TableRow -> TableRowView(item, settings)
        is RenderItem.TableFooter -> Spacer(Modifier.height(12.dp))

        is RenderItem.ListItemRow -> ListItemView(item, settings)

        is RenderItem.SimpleBlock -> SimpleBlockView(item.block, settings)

        is RenderItem.ParagraphChunk -> ParagraphChunkView(item, settings)
    }
}

// ---------------- 元信息 ----------------

@Composable
private fun MetaHeader(doc: DocumentModel, modifier: Modifier = Modifier) {
    val m = doc.meta
    Column(
        modifier = modifier.padding(top = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$ ${m.fileName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = TermGreenBright
        )
        val parts = buildList {
            m.language?.let { add(it) }
            m.encoding?.let { add(it) }
            if (m.fileSize > 0) add(formatSize(m.fileSize))
        }
        if (parts.isNotEmpty()) {
            Text(
                text = "# " + parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TermOnBgVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------------- 代码块（逐行渲染） ----------------

@Composable
private fun CodeHeaderRow(item: RenderItem.CodeHeader) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "── ${item.language ?: "code"} ── ${item.lineCount} 行",
            style = MaterialTheme.typography.labelMedium,
            color = TermGreen,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                copyToClipboard(context, item.rawCode)
                Toast.makeText(context, "已复制 ${item.lineCount} 行", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = "复制代码",
                tint = TermGreenBright,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 单行代码。
 *
 * 性能要点（关键）：
 * - 把「行号 + 代码」合并进**一个 Text**（用 AnnotatedString 让行号着色），
 *   使每行只剩 1 个绘制/布局节点，而不是初版的 Row + 2×Text（3 个节点）。
 * - 行号用固定宽度占位（右对齐 + 单空格分隔），无需额外宽度修饰符。
 * - **maxLines = 3**：超长行（压缩 JS/JSON）最多只量 3 行高度，
 *   避免单个 item 触发数十次换行计算。配合 RenderPlan 的字符截断双保险。
 * - **includeFontPadding = false**：去掉字体内边距，垂直测量更精确、行高更稳定，
 *   减少因行高抖动导致的重排。
 * - 字面量 style 全部预先构建（在 remember 中），避免每行重复构造 TextStyle。
 */
@Composable
private fun CodeLineRow(item: RenderItem.CodeLine, settings: ReaderSettings.Snapshot) {
    val style = remember(settings.codeFontSize, settings.lineHeightScale) {
        val size = settings.codeFontSize
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = size.sp,
            color = TermCodeText,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            // 行高随字号等比缩放，保证不同字号下视觉比例一致
            lineHeight = (size * 1.36f).sp
        )
    }
    val lineNumberColor = TermLineNumber

    val annotated = remember(item.numberText, item.text) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = lineNumberColor)) {
                append(item.numberText.padStart(4))
                append("  ")
            }
            append(item.text)
        }
    }

    Text(
        text = annotated,
        style = style,
        softWrap = true,
        maxLines = CODE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep)
            .padding(horizontal = 12.dp, vertical = 1.dp)
    )
}

// ---------------- 代码块尾部（截断提示） ----------------

@Composable
private fun CodeFooterView(item: RenderItem.CodeFooter) {
    if (item.truncated) {
        Text(
            text = "── 行数过多，仅显示前 ${RenderPlan.CODE_LINE_LIMIT} 行 ──",
            style = MaterialTheme.typography.labelSmall,
            color = TermGreenDim,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    } else {
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------- 表格（逐行渲染） ----------------

@Composable
private fun TableRowView(item: RenderItem.TableRow, settings: ReaderSettings.Snapshot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep)
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        item.cells.forEach { cell ->
            Text(
                text = cell,
                fontFamily = FontFamily.Monospace,
                fontSize = (settings.bodyFontSize - 3f).coerceAtLeast(9f).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (item.isHeader) FontWeight.Bold else FontWeight.Normal,
                color = if (item.isHeader) TermGreenBright else TermCodeText,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
        }
    }
    if (item.isHeader) {
        HorizontalDivider(color = TermGreenDim)
    }
}

// ---------------- 列表项 ----------------

@Composable
private fun ListItemView(item: RenderItem.ListItemRow, settings: ReaderSettings.Snapshot) {
    val size = settings.bodyFontSize.sp
    val lineH = (settings.bodyFontSize * settings.lineHeightScale).sp
    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Text(
            text = item.marker + " ",
            fontSize = size,
            lineHeight = lineH,
            color = TermGreenBright,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = item.text,
            fontSize = size,
            lineHeight = lineH,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ---------------- 超长段落片段 ----------------

/**
 * 超长段落的一个片段。
 *
 * 与 [SimpleBlockView] 里的 Paragraph 分支保持**一致的视觉**：
 * 同样的字号/颜色/左右内边距，只是把上下间距分配到首尾片段，
 * 使拆分后的段落看起来仍是一个连续段落。
 */
@Composable
private fun ParagraphChunkView(item: RenderItem.ParagraphChunk, settings: ReaderSettings.Snapshot) {
    Text(
        text = item.text,
        fontSize = settings.bodyFontSize.sp,
        lineHeight = (settings.bodyFontSize * settings.lineHeightScale).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(
                top = if (item.isFirst) 3.dp else 0.dp,
                bottom = if (item.isLast) 3.dp else 0.dp
            )
    )
}

// ---------------- 其他块 ----------------

@Composable
private fun SimpleBlockView(block: Block, settings: ReaderSettings.Snapshot) {
    val pad = Modifier.padding(horizontal = 12.dp)
    val bodySize = settings.bodyFontSize.sp
    val bodyLine = (settings.bodyFontSize * settings.lineHeightScale).sp

    when (block) {
        is Block.Heading -> {
            // 标题字号按正文比例缩放，保证不同字号下层级关系一致
            val size = when (block.level) {
                1 -> (settings.bodyFontSize + 4f).sp
                2 -> (settings.bodyFontSize + 1.5f).sp
                else -> settings.bodyFontSize.sp
            }
            Text(
                text = "#".repeat(block.level.coerceAtMost(3)) + " " + block.text,
                fontSize = size,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TermGreenBright,
                modifier = pad.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        is Block.Paragraph -> Text(
            block.text,
            fontSize = bodySize,
            lineHeight = bodyLine,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = pad.padding(vertical = 3.dp)
        )

        is Block.ImageBlock -> Text(
            "[image] ${block.alt ?: block.uri}",
            color = TermOnBgVariant,
            fontFamily = FontFamily.Monospace,
            modifier = pad
        )

        is Block.Slide -> Column(modifier = pad) {
            block.title?.let {
                Text(
                    it,
                    fontSize = (settings.bodyFontSize + 4f).sp,
                    color = TermGreenBright
                )
            }
            block.body.forEach {
                Text(
                    it,
                    fontSize = bodySize,
                    lineHeight = bodyLine,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // 由 RenderPlan 拆分的类型不会走到这里
        is Block.CodeBlock, is Block.TableBlock, is Block.BulletList -> Unit
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("谛听文档", text))
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
}