package com.dt.docreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 终端风暗色主题。
 *
 * 关键设计决定：
 * - **锁定暗色**：不再跟随系统、不再使用动态取色，保证终端风格恒定。
 * - 线条统一使用暗绿（outline / outlineVariant），卡片与分隔线呈绿色描边。
 */

private val TerminalColorScheme = darkColorScheme(
    // 主色：暗绿；强调交互元素
    primary = TermGreenBright,
    onPrimary = TermBg,
    primaryContainer = TermGreenDim,
    onPrimaryContainer = TermOnBg,

    secondary = TermGreen,
    onSecondary = TermBg,
    secondaryContainer = TermSurfaceHigh,
    onSecondaryContainer = TermOnBg,

    tertiary = TermGreenDim,
    onTertiary = TermOnBg,

    // 背景与表面
    background = TermBg,
    onBackground = TermOnBg,
    surface = TermSurface,
    onSurface = TermOnBg,
    surfaceVariant = TermSurfaceHigh,
    onSurfaceVariant = TermOnBgVariant,
    surfaceContainer = TermSurface,
    surfaceContainerHigh = TermSurfaceHigh,
    surfaceContainerHighest = TermSurfaceHigh,

    // 线条（界面线条 = 暗绿）
    outline = TermGreen,
    outlineVariant = TermGreenDim,

    // 错误
    error = TermError,
    onError = TermBg,

    // 反色（个别组件用）
    inverseSurface = TermOnBg,
    inverseOnSurface = TermBg,
    inversePrimary = TermGreen
)

private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

/**
 * 应用主题入口。
 *
 * 注意：签名保留 darkTheme / dynamicColor 两个参数以避免调用方改动，
 * 但实现上**强制暗色、强制关闭动态色**，实现「锁定暗色调」的需求。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DocReaderTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        shapes = TerminalShapes,
        content = content
    )
}