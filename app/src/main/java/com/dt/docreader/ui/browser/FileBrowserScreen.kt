package com.dt.docreader.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.R
import com.dt.docreader.data.FileBrowser
import com.dt.docreader.ui.components.TermEmptyState
import com.dt.docreader.ui.components.TermNoticeCard
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器：直接浏览存储目录。
 *
 * @param currentDir 当前目录
 * @param hasPermission 是否已拿到所有文件访问权限
 * @param onNavigate 进入某个目录
 * @param onOpenFile 打开某个文件
 * @param onRequestPermission 请求权限
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    currentDir: File,
    hasPermission: Boolean,
    onNavigate: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showHidden by remember { mutableStateOf(false) }
    var textOnly by remember { mutableStateOf(true) }

    val entries = remember(currentDir, showHidden, textOnly) {
        runCatching { FileBrowser.list(currentDir, showHidden, textOnly) }
            .getOrDefault(emptyList())
    }
    val parent = currentDir.parentFile

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TermBg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TermSurface,
                        titleContentColor = TermGreenBright,
                        navigationIconContentColor = TermGreen
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { parent?.let(onNavigate) },
                            enabled = parent != null
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_left),
                                contentDescription = "上级目录",
                                tint = if (parent != null) TermGreen else TermOnBgVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            currentDir.name.ifEmpty { "/" },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        // 显示隐藏文件开关
                        Text(
                            "隐藏",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TermOnBgVariant
                        )
                        Switch(
                            checked = showHidden,
                            onCheckedChange = { showHidden = it },
                            modifier = Modifier.padding(horizontal = 4.dp).size(width = 40.dp, height = 24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TermGreenBright,
                                checkedTrackColor = TermGreenDeep,
                                uncheckedThumbColor = TermOnBgVariant,
                                uncheckedTrackColor = TermGreenDeep
                            )
                        )
                    }
                )
                // 路径面包屑
                Text(
                    currentDir.absolutePath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TermCodeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
                // 过滤开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermBg)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChipMini(
                        label = if (textOnly) "# 仅可读文档" else "# 全部文件",
                        active = textOnly,
                        onClick = { textOnly = !textOnly }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${entries.size} 项",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = TermOnBgVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasPermission) {
                Box(Modifier.padding(14.dp)) {
                    TermNoticeCard(
                        title = "! 需要存储访问权限",
                        message = "为了浏览设备中的文档，需要授予「所有文件访问权限」。\n授权后返回本页即可。",
                        actionLabel = "▸ 前往授权",
                        onAction = onRequestPermission
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    TermEmptyState(
                        title = "# 此目录为空",
                        lines = buildList {
                            add(currentDir.absolutePath)
                            if (textOnly) add("试试关闭「仅可读文档」筛选")
                            if (!showHidden) add("隐藏文件未显示")
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = entries,
                        key = { it.path }
                    ) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDir) onNavigate(entry.file) else onOpenFile(entry.file)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipMini(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (active) TermGreenDim else TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (active) TermGreenBright else TermOnBgVariant
        )
    }
}

@Composable
private fun FileRow(entry: FileBrowser.Entry, onClick: () -> Unit) {
    val dateText = remember(entry.lastModified) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.lastModified))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (entry.isDir) "▸" else "·",
            fontFamily = FontFamily.Monospace,
            color = if (entry.isDir) TermGreenBright else TermGreen,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name + if (entry.isDir) "/" else "",
                fontFamily = FontFamily.Monospace,
                fontWeight = if (entry.isDir) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (entry.isDir) TermGreenBright else TermGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDir) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${FileBrowser.formatSize(entry.size)} · $dateText",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TermOnBgVariant
                )
            }
        }
    }
}