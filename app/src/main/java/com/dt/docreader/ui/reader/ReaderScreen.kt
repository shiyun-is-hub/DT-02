package com.dt.docreader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: DocumentViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val name = (state as? UiState.Success)?.doc?.meta?.fileName ?: "阅读"
                    Text(name, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UiState.Idle -> CenterText("请返回选择文件")
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> CenterText("打开失败：${s.message}")
                is UiState.Success -> DocumentContent(s.doc)
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun DocumentContent(doc: DocumentModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MetaHeader(doc) }
        item { HorizontalDivider() }
        items(doc.allBlocks) { block -> BlockItem(block) }
        item { Box(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun MetaHeader(doc: DocumentModel) {
    val m = doc.meta
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(m.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val parts = buildList {
            m.language?.let { add(it) }
            m.encoding?.let { add(it) }
            if (m.fileSize > 0) add(formatSize(m.fileSize))
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockItem(block: Block) {
    when (block) {
        is Block.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.titleMedium
            }
            Text(
                block.text,
                style = style,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        is Block.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)

        is Block.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row {
                    Text(
                        if (block.ordered) "${idx + 1}. " else "•  ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(item, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        is Block.CodeBlock -> CodeBlockView(block)

        is Block.TableBlock -> TableView(block.rows)

        is Block.ImageBlock -> Text("[图片] ${block.alt ?: block.uri}")

        is Block.Slide -> Column {
            block.title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            block.body.forEach { Text(it) }
        }
    }
}

/** 代码块：等宽 + 行号 + 复制按钮 + 横向滚动。 */
@Composable
private fun CodeBlockView(block: Block.CodeBlock) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F8FA), MaterialTheme.shapes.medium)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                block.language ?: "代码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { copyToClipboard(context, block.code) },
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val lines = block.code.split("\n")
        val hScroll = rememberScrollState()
        Column(modifier = Modifier.horizontalScroll(hScroll)) {
            lines.forEachIndexed { idx, line ->
                Row {
                    Text(
                        text = "%4d".format(idx + 1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF9AA4B2),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF24292F)
                    )
                }
            }
        }
    }
}

/** 表格：等宽分列 + 横向滚动。 */
@Composable
private fun TableView(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val hScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA), MaterialTheme.shapes.medium)
            .padding(8.dp)
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (rIdx == 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
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