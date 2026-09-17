package com.dt.docreader.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermCodeText
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBgVariant
import com.dt.docreader.ui.theme.TermSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onFilePicked: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onFilePicked(uri) }

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
                        "docreader@local:~$",
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 终端欢迎语
            TerminalBanner()

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { launcher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TermGreenDeep,
                    contentColor = TermGreenBright
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, TermGreen)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(
                    "./open --file",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            SupportCard()
        }
    }
}

@Composable
private fun TerminalBanner() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "DocReader v0.2.0",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = TermGreenBright
        )
        Text(
            "# multi-format document reader",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TermOnBgVariant
        )
        Text(
            "# 打开本地文档，自动识别格式",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = TermOnBgVariant
        )
    }
}

@Composable
private fun SupportCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermGreenDeep, MaterialTheme.shapes.small)
            .border(1.dp, TermGreenDim, MaterialTheme.shapes.small)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "── 支持的格式 ──",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TermGreen
            )
            SupportRow("纯文本", "txt · text · log · nfo")
            SupportRow("Markdown", "md · markdown · mdown · mkd")
            SupportRow("代码", "kt · java · py · js · ts · c · cpp · rs · go …40+ 语言")
            SupportRow("结构化数据", "json · xml · html · csv · tsv · yaml")
            SupportRow("配置", "ini · properties · toml · env · cfg · conf")
            Spacer(Modifier.height(2.dp))
            Text(
                "# P3–P5：PDF · Word · PPT",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TermOnBgVariant
            )
        }
    }
}

@Composable
private fun SupportRow(title: String, detail: String) {
    Row {
        Text(
            "▸ ",
            fontFamily = FontFamily.Monospace,
            color = TermGreenBright,
            style = MaterialTheme.typography.bodyMedium
        )
        Column {
            Text(
                title,
                fontFamily = FontFamily.Monospace,
                color = TermGreen,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TermCodeText
            )
        }
    }
}