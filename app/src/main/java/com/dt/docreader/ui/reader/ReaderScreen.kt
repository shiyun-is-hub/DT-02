package com.dt.docreader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermLineNumber
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

/** 代码块首屏最多渲染行数（性能保护）。超过部分由「展开」按钮渐进加载。 */
private const val CODE_PAGE_SIZE = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: DocumentViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                    val name = (state as? UiState.Success)?.doc?.meta?.fileName ?: "reader"
                    Text(
                        text = name,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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
                is UiState.Success -> DocumentContent(s.doc)
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

@Composable
private fun DocumentContent(doc: DocumentModel) {
    // 用稳定的 key 让 LazyColumn 更好复用，提升长文档滚动性能
    val blocks = doc.allBlocks
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "__meta__") {
            Spacer(Modifier.height(8.dp))
            MetaHeader(doc)
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = TermGreenDim)
        }
        itemsIndexed(
            items = blocks,
            key = { index, block -> "b_${index}_${block.javaClass.simpleName}" }
        ) { _, block -> BlockItem(block) }
        item(key = "__tail__") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MetaHeader(doc: DocumentModel) {
    val m = doc.meta
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

@Composable
private fun BlockItem(block: Block) {
    when (block) {
        is Block.Heading -> {
            val size = when (block.level) {
                1 -> 20.sp
                2 -> 17.sp
                else -> 15.sp
            }
            Column {
                Text(
                    text = "#".repeat(block.level.coerceAtMost(3)) + " " + block.text,
                    fontSize = size,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TermGreenBright,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        is Block.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        is Block.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row {
                    Text(
                        text = if (block.ordered) "${idx + 1}. " else "▸ ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TermGreenBright,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        is Block.CodeBlock -> CodeBlockView(block)

        is Block.TableBlock -> TableView(block.rows)

        is Block.ImageBlock -> Text(
            "[image] ${block.alt ?: block.uri}",
            color = TermOnBgVariant,
            fontFamily = FontFamily.Monospace
        )

        is Block.Slide -> Column {
            block.title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, color = TermGreenBright)
            }
            block.body.forEach {
                Text(it, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

/**
 * 代码块：终端风渲染。
 *
 * 性能要点：
 * - 用 remember 缓存 split 结果与行号字符串，避免重组时重复计算。
 * - 超长代码默认只渲染 CODE_PAGE_SIZE 行，避免一次性生成上万 Composable。
 * - 行号预先格式化，不在每行绘制时调用 String.format。
 */
@Composable
private fun CodeBlockView(block: Block.CodeBlock) {
    val context = LocalContext.current

    val lines = remember(block.code) { block.code.split('\n') }
    val lineNumbers = remember(lines) {
        Array(lines.size) { "%4d".format(it + 1) }
    }

    var visibleCount by rememberSaveable(block.code.hashCode()) {
        mutableIntStateOf(minOf(CODE_PAGE_SIZE, lines.size))
    }
    val shown = minOf(visibleCount, lines.size)
    val hScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep, MaterialTheme.shapes.small)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        // 头：语言标签 + 复制
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "── ${block.language ?: "code"} ── ${lines.size} 行",
                style = MaterialTheme.typography.labelMedium,
                color = TermGreen,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                copyToClipboard(context, block.code)
                Toast.makeText(context, "已复制 ${lines.size} 行", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制代码",
                    tint = TermGreenBright
                )
            }
        }

        Column(modifier = Modifier.horizontalScroll(hScroll)) {
            for (i in 0 until shown) {
                Row {
                    Text(
                        text = lineNumbers[i],
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TermLineNumber,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = lines[i].ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TermCodeText
                    )
                }
            }
        }

        // 超长文件：渐进展开
        if (shown < lines.size) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                visibleCount = minOf(visibleCount + CODE_PAGE_SIZE * 2, lines.size)
            }) {
                Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = TermGreenBright)
                Spacer(Modifier.width(6.dp))
                Text(
                    "展开更多（剩余 ${lines.size - shown} 行）",
                    color = TermGreenBright,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** 表格：终端风网格。 */
@Composable
private fun TableView(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val hScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep, MaterialTheme.shapes.small)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .padding(6.dp)
            .horizontalScroll(hScroll)
    ) {
        rows.forEachIndexed { rIdx, row ->
            Row {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (rIdx == 0) TermGreenBright else TermCodeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (rIdx == 0) HorizontalDivider(color = TermGreen, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("DocReader", text))
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
}