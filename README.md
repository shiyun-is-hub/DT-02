# DocReaderApp — Kotlin 多格式文档阅读 App

Android 原生（Kotlin + Jetpack Compose）多格式文档阅读器。
借鉴 OpenKB 的『统一中间表示(IR) + 格式分发』思路重写，不移植其 LLM/知识库。

## 当前进度
- ✅ P0 工程骨架（Gradle、Compose 空壳、可编译）
- ✅ P1 txt 端到端打通（ReaderFactory + TxtReader + 阅读界面）
- ⏳ P2 Markdown + 代码高亮 / P3 PDF / P4 Word / P5 PPT / P6 打磨：占位已就位

## 构建方式
用 Android Studio 打开本目录（DocReaderApp），等 Gradle Sync 后运行。
JDK 17 / AGP 8.5.x / Kotlin 2.0.x / compileSdk 35。

## 目录结构
app/src/main/java/com/dt/docreader/
  ├ infra/  (EncodingDetector, FileAccess, SandboxManager)
  ├ domain/ (OpenDocumentUseCase, model/DocumentModel, model/FileKind)
  ├ data/   (ReaderFactory, reader/*Reader)
  └ ui/     (home/, reader/, theme/)

## 各格式 Reader 与依赖启用
在 app/build.gradle.kts 中取消对应注释即可：
- P2: sora-editor(代码高亮) / markwon(md)
- P3: pdfbox-android
- P4: poi-ooxml
- P5: poi-scratchpad
- P6: room

## 安全与沙箱
默认用 SAF(OpenDocument) 打开文件，不申请 MANAGE_EXTERNAL_STORAGE。
运行时沙箱为应用私有目录 filesDir/documents 与 cacheDir/parse。
