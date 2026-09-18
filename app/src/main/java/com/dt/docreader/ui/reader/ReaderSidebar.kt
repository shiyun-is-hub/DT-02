package com.dt.docreader.ui.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dt.docreader.ui.theme.TermGreenDim
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 阅读页的右侧抽屉侧边栏。
 *
 * 交互（按需求）：
 * - **全屏左滑**唤出（任意位置向左拖动）。
 * - **点击侧边栏之外的空白区域（剩余 [1 - WIDTH_RATIO]）收起**。
 *   —— 不做右滑退出，避免与阅读页手势/滚动产生歧义。
 * - 侧边栏浮在阅读页**上层**，操作侧边栏不影响下层阅读页状态。
 * - 宽度为屏幕的 [WIDTH_RATIO]。
 *
 * ## 手势实现要点（易踩坑）
 *
 * 阅读页是 `LazyColumn`（垂直滚动）。若用 `detectHorizontalDragGestures`
 * 铺满全屏，它会**无条件**吞掉所有水平拖动。这里采用
 * 「先判方向、再决定是否消费」的方式，保证垂直滚动零影响：
 *
 * 1. `awaitFirstDown(pass = PointerEventPass.Initial)` 拿到按下点，
 *    **此时不消费事件**，让下层正常收到。
 * 2. 累计位移超过 [DIRECTION_THRESHOLD_DP] 后判定方向：
 *    - 水平占优 **且向左** → 消费事件，驱动面板展开；
 *    - 其余情况（垂直占优 / 向右）→ 直接放手，不再干预。
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
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val panelWidthPx = screenWidthPx * WIDTH_RATIO
    val thresholdPx = with(density) { DIRECTION_THRESHOLD_DP.dp.toPx() }

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
        // ---- 下层：阅读页 ----
        content()

        // ---- 左滑唤出手势层：仅在面板未展开时生效 ----
        if (!open) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            var totalX = 0f
                            var totalY = 0f
                            var decided = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) break

                                totalX += change.position.x - change.previousPosition.x
                                totalY += change.position.y - change.previousPosition.y

                                if (!decided) {
                                    if (abs(totalX) > thresholdPx || abs(totalY) > thresholdPx) {
                                        decided = true
                                        // 垂直占优 或 向右 → 放手，交给下层
                                        if (abs(totalY) > abs(totalX) || totalX > 0f) break
                                    }
                                } else {
                                    // 已判定为「左滑唤出」：消费事件，阻止下层滚动
                                    change.consume()
                                    if (totalX < -thresholdPx) {
                                        onOpenChange(true)
                                        break
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // ---- 右侧竖线把手：提示"这里可以唤出侧边栏" ----
        // 距屏幕右边缘 2dp，宽度 2dp，高度占屏幕 22%（居中）。
        // 面板展开时淡出（它只负责"提示可唤出"）。
        if (!open) {
            val handleAlpha = (1f - (animated / panelWidthPx).coerceIn(0f, 1f))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.22f)
                    .width(HANDLE_WIDTH_DP.dp)
                    .padding(end = HANDLE_MARGIN_DP.dp)
                    .background(
                        TermGreenDim.copy(alpha = 0.85f * handleAlpha),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                    )
                    .pointerInput(Unit) {
                        // 点竖线也可唤出（与左滑并存）
                        detectTapGestures { onOpenChange(true) }
                    }
            )
        }

        // ---- 遮罩：只覆盖侧边栏之外的区域 ----
        // 宽度 = 屏幕宽 - 面板宽，因此它**不会**与面板重叠，
        // 点击面板内部绝不会误触关闭；点击这块空白则收起。
        if (animated < panelWidthPx - 1f) {
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

/** 判定水平/垂直方向所需的累计位移（dp）。 */
private const val DIRECTION_THRESHOLD_DP = 12f

/** 右侧竖线把手：宽度（dp）。 */
private const val HANDLE_WIDTH_DP = 2f

/** 右侧竖线把手：距屏幕右边缘的距离（dp）。 */
private const val HANDLE_MARGIN_DP = 2f