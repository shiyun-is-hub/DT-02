package com.dt.docreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dt.docreader.domain.model.Block
import com.dt.docreader.domain.model.DocumentModel

@Composable
fun ReaderScreen(viewModel: DocumentViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("阅读") }) }
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
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text(text) }
}

@Composable
private fun DocumentContent(doc: DocumentModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = doc.meta.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(doc.allBlocks) { block -> BlockItem(block) }
    }
}

@Composable
private fun BlockItem(block: Block) {
    when (block) {
        is Block.Heading -> Text(
            text = block.text,
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.titleMedium
            }
        )
        is Block.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
        is Block.BulletList -> Column {
            block.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
        is Block.CodeBlock -> Text(
            text = block.code,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFFF2F2F2))
                .padding(8.dp)
        )
        is Block.TableBlock -> Text("[表格] ${block.rows.size} 行")
        is Block.ImageBlock -> Text("[图片] ${block.alt ?: block.uri}")
        is Block.Slide -> Column {
            block.title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            block.body.forEach { Text(it) }
        }
    }
}
