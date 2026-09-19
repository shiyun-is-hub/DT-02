package com.dt.docreader

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dt.docreader.data.DocumentWriter
import com.dt.docreader.data.FileBrowser
import com.dt.docreader.data.RecentStore
import com.dt.docreader.infra.StoragePermission
import com.dt.docreader.ui.browser.FileBrowserScreen
import com.dt.docreader.ui.editor.EditorScreen
import kotlinx.coroutines.launch
import com.dt.docreader.ui.nav.BottomNavBar
import com.dt.docreader.ui.nav.BottomTab
import com.dt.docreader.ui.reader.DocumentViewModel
import com.dt.docreader.ui.reader.ReaderScreen
import com.dt.docreader.ui.recent.RecentScreen
import com.dt.docreader.ui.settings.SettingsScreen
import com.dt.docreader.ui.theme.DocReaderTheme
import com.dt.docreader.ui.theme.TermBg
import java.io.File

class MainActivity : ComponentActivity() {

    /**
     * 外部传入的待打开 URI（`ACTION_VIEW` / `ACTION_SEND`）。
     *
     * 用可变状态而非直接读 `intent`：因为 Activity 可能已存在并被复用
     * （`launchMode` 默认 standard 时不会，但用户从最近任务切回、
     * 或系统复用实例时 `onNewIntent` 会触发），需要让 Compose 感知变化。
     */
    private var pendingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 锁定暗色系统栏（终端风）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        pendingUri.value = extractUri(intent)
        setContent {
            DocReaderTheme {
                // 全局避开状态栏：内容渲染在状态栏下方，而不是被状态栏压住。
                // 放在最外层，使主界面 / 阅读页 / 编辑器三者行为一致。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TermBg)
                        .statusBarsPadding()
                ) {
                    AppRoot(externalUri = pendingUri.value, onExternalUriConsumed = {
                        pendingUri.value = null
                    })
                }
            }
        }
    }

    /**
     * 应用已在前台时被再次调起（如从文件管理器再次点文件）。
     * 不处理会导致「点了没反应」。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUri(intent)?.let { pendingUri.value = it }
    }

    /** 从 Intent 中提取文档 URI；不是文档打开请求则返回 null。 */
    private fun extractUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
}

/** 打开中的文档状态（null = 未打开）。 */
private data class OpenDoc(
    /** 文件系统路径；URI 打开时为 null。 */
    val path: String?,
    /** 外部传入的 URI；路径打开时为 null。 */
    val uri: Uri? = null
)

/** 编辑中的文档状态。 */
private data class EditDoc(
    val path: String,
    val initialText: String,
    val fileName: String
)

@Composable
private fun AppRoot(
    externalUri: Uri?,
    onExternalUriConsumed: () -> Unit
) {
    val context = LocalContext.current

    // ---- 导航状态 ----
    var tab by remember { mutableStateOf(BottomTab.RECENT) }
    var openDoc by remember { mutableStateOf<OpenDoc?>(null) }
    var editDoc by remember { mutableStateOf<EditDoc?>(null) }

    // ---- 外部调起：文件管理器 / 分享 / 浏览器点文档 ----
    // 注意：**不写入「最近记录」**。RecentStore 存的是文件系统路径，
    // 而 content:// URI 不是稳定路径（provider 可能失效、权限可能被回收），
    // 记进去只会在下次点击时打开失败。
    LaunchedEffect(externalUri) {
        val uri = externalUri ?: return@LaunchedEffect
        openDoc = OpenDoc(path = null, uri = uri)
        onExternalUriConsumed()
    }

    // ---- 编辑器状态 ----
    var editDirty by remember { mutableStateOf(false) }
    var editSaving by remember { mutableStateOf(false) }
    var editMessage by remember { mutableStateOf<String?>(null) }
    val editScope = rememberCoroutineScope()

    // ---- 文件管理器状态 ----
    // 用 Environment API 而非硬编码路径（多用户/特殊 ROM 下路径可能不同）
    val storageRoot = remember { FileBrowser.storageRoots().firstOrNull() ?: File("/") }
    var currentDir by remember { mutableStateOf(storageRoot) }

    // ---- 权限状态（进入页面 / 从设置返回时刷新） ----
    var hasPermission by remember { mutableStateOf(StoragePermission.hasAllFilesAccess(context)) }
    // 用 mutableIntStateOf 避免 Int 装箱（AutoboxingStateCreation）
    var permissionTick by remember { mutableIntStateOf(0) }

    // ---- 最近记录 ----
    var recents by remember { mutableStateOf(RecentStore.listExisting(context)) }

    // 生命周期：回到前台时刷新权限与最近列表
    DisposableEffect(permissionTick) {
        hasPermission = StoragePermission.hasAllFilesAccess(context)
        recents = RecentStore.listExisting(context)
        onDispose { }
    }

    LaunchedEffect(openDoc) {
        // 关闭阅读器后刷新最近列表
        if (openDoc == null) recents = RecentStore.listExisting(context)
    }

    val requestPermission: () -> Unit = {
        runCatching { context.startActivity(StoragePermission.allFilesAccessIntent(context)) }
            .onFailure {
                runCatching { context.startActivity(StoragePermission.appDetailsIntent(context)) }
            }
        // 用户去设置页授权，回来后手动点一下即可刷新
        permissionTick++
    }

    // ---- 编辑器：全屏覆盖（优先级高于阅读器） ----
    val editing = editDoc
    if (editing != null) {
        BackHandler {
            if (editDirty) {
                editMessage = "! 有未保存的修改，请先保存或再次退出"
                // 二次返回才真正退出
                editDirty = false
            } else {
                editDoc = null
                editMessage = null
            }
        }
        EditorScreen(
            fileName = editing.fileName,
            initialText = editing.initialText,
            dirty = editDirty,
            saving = editSaving,
            message = editMessage,
            onDirtyChange = { editDirty = it },
            onSave = { getContent ->
                if (editSaving) return@EditorScreen
                editSaving = true
                editMessage = null
                editScope.launch {
                    // 保存瞬间才拼接全文（大文件编辑过程中零全文拷贝）
                    val content = getContent()
                    val result = DocumentWriter.write(
                        path = editing.path,
                        content = content,
                        charsetName = null,
                        makeBackup = true
                    )
                    editSaving = false
                    when (result) {
                        is DocumentWriter.Result.Ok -> {
                            editMessage = "✓ 已保存（备份：${result.backupPath?.substringAfterLast('/') ?: "无"}）"
                            editDirty = false
                            // 同步更新编辑态初值，避免再次编辑时误判 dirty
                            editDoc = editing.copy(initialText = content)
                        }
                        is DocumentWriter.Result.Fail -> {
                            editMessage = "! 保存失败：${result.message}"
                        }
                    }
                }
            },
            onExit = {
                if (editDirty) {
                    editMessage = "! 有未保存的修改，请先保存或再次退出"
                    editDirty = false
                } else {
                    editDoc = null
                    editMessage = null
                }
            }
        )
        return
    }

    // ---- 阅读器：全屏覆盖（无底栏） ----
    val doc = openDoc
    if (doc != null) {
        val vm: DocumentViewModel = viewModel()
        // 按来源分流：外部 URI 走 content:// 解析，本地路径走文件 + 缓存。
        // key 用 uri/path 本身，切换文档时 LaunchedEffect 会重新加载。
        val loadKey = doc.uri?.toString() ?: doc.path
        LaunchedEffect(loadKey) {
            if (doc.uri != null) vm.load(doc.uri) else doc.path?.let { vm.loadFile(it) }
        }
        BackHandler { openDoc = null }
        ReaderScreen(
            viewModel = vm,
            onBack = { openDoc = null },
            // URI 模式下禁用编辑：编辑器按文件系统路径写回，
            // content:// 不是可写路径，进入后保存必然失败。
            editable = doc.uri == null,
            onEdit = { path, text ->
                editDirty = false
                editMessage = null
                editDoc = EditDoc(path, text, File(path).name)
            }
        )
        return
    }

    // ---- 主界面（带底栏） ----
    BackHandler(enabled = tab == BottomTab.BROWSER && currentDir != storageRoot) {
        currentDir.parentFile?.let { currentDir = it }
    }

    Scaffold(
        containerColor = TermBg,
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                BottomNavBar(
                    current = tab,
                    onSelect = { selected ->
                        tab = selected
                        if (selected == BottomTab.BROWSER) {
                            // 每次进入都回到根目录，避免停在深层路径
                            if (currentDir != storageRoot) currentDir = storageRoot
                        }
                        if (selected == BottomTab.RECENT) {
                            recents = RecentStore.listExisting(context)
                        }
                        hasPermission = StoragePermission.hasAllFilesAccess(context)
                    }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(TermBg)
                .padding(padding)
        ) {
            when (tab) {
                BottomTab.RECENT -> RecentScreen(
                    items = recents,
                    onOpen = { item ->
                        RecentStore.add(context, item.path, item.name, item.size)
                        openDoc = OpenDoc(item.path)
                    },
                    onRemove = { item ->
                        RecentStore.remove(context, item.path)
                        recents = RecentStore.listExisting(context)
                    },
                    onGoBrowser = { tab = BottomTab.BROWSER }
                )

                BottomTab.BROWSER -> FileBrowserScreen(
                    currentDir = currentDir,
                    hasPermission = hasPermission,
                    onNavigate = { dir -> currentDir = dir },
                    onOpenFile = { file ->
                        // 不支持的文件给出提示而不是静默失败
                        if (isOpenable(file)) {
                            RecentStore.add(context, file.absolutePath, file.name, file.length())
                            openDoc = OpenDoc(file.absolutePath)
                        }
                    },
                    onRequestPermission = requestPermission
                )

                BottomTab.SETTINGS -> SettingsScreen(
                    hasPermission = hasPermission,
                    recentCount = recents.size,
                    onRequestPermission = requestPermission,
                    onClearRecent = {
                        RecentStore.clear(context)
                        recents = emptyList()
                    }
                )
            }
        }
    }
}

/** 是否可用本 App 打开（文本家族）。 */
private fun isOpenable(file: File): Boolean {
    val name = file.name
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext.isEmpty()) return true // 无扩展名按文本处理
    return ext in com.dt.docreader.data.ReaderFactory.SUPPORTED_EXTENSIONS
}