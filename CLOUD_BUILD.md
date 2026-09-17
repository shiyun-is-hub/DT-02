# 云端构建指南（GitHub Actions）

本工程已内置 GitHub Actions 工作流：`.github/workflows/build.yml`
无需手机或电脑搭环境，云端自动编译出 APK。

## 一、前置准备
1. 一个 GitHub 账号（免费）。
2. 手机端可用任何 Git 客户端，或直接用手机浏览器在 GitHub 网页上传文件。

## 二、把工程推上 GitHub

### 方式 A：命令行（如果你有可用的 git 环境）
```bash
cd /storage/emulated/0/DT/DT文档/DocReaderApp
git init
git add .
git commit -m "init DocReaderApp with CI"
git branch -M main
git remote add origin https://github.com/<你的用户名>/DocReaderApp.git
git push -u origin main
```

### 方式 B：网页上传（手机也能做）
1. 在 GitHub 新建仓库 `DocReaderApp`（Public 或 Private 均可）。
2. 进入仓库 → Add file → Upload files。
3. 把 `DocReaderApp/` 下所有文件（含 `.github`、`app`、`gradle`、`build.gradle.kts` 等）拖入上传。
   - 注意：**不要把本机的 `local.properties` 上传**（已在 .gitignore 忽略）。
4. 提交。

## 三、触发构建
- 推送到 `main`/`master` 分支会自动触发；
- 或在仓库页面 → Actions → Build Debug APK → Run workflow 手动触发。

## 四、下载 APK
1. 构建完成后，进入 Actions → 对应运行记录。
2. 页面底部 **Artifacts** 区域下载 `DocReader-debug-apk`（zip）。
3. 解压得到 `app-debug.apk`，传到手机安装（需允许"未知来源"）。

## 五、构建失败排查
- 在 Actions 运行记录里点开 `Build debug APK` 步骤查看日志。
- 常见问题：
  - `.gitignore` 误忽略了 `gradle/` → 确保 `gradle/wrapper/gradle-wrapper.properties` 已上传。
  - 依赖下载失败 → 重跑一次。
  - `local.properties` 被上传导致 SDK 路径错误 → 删除后再构建（CI 用环境变量定位 SDK）。

## 六、工作流关键配置
- JDK：Temurin 17
- Gradle：8.7（由 gradle/actions/setup-gradle 提供）
- Android SDK：android-actions/setup-android 自动安装
- 产物：`app/build/outputs/apk/debug/*.apk`
