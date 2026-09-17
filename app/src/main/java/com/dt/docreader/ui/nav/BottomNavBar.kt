package com.dt.docreader.ui.nav

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dt.docreader.R
import com.dt.docreader.ui.theme.TermBg
import com.dt.docreader.ui.theme.TermGreen
import com.dt.docreader.ui.theme.TermGreenBright
import com.dt.docreader.ui.theme.TermGreenDeep
import com.dt.docreader.ui.theme.TermGreenDim
import com.dt.docreader.ui.theme.TermOnBgVariant

/** 底栏三个位置。 */
enum class BottomTab { RECENT, BROWSER, SETTINGS }

/**
 * 自绘底部导航栏（原生 Android 10 风格）。
 *
 * 布局：左（主页/最近）· 中（圆形加号，凸起填色）· 右（设置）
 */
@Composable
fun BottomNavBar(
    current: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TermBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(
            tab = BottomTab.RECENT,
            label = "主页",
            iconRes = R.drawable.ic_tab_home,
            selected = current == BottomTab.RECENT,
            onClick = { onSelect(BottomTab.RECENT) },
            modifier = Modifier.weight(1f)
        )

        // 中间：圆形加号（凸起 + 填色 + 按压反馈）
        CenterPlusButton(
            selected = current == BottomTab.BROWSER,
            onClick = { onSelect(BottomTab.BROWSER) },
            modifier = Modifier.weight(1f)
        )

        NavItem(
            tab = BottomTab.SETTINGS,
            label = "设置",
            iconRes = R.drawable.ic_tab_settings,
            selected = current == BottomTab.SETTINGS,
            onClick = { onSelect(BottomTab.SETTINGS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NavItem(
    tab: BottomTab,
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) TermGreenBright else TermOnBgVariant
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 中间的圆形加号按钮。
 *
 * - 填色圆形（亮绿底 + 深色加号）
 * - 按压缩放动画（0.88）
 * - 选中态用边框高亮
 */
@Composable
private fun CenterPlusButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "plusScale"
    )
    val container = if (selected) TermGreenBright else TermGreen

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(container)
                .border(1.dp, if (selected) TermGreenBright else TermGreenDim, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tab_plus),
                contentDescription = "文件管理器",
                tint = Color(0xFF0B0F0C),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}