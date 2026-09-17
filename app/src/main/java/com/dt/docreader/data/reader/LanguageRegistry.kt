package com.dt.docreader.data.reader

import com.dt.docreader.domain.model.FileKind

/**
 * 语言注册表：扩展名 -> 语言标识 / 显示名。
 *
 * 用途：
 * 1) CodeReader 用它决定 CodeBlock.language，供 UI 做语法高亮；
 * 2) 展示「当前文件是什么语言」；
 * 3) 与 FileKind 解耦——FileKind 只区分「大类」，本表提供「具体语言」。
 */
object LanguageRegistry {

    data class Language(
        /** 用于高亮的标识，如 "kotlin" / "python"。 */
        val id: String,
        /** 界面展示名，如 "Kotlin"。 */
        val displayName: String
    )

    private val byExtension: Map<String, Language> = buildMap {
        fun put(lang: Language, vararg exts: String) = exts.forEach { put(it, lang) }

        put(Language("kotlin", "Kotlin"), "kt", "kts")
        put(Language("java", "Java"), "java")
        put(Language("python", "Python"), "py", "pyw")
        put(Language("javascript", "JavaScript"), "js", "mjs", "cjs")
        put(Language("typescript", "TypeScript"), "ts", "tsx")
        put(Language("jsx", "React JSX"), "jsx")
        put(Language("c", "C"), "c", "h")
        put(Language("cpp", "C++"), "cpp", "cc", "cxx", "hpp", "hh")
        put(Language("csharp", "C#"), "cs")
        put(Language("rust", "Rust"), "rs")
        put(Language("go", "Go"), "go")
        put(Language("swift", "Swift"), "swift")
        put(Language("php", "PHP"), "php")
        put(Language("ruby", "Ruby"), "rb")
        put(Language("shell", "Shell"), "sh", "bash", "zsh", "fish")
        put(Language("sql", "SQL"), "sql")
        put(Language("lua", "Lua"), "lua")
        put(Language("dart", "Dart"), "dart")
        put(Language("r", "R"), "r")
        put(Language("scala", "Scala"), "scala")
        put(Language("clojure", "Clojure"), "clj", "cljs")
        put(Language("elixir", "Elixir"), "ex", "exs")
        put(Language("erlang", "Erlang"), "erl")
        put(Language("haskell", "Haskell"), "hs")
        put(Language("objectivec", "Objective-C"), "m", "mm")
        put(Language("vbnet", "VB.NET"), "vb")
        put(Language("asm", "Assembly"), "asm", "s")
        put(Language("pascal", "Pascal"), "pas")
        put(Language("perl", "Perl"), "pl", "pm")
        put(Language("groovy", "Groovy"), "groovy", "gradle")
        put(Language("vue", "Vue"), "vue")
        put(Language("svelte", "Svelte"), "svelte")

        // 标记 / 数据（也用于 CodeBlock 高亮标识）
        put(Language("xml", "XML"), "xml", "xsd", "xsl", "xslt", "svg", "plist")
        put(Language("html", "HTML"), "html", "htm", "xhtml")
        put(Language("json", "JSON"), "json", "jsonc", "json5")
        put(Language("yaml", "YAML"), "yaml", "yml")
        put(Language("toml", "TOML"), "toml")
        put(Language("ini", "INI"), "ini", "properties", "cfg", "conf", "env",
            "editorconfig", "gitignore")
        put(Language("markdown", "Markdown"), "md", "markdown", "mdown", "mkd")
        put(Language("csv", "CSV"), "csv", "tsv")
    }

    /** 从文件名解析语言；未知返回 null。 */
    fun fromFileName(fileName: String): Language? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return byExtension[ext]
    }

    /** 依据 FileKind 给出兜底语言。 */
    fun fallbackFor(kind: FileKind): Language = when (kind) {
        FileKind.HTML -> Language("html", "HTML")
        FileKind.XML -> Language("xml", "XML")
        FileKind.JSON -> Language("json", "JSON")
        FileKind.YAML -> Language("yaml", "YAML")
        FileKind.CSV -> Language("csv", "CSV")
        FileKind.CONFIG -> Language("ini", "Config")
        FileKind.MARKDOWN -> Language("markdown", "Markdown")
        else -> Language("plaintext", "纯文本")
    }
}