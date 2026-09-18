package com.dt.docreader.data

import android.content.Context

/**
 * 阅读设置（字号 / 行距）持久化。
 *
 * 用 SharedPreferences 存储：设置项很少、读写频繁、无需关系型数据库。
 * 所有取值都做了范围钳制，避免外部写入越界值导致布局异常。
 */
object ReaderSettings {

    private const val PREF = "docreader_reader_settings"

    private const val KEY_BODY_SIZE = "body_font_size"
    private const val KEY_CODE_SIZE = "code_font_size"
    private const val KEY_LINE_HEIGHT = "line_height_scale"

    // ---- 取值范围 ----
    const val BODY_MIN = 12f
    const val BODY_MAX = 28f
    const val BODY_DEFAULT = 16f

    const val CODE_MIN = 9f
    const val CODE_MAX = 22f
    const val CODE_DEFAULT = 12.5f

    const val LINE_MIN = 1.0f
    const val LINE_MAX = 2.0f
    const val LINE_DEFAULT = 1.35f

    /** 不可变快照，供 UI 读取（避免每次组合都访问 SharedPreferences）。 */
    data class Snapshot(
        val bodyFontSize: Float = BODY_DEFAULT,
        val codeFontSize: Float = CODE_DEFAULT,
        val lineHeightScale: Float = LINE_DEFAULT
    )

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return Snapshot(
            bodyFontSize = p.getFloat(KEY_BODY_SIZE, BODY_DEFAULT).coerceIn(BODY_MIN, BODY_MAX),
            codeFontSize = p.getFloat(KEY_CODE_SIZE, CODE_DEFAULT).coerceIn(CODE_MIN, CODE_MAX),
            lineHeightScale = p.getFloat(KEY_LINE_HEIGHT, LINE_DEFAULT).coerceIn(LINE_MIN, LINE_MAX)
        )
    }

    fun save(context: Context, s: Snapshot) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_BODY_SIZE, s.bodyFontSize.coerceIn(BODY_MIN, BODY_MAX))
            .putFloat(KEY_CODE_SIZE, s.codeFontSize.coerceIn(CODE_MIN, CODE_MAX))
            .putFloat(KEY_LINE_HEIGHT, s.lineHeightScale.coerceIn(LINE_MIN, LINE_MAX))
            .apply()
    }

    fun reset(context: Context): Snapshot {
        val d = Snapshot()
        save(context, d)
        return d
    }
}