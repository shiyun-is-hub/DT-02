package com.dt.docreader.ui.recent

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.R
import com.dt.docreader.data.FileBrowser
import com.dt.docreader.data.RecentStore
import com.dt.docreader.ui.components.TermEmptyState
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页：最近打开的文件列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(
    items: List<RecentStore.RecentItem>,
    onOpen: (RecentStore.RecentItem) -> Unit,
    onRemove: (RecentStore.RecentItem) -> Unit,
    onGoBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TermBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TermSurface,
                    titleContentColor = TermGreenBright
                ),
                title = {
                    Text(
                        "recent@local:~$",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                TermEmptyState(
                    title = "# 暂无最近记录",
                    lines = listOf(
                        "点击底部 [+] 浏览文件",
                        "打开过的文档会出现在这里"
                    ),
                    actionLabel = "▸ 立即浏览文件",
                    onAction = onGoBrowser
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "__header__") {
                    Text(
                        "# 最近打开（${items.size}）",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TermOnBgVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                items(items = items, key = { it.path }) { item ->
                    RecentRow(
                        item = item,
                        onOpen = { onOpen(item) },
                        onRemove = { onRemove(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    item: RecentStore.RecentItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val timeText = remember(item.timestamp) { formatTime(item.timestamp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (pressed) TermGreenDim else TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .clickable {
                pressed = true
                onOpen()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "▸",
            fontFamily = FontFamily.Monospace,
            color = TermGreenBright,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TermGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.path,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TermOnBgVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$timeText · ${FileBrowser.formatSize(item.size)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TermCodeText
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = "移除记录",
                tint = TermOnBgVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatTime(ts: Long): String {
    if (ts <= 0L) return "--"
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    }
}