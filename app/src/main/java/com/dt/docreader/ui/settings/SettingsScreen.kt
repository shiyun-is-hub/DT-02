package com.dt.docreader.ui.settings

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.R
import com.dt.docreader.ui.components.TermNoticeCard
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermError
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBg
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

/**
 * 设置页：权限状态、支持格式、数据管理、关于。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    hasPermission: Boolean,
    recentCount: Int,
    onRequestPermission: () -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }

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
                        "settings@local:~$",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 权限 ----
            SectionTitle("权限")
            if (hasPermission) {
                InfoCard(
                    title = "✓ 所有文件访问权限",
                    lines = listOf("状态：已授予", "可浏览设备中的全部文档")
                )
            } else {
                TermNoticeCard(
                    title = "! 所有文件访问权限",
                    message = "当前未授予。无此权限时无法浏览设备中的文档。",
                    actionLabel = "▸ 前往授权",
                    onAction = onRequestPermission
                )
            }

            // ---- 数据 ----
            SectionTitle("数据")
            InfoCard(
                title = "最近打开记录",
                lines = listOf("共 $recentCount 条记录")
            )
            Spacer(Modifier.height(-4.dp))
            DangerAction(
                label = if (showClearConfirm) "确认清空？点击执行" else "清空最近打开记录",
                icon = true,
                enabled = recentCount > 0,
                onDoubleClick = {
                    if (showClearConfirm) {
                        onClearRecent()
                        showClearConfirm = false
                    } else {
                        showClearConfirm = true
                    }
                }
            )
            if (showClearConfirm) {
                Text(
                    "# 再次点击确认清空（不删除文件本身）",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TermError
                )
            }

            // ---- 支持格式 ----
            SectionTitle("支持的格式")
            InfoCard(
                title = "文本类",
                lines = listOf(
                    "纯文本  txt · text · log · nfo",
                    "Markdown  md · markdown · mkd",
                    "代码  kt · java · py · js · c · go …40+ 语言",
                    "结构化  json · xml · html · csv · yaml",
                    "配置  ini · toml · properties · env · cfg"
                )
            )
            InfoCard(
                title = "Office 文档",
                lines = listOf(
                    "Word  docx（含标题/列表/表格）",
                    "演示  pptx（多页幻灯片）",
                    "旧版 doc / ppt 请另存为新格式"
                )
            )
            InfoCard(
                title = "PDF 暂不支持",
                lines = listOf("将由独立的 PDF 查看器提供")
            )

            // ---- 关于 ----
            SectionTitle("关于")
            InfoCard(
                title = "DocReader",
                lines = listOf(
                    "版本：v0.3.0",
                    "架构：统一中间表示(IR) + 格式分发",
                    "UI：Jetpack Compose · 终端暗绿主题",
                    "图标：Tabler Icons (MIT)",
                    "语言：Kotlin"
                )
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        "── $text ──",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = TermGreen,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = TermGreenBright
        )
        lines.forEach { line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TermCodeText,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun DangerAction(
    label: String,
    icon: Boolean,
    enabled: Boolean,
    onDoubleClick: () -> Unit
) {
    val contentColor = if (enabled) TermError else TermOnBgVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(TermGreenDeep)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onDoubleClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                painter = painterResource(R.drawable.ic_trash),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}