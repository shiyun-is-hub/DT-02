# 读我 — 谛听文档 (DT-02)

Android 原生（Kotlin + Jetpack Compose）多格式文档阅读器，纯本地、无网络。
借鉴 OpenKB 的「统一中间表示(IR) + 格式分发」思路重写，不移植其 LLM/知识库。

当前版本：**v0.6.0**（versionCode 6，正式签名）

---

## 功能

| 能力 | 说明 |
|---|---|
| 文本格式 | TXT / Markdown / 代码（40+ 语言）/ JSON / XML / HTML / CSV / YAML / 配置 |
| 阅读 | 终端暗绿主题（锁暗色）、字号与行距调节 |
| 侧边栏 | 阅读页右侧抽屉（顶栏 `☰` 唤出，点左侧空白收起），内含字号 / 搜索 / 书签 / 编辑 |
| 搜索 | 全文搜索 + 跳转（基于原文，非解析结果） |
| 书签 | 按 `blockIndex` 锚定，调字号不错位 |
| 编辑 | 全屏编辑器 + 符号底栏（贴 IME）；>256KB 自动启用**分块虚拟化**；保存前生成 `.bak` |
| 缓存 | 已解析文档内存缓存（LRU，key 含 mtime+size），进程被杀即清 |
| 大文件 | 段落分块渲染；32MB 上限，超限**明确提示**而非静默截断 |
| 未支持 | PDF / Word / PPT：给出转换提示，不做解析 |

---

## 构建

```
gradle assembleDebug
```

环境：JDK 17 / AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 35 / minSdk 26。

> 注意：仓库**没有 `gradlew` 脚本和 `gradle-wrapper.jar`**（只有 `gradle-wrapper.properties`），
> 因此无法执行 `./gradlew`，请使用系统 Gradle 8.7。
> 在 arm64 设备上构建需覆盖 aapt2：
> `-Pandroid.aapt2FromMavenOverride=/path/to/arm64/aapt2`

---

## 目录结构

```
app/src/main/java/com/dt/docreader/
  ├ infra/  EncodingDetector, SandboxManager, StoragePermission
  ├ domain/ OpenDocumentUseCase, SearchDocumentUseCase, model/*
  ├ data/   ReaderFactory, reader/*Reader, RecentStore, BookmarkStore,
  │         ReaderSettings, DocumentWriter, DocumentCache
  └ ui/     browser/, reader/, editor/, recent/, settings/, nav/, theme/
app/src/test/java/com/dt/docreader/   ← 单元测试
```

---

## 权限与存储（实际行为）

**本应用使用「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）**，
通过内置文件管理器直接遍历 `/storage` 下的文件。

- **不使用 SAF**。`ACTION_OPEN_DOCUMENT` / `content://` / `takePersistableUriPermission`
  均未使用；`FileSourceFactory.fromUri` 与 `DocumentViewModel.load(uri)` 已实现但**当前未被调用**（死代码）。
- 因此「重启后仍能打开最近文档」依赖的是文件系统路径仍可读，**不是**持久化 URI 权限。
- 该权限无法上架 Google Play，仅适用于自用 / 侧载。

---

## 隐私

- **无网络权限**：Manifest 未声明 `INTERNET`；无 OkHttp / Retrofit / Firebase / Analytics / WebView。
  Markdown 链接仅作文本渲染，不自动发起请求。
- **无云解析**：全部解析在设备本地完成。
- **不持久化文档正文**：书签只保存结构性标签（如「段落 #12」），不保存内容片段。
- **最近记录**只保存路径、显示名、大小、时间戳。
- **已关闭系统备份**（`android:allowBackup="false"` + `fullBackupContent="false"`），
  避免最近记录 / 书签进入云备份或随设备迁移。
- **无日志泄露**：全项目仅一处 `Log.w`（记录解析失败），不含正文 / 路径 / URI。
- **剪贴板**：仅在用户显式点击复制时写入，不自动复制全文。
- 运行时沙箱：`filesDir/documents` 与 `cacheDir/parse`。

---

## 测试

```
gradle testDebugUnitTest
```

共 **73 个单元测试**，覆盖：

| 测试类 | 数量 | 覆盖内容 |
|---|---|---|
| `TextChunkerTest` | 11 | 分块无损性（`join(chunk(t)) == t`）、空行 / 无结尾换行 / 超长单行边界 |
| `EncodingDetectorTest` | 24 | UTF-8 / UTF-8 BOM / UTF-16 LE / UTF-16 BE / GBK / GB18030；中文·English·日本語·Русский·emoji；截断不误判 |
| `FileKindTest` | 16 | 扩展名大小写、无扩展名、多点、隐藏文件、Magic Number |
| `ReaderFactoryTest` | 22 | 各格式 Reader 可解析、大小写不敏感、中文 / 空格 / Unicode 文件名、缓存一致性 |

---

## 审计记录

本项目经过两轮 BUG AUDIT，修复项摘要：

**第一轮** — 状态管理、存储、编码、大文件：

- 快速切换文档时旧解析任务未取消 → 显示错误文档（`loadJob.cancel()`）
- `ReaderFactory` 缓存非线程安全 → `ConcurrentHashMap`
- 书签 key 用 `hashCode()` 会碰撞 → SHA-256/128 位
- 书签 JSON 单项损坏导致整列表丢失 → `optJSONObject`
- 搜索对 32MB 原文 `lowercase()` 内存翻倍 → `indexOf(ignoreCase=true)`
- 搜索定位 O(hits×blocks) → 首字符分桶
- 编码截断落在多字节字符中间 → 整文件误判 GBK → `trimIncompleteTail`
- GB18030 未支持 → 增加回退
- 目录读取失败伪装成空目录 → 区分错误态
- 硬编码 `/storage/emulated/0` → `Environment.getExternalStorageDirectory()`

**第二轮** — 隐私与截断可见性：

- **书签持久化了文档正文**（`preview.take(120)`）→ 改为结构性标签
- **`allowBackup="true"` 且无提取规则** → 改为 `false`
- **超限文件静默截断**（32MB 上限无提示）→ 新增 `DocumentMeta.truncated` + 阅读页红色横幅
- README 中「使用 SAF / 不申请 MANAGE_EXTERNAL_STORAGE」的表述与实际不符 → 已更正

---

## 已知限制

1. **未实现 SAF 架构** —— 依赖文件系统路径 + `MANAGE_EXTERNAL_STORAGE`；`load(uri)` 为死代码。
2. **未使用 `rememberSaveable`** —— 旋转 / 进程重建后阅读位置与侧边栏状态丢失。
3. **无 BOM 的 UTF-16 无法识别**（有 BOM 的正常）。
4. **32MB 文件峰值内存约 130MB**（`ByteArray` + `String` + `blocks` 并存），有上限保护但未优化。
5. **无应用图标**。
6. 上述修复均通过**编译 + 单元测试**验证，**未在设备上逐项手工验证**。

---

## 各格式依赖启用

`app/build.gradle.kts` 中取消对应注释即可（当前均未启用）：

- P2: sora-editor（代码高亮）/ markwon（md）
- P3: pdfbox-android
- P4: poi-ooxml
- P5: poi-scratchpad
- P6: room
