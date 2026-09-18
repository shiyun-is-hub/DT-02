package com.dt.docreader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 阅读页的右侧抽屉侧边栏。
 *
 * ## 交互（最终版）
 *
 * - **顶栏菜单按钮**唤出（见 ReaderScreen 的 `actions`）。
 * - **点击侧边栏之外的空白区域（剩余 [1 - WIDTH_RATIO]）收起**。
 * - 侧边栏浮在阅读页**上层**，操作侧边栏不影响下层阅读页状态。
 * - 宽度为屏幕的 [WIDTH_RATIO]。
 *
 * ## 为什么不做滑动手势（重要教训）
 *
 * 曾尝试「全屏左滑唤出」，用 `awaitEachGesture` + 手动方向判定。
 * 无论用 `PointerEventPass.Initial` 还是 `Final`，
 * **只要在阅读页之上铺一层全屏 `pointerInput`，就会破坏 LazyColumn 的滚动**：
 * `awaitFirstDown` 会消费 down 事件，导致滚动识别失败。
 *
 * 结论：**阅读页上层不注册任何全屏手势**。
 * 唤出走显式按钮，收起走点击空白 —— 二者都不会干扰滚动。
 *
 * @param open 是否展开
 * @param onOpenChange 展开状态变化回调
 * @param sidebar 侧边栏内容
 * @param content 阅读页内容（下层）
 */
@Composable
fun ReaderScaffoldWithSidebar(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val panelWidthPx = screenWidthPx * WIDTH_RATIO

    val animated by animateFloatAsState(
        targetValue = if (open) 0f else panelWidthPx,
        animationSpec = tween(durationMillis = 220),
        label = "sidebar"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        // ---- 下层：阅读页（不注册任何手势，滚动完全不受影响） ----
        content()

        // ---- 遮罩：只覆盖侧边栏之外的区域 ----
        // 仅在面板**真正展开**时才存在（用 open 判断，不能用动画值）：
        // 否则面板关闭时遮罩仍会拦截左侧 20% 的触摸，导致无法纵向滚动。
        if (open) {
            val openProgress = 1f - (animated / panelWidthPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width((configuration.screenWidthDp * (1f - WIDTH_RATIO)).dp)
                    .background(Color.Black.copy(alpha = 0.45f * openProgress))
                    .clickable(
                        // 去掉水波纹与焦点反馈：这是"点空白关闭"，不是按钮
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpenChange(false) }
            )
        }

        // ---- 上层：侧边栏 ----
        // 面板自身会消费点击，因此点面板内部不会触发上面的遮罩关闭。
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset { IntOffset(animated.roundToInt(), 0) }
                .width((configuration.screenWidthDp * WIDTH_RATIO).dp)
                .fillMaxHeight()
        ) {
            sidebar()
        }
    }
}

/** 侧边栏宽度占屏幕比例。 */
private const val WIDTH_RATIO = 0.80f