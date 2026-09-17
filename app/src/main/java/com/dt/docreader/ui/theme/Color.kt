package com.dt.docreader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 终端风配色（Terminal / Hacker 风格）
 *
 * 设计原则：
 * - 锁定暗色，不使用动态取色（dynamic color），保证终端风格一致。
 * - 线条与强调色统一使用「暗绿」，与近黑背景形成低饱和对比，久看不刺眼。
 * - 文字使用偏绿的浅灰白，避免纯白在暗底上的眩光。
 */

// ---- 基础背景 ----
/** 近黑（略带绿调）—— 主背景 */
val TermBg = Color(0xFF0B0F0C)
/** 略亮的表面（卡片/顶栏） */
val TermSurface = Color(0xFF10160F)
/** 更高一层的表面（弹层/输入框） */
val TermSurfaceHigh = Color(0xFF161E17)

// ---- 绿色系（线条 / 强调） ----
/** 暗绿 —— 主色、界面线条 */
val TermGreen = Color(0xFF2E7D4F)
/** 亮绿 —— 高亮、选中、光标 */
val TermGreenBright = Color(0xFF4ADE80)
/** 深绿 —— 分隔线、边框 */
val TermGreenDim = Color(0xFF1F4A31)
/** 极暗绿 —— 代码块背景 */
val TermGreenDeep = Color(0xFF0A0E0B)

// ---- 文字 ----
/** 主文字：浅绿白 */
val TermOnBg = Color(0xFFC8E6C9)
/** 次要文字 */
val TermOnBgVariant = Color(0xFF7BA88A)
/** 代码文字 */
val TermCodeText = Color(0xFFB9DCC4)
/** 行号灰绿 */
val TermLineNumber = Color(0xFF4A6B57)

// ---- 状态色（保持终端常见色） ----
val TermError = Color(0xFFE05252)
val TermWarning = Color(0xFFD9A441)
val TermInfo = Color(0xFF5AA9E6)

// ---- 兼容旧引用（避免遗漏导入导致编译失败） ----
@Deprecated("使用终端风配色", ReplaceWith("TermGreen"))
val Purple80 = TermGreen
@Deprecated("使用终端风配色", ReplaceWith("TermGreen"))
val PurpleGrey80 = TermGreenDim
@Deprecated("使用终端风配色", ReplaceWith("TermGreen"))
val Pink80 = TermGreenBright
@Deprecated("使用终端风配色", ReplaceWith("TermGreen"))
val Purple40 = TermGreen
@Deprecated("使用终端风配色", ReplaceWith("TermGreenDim"))
val PurpleGrey40 = TermGreenDim
@Deprecated("使用终端风配色", ReplaceWith("TermGreenBright"))
val Pink40 = TermGreenBright