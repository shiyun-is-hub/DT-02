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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** 代码字号 */
private val CODE_FONT_SIZE = 12.5.sp

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
                        overflow = TextOverflow.Ellipsis,
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

/**
 * 文档渲染。
 *
 * 性能关键：
 * - 把 DocumentModel 一次性展开为**扁平 RenderItem 列表**（代码块逐行、表格逐行）。
 * - 全部交给 LazyColumn，只有可见项会被组合/测量，滚动时复用节点。
 * - 展开结果用 remember(doc) 缓存，重组时不重算。
 * - 每个 item 有稳定 key，最大化复用。
 */
@Composable
private fun DocumentContent(doc: DocumentModel) {
    val items = remember(doc) { RenderPlan.build(doc) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "__meta__") {
            Spacer(Modifier.height(10.dp))
            MetaHeader(doc)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = TermGreenDim)
            Spacer(Modifier.height(6.dp))
        }

        items(items = items, key = { it.stableKey() }) { item ->
            RenderItemView(item)
        }

        item(key = "__tail__") { Spacer(Modifier.height(32.dp)) }
    }
}

/** 为每个渲染项生成稳定 key（决定复用效率）。 */
private fun RenderItem.stableKey(): String = when (this) {
    is RenderItem.CodeHeader -> "ch_$blockIndex"
    is RenderItem.CodeLine -> "cl_${blockIndex}_$lineNo"
    is RenderItem.CodeFooter -> "cf_$blockIndex"
    is RenderItem.TableRow -> "tr_${blockIndex}_$rowIndex"
    is RenderItem.TableFooter -> "tf_$blockIndex"
    is RenderItem.ListItemRow -> "li_${blockIndex}_$index"
    is RenderItem.SimpleBlock -> "sb_$blockIndex"
}

@Composable
private fun RenderItemView(item: RenderItem) {
    when (item) {
        is RenderItem.CodeHeader -> CodeHeaderRow(item)
        is RenderItem.CodeLine -> CodeLineRow(item)
        is RenderItem.CodeFooter -> Spacer(Modifier.height(12.dp))

        is RenderItem.TableRow -> TableRowView(item)
        is RenderItem.TableFooter -> Spacer(Modifier.height(12.dp))

        is RenderItem.ListItemRow -> ListItemView(item)

        is RenderItem.SimpleBlock -> SimpleBlockView(item.block)
    }
}

// ---------------- 元信息 ----------------

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

// ---------------- 代码块（逐行渲染） ----------------

@Composable
private fun CodeHeaderRow(item: RenderItem.CodeHeader) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
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
                Icons.Filled.ContentCopy,
                contentDescription = "复制代码",
                tint = TermGreenBright
            )
        }
    }
}

/**
 * 单行代码。
 *
 * 性能：每行是一个独立 LazyColumn item，不在组合期做任何分配以外的工作。
 * 采用**软换行**（softWrap = true），避免横向滚动导致的整行超长测量。
 */
@Composable
private fun CodeLineRow(item: RenderItem.CodeLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep)
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = item.numberText,
            fontFamily = FontFamily.Monospace,
            fontSize = CODE_FONT_SIZE,
            color = TermLineNumber,
            modifier = Modifier
                .width(40.dp)
                .padding(end = 8.dp),
            maxLines = 1
        )
        Text(
            text = item.text,
            fontFamily = FontFamily.Monospace,
            fontSize = CODE_FONT_SIZE,
            color = TermCodeText,
            softWrap = true
        )
    }
}

// ---------------- 表格（逐行渲染） ----------------

@Composable
private fun TableRowView(item: RenderItem.TableRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep)
            .padding(vertical = 3.dp)
    ) {
        item.cells.forEach { cell ->
            Text(
                text = cell,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
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
private fun ListItemView(item: RenderItem.ListItemRow) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = item.marker + " ",
            style = MaterialTheme.typography.bodyLarge,
            color = TermGreenBright,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ---------------- 其他块 ----------------

@Composable
private fun SimpleBlockView(block: Block) {
    when (block) {
        is Block.Heading -> {
            val size = when (block.level) {
                1 -> 20.sp
                2 -> 17.sp
                else -> 15.sp
            }
            Text(
                text = "#".repeat(block.level.coerceAtMost(3)) + " " + block.text,
                fontSize = size,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TermGreenBright,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
            )
        }

        is Block.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 3.dp)
        )

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

        // 由 RenderPlan 拆分的类型不会走到这里
        is Block.CodeBlock, is Block.TableBlock, is Block.BulletList -> Unit
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