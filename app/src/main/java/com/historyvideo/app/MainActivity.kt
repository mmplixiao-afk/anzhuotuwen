package com.historyvideo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.media.MediaPlayer
import android.widget.VideoView
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.io.File
import java.util.Date
import kotlin.math.abs

private object HistoryColors {
    val paper = Color(0xFFFAF8F4)
    val card = Color.White
    val subcard = Color(0xFFF3F1ED)
    val ink = Color(0xFF202124)
    val muted = Color(0xFF737579)
    val line = Color(0xFFE7E3DD)
    val cinnabar = Color(0xFFC32621)
    val cinnabarSoft = Color(0xFFFCECE8)
    val teal = Color(0xFF405956)
    val tealSoft = Color(0xFFEAF2EF)
    val copper = cinnabar
    val green = Color(0xFF287551)
    val ochre = Color(0xFF9D6729)
}

private data class ProjectContentItem(
    val title: String,
    val detail: String,
    val action: () -> Unit,
    val ready: Boolean
)

private val HistoryTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(color = HistoryColors.ink, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(color = HistoryColors.ink, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(color = HistoryColors.muted, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.sp)
    )
}

class MainActivity : ComponentActivity() {
    private lateinit var store: ProjectStore
    private var player: MediaPlayer? = null
    private var notificationProjectId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureStatusBar()
        store = ProjectStore(this)
        notificationProjectId = intent?.getStringExtra(EXTRA_OPEN_PROJECT)
        store.all().filter { CurrentProjectRules.inProgress(it) && it.id != GenerationService.activeProjectId }
            .forEach { project ->
                project.status = "paused"
                project.detail = "任务中断，可继续制作"
                store.save(project)
            }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            val historyScheme = lightColorScheme(
                primary = HistoryColors.cinnabar,
                onPrimary = Color.White,
                primaryContainer = HistoryColors.cinnabarSoft,
                onPrimaryContainer = HistoryColors.ink,
                secondary = HistoryColors.teal,
                onSecondary = Color.White,
                secondaryContainer = HistoryColors.tealSoft,
                onSecondaryContainer = HistoryColors.ink,
                background = HistoryColors.paper,
                surface = HistoryColors.card,
                surfaceVariant = HistoryColors.subcard,
                onSurface = HistoryColors.ink,
                onSurfaceVariant = HistoryColors.muted,
                outline = HistoryColors.line,
                error = HistoryColors.ochre
            )
            MaterialTheme(colorScheme = historyScheme, typography = HistoryTypography,
                shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
                    medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(8.dp))) {
                Surface(color = HistoryColors.paper) { App() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationProjectId = intent.getStringExtra(EXTRA_OPEN_PROJECT)
    }

    companion object {
        const val EXTRA_OPEN_PROJECT = "open_project_id"
    }

    @Suppress("DEPRECATION")
    private fun configureStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = android.graphics.Color.WHITE
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun start(project: VideoProject, action: String, shotId: Int = 0, prompt: String = "") {
        requestNotificationPermissionIfNeeded()
        val intent = Intent(this, GenerationService::class.java).setAction(action)
            .putExtra(GenerationService.EXTRA_PROJECT, project.id)
            .putExtra(GenerationService.EXTRA_SHOT, shotId)
            .putExtra(GenerationService.EXTRA_PROMPT, prompt)
        if (action == GenerationService.IMAGE || action == GenerationService.IMAGE_OPTIMIZE) {
            val shot = project.shots.firstOrNull { it.id == shotId } ?: error("镜头不存在")
            intent.putExtra(GenerationService.EXTRA_SHOT_IMAGE_PROMPT, shot.imagePrompt)
        }
        startForegroundService(intent)
    }

    private fun play(project: VideoProject, shot: Shot): String {
        val file = store.audio(project, shot)
        require(shot.audioStatus == "done" && file.isFile && file.length() > 1000) {
            "第 ${project.shotNumber(shot.id)} 段配音尚未生成，请先完成配音"
        }
        return playFile(file, "正在试听第 ${project.shotNumber(shot.id)} 段配音")
    }

    private fun playFile(file: File, message: String): String {
        require(file.isFile && file.length() > 1000) { "音频文件不存在或为空" }
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { it.start() }
            setOnCompletionListener { completed ->
                completed.release()
                if (player === completed) player = null
            }
            setOnErrorListener { failed, _, _ ->
                failed.release()
                if (player === failed) player = null
                true
            }
            prepareAsync()
        }
        return message
    }

    private fun buildVoicePreview(project: VideoProject): File {
        val output = File(store.directory(project.id), "temp/voice_preview.m4a")
        return VideoRenderer(store).concatAudio(project, output)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun share(project: VideoProject): String {
        val file = store.output(project)
        require(file.isFile) { "成片尚未生成" }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享视频"))
        return "已打开分享面板"
    }

    private fun playVideo(project: VideoProject): String {
        val file = store.output(project)
        require(file.isFile) { "成片尚未生成" }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        return "正在打开成片"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun App() {
        val settings = remember { AppSettings(this) }
        val appVersion = remember {
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                .getOrNull()
                ?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }
                ?: "v未知"
        }
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        var navigation by rememberSaveable { mutableStateOf(listOf("home")) }
        var selectedId by rememberSaveable { mutableStateOf("") }
        var selectedShotId by rememberSaveable { mutableStateOf(0) }
        var selectedCostCategory by rememberSaveable(selectedId) { mutableStateOf("total") }
        var selectedPromptType by rememberSaveable { mutableStateOf("script") }
        var historySearch by rememberSaveable { mutableStateOf(false) }
        val pageStates = rememberSaveableStateHolder()
        var projects by remember { mutableStateOf(store.all()) }
        var preferredCurrentId by remember { mutableStateOf(store.currentProjectId()) }
        var message by remember { mutableStateOf("") }
        var exportingAsset by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<VideoProject?>(null) }
        var noBgmRenderProjectId by rememberSaveable { mutableStateOf("") }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        val screen = when (val route = navigation.last()) {
            "topics", "topic_editor" -> "script"
            else -> route
        }
        val pageKey = when (screen) {
            "project", "script", "shots", "effects", "result", "costs" -> "$screen:$selectedId"
            "shot" -> "$screen:$selectedId:$selectedShotId"
            "prompt_editor" -> "$screen:$selectedPromptType"
            else -> screen
        }

        fun navigate(to: String) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            navigation = navigation + to
            message = ""
        }

        fun newProject() {
            val project = store.create("")
            store.markCurrentProject(project.id)
            preferredCurrentId = project.id
            projects = store.all()
            selectedId = project.id
            navigate("project")
        }

        fun goBack() {
            if (navigation.size > 1) {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                player?.release(); player = null
                navigation = navigation.dropLast(1)
                message = ""
            }
        }

        BackHandler(enabled = navigation.size > 1) { goBack() }
        LaunchedEffect(screen) { message = "" }
        LaunchedEffect(Unit) {
            // Restore old topic routes into the combined writing page after an app update.
            navigation = navigation.map { if (it == "topics" || it == "topic_editor") "script" else it }
                .fold(emptyList<String>()) { routes, route ->
                    if (routes.lastOrNull() == route) routes else routes + route
                }
            while (true) {
                projects = store.all()
                preferredCurrentId = store.currentProjectId()
                delay(1200)
            }
        }
        val selected = projects.firstOrNull { it.id == selectedId }
        val runningProject = CurrentProjectRules.running(projects)
        val currentProject = CurrentProjectRules.current(projects, preferredCurrentId)
        LaunchedEffect(runningProject?.id, runningProject?.status, runningProject?.taskStartedAt) {
            if (runningProject != null) {
                while (CurrentProjectRules.inProgress(runningProject)) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
        }

        LaunchedEffect(notificationProjectId) {
            val id = notificationProjectId ?: return@LaunchedEffect
            val project = runCatching { store.get(id) }.getOrNull()
            notificationProjectId = null
            if (project != null) {
                store.markCurrentProject(project.id)
                preferredCurrentId = project.id
                projects = store.all()
                selectedId = project.id
                focusManager.clearFocus(force = true); keyboard?.hide()
                message = ""
                navigation = listOf("home", CurrentProjectRules.resumeScreen(project))
            }
        }

        fun saveProject(project: VideoProject) {
            store.save(project)
            projects = store.all()
        }

        fun launchProjectTask(project: VideoProject, action: String, shotId: Int = 0) {
            val promptName = when (action) {
                GenerationService.SCRIPT -> "script"
                GenerationService.SHOTS -> "shots"
                GenerationService.IMAGE_ALL, GenerationService.IMAGE -> "image"
                GenerationService.IMAGE_OPTIMIZE -> "image_failure"
                else -> null
            }
            val prompt = promptName?.let { settings.prompt(it) }.orEmpty()
            runCatching {
                start(project, action, shotId, prompt)
                store.markCurrentProject(project.id)
                preferredCurrentId = project.id
                projects = store.all()
            }.onFailure { error ->
                message = error.message.orEmpty()
            }
        }

        fun runProject(project: VideoProject, action: String, shotId: Int = 0) {
            if (action == GenerationService.RENDER && (project.bgmId.isBlank() || project.bgmId == "none")) {
                noBgmRenderProjectId = project.id
                return
            }
            launchProjectTask(project, action, shotId)
        }

        fun exportProject(project: VideoProject, cover: Boolean = false) {
            if (exportingAsset) return
            exportingAsset = true
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { AlbumExporter(store).save(project, cover) } }
                message = result.getOrElse { "保存失败：${it.message ?: it.javaClass.simpleName}" }
                exportingAsset = false
            }
        }

        fun playShot(project: VideoProject, shot: Shot) {
            message = "正在混合本镜配音和音效…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = File(store.directory(project.id), "temp/shot_${shot.id}_preview.m4a")
                        VideoRenderer(store).previewShotAudio(project, shot.id, output)
                    }
                }
                result.onSuccess { file ->
                    runCatching { playFile(file, "正在试听第 ${project.shotNumber(shot.id)} 镜配音与音效") }
                        .onFailure { message = it.message.orEmpty() }
                }.onFailure { message = it.message.orEmpty() }
            }
        }

        fun playVoice(project: VideoProject) {
            message = "正在合并配音，请稍候..."
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { buildVoicePreview(project) }
                }
                result.onSuccess { file ->
                    runCatching { playFile(file, "正在试听配音、BGM和镜头音效") }
                        .onSuccess { message = it }
                        .onFailure { message = it.message.orEmpty() }
                }.onFailure { error -> message = error.message.orEmpty() }
            }
        }

        Scaffold(
            containerColor = HistoryColors.paper,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            topBar = {
                Column {
                    // Paint behind the transparent status bar on Android 15 and newer.
                    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.White))
                    TopAppBar(
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = HistoryColors.paper,
                            titleContentColor = HistoryColors.ink,
                            navigationIconContentColor = HistoryColors.cinnabar,
                            actionIconContentColor = HistoryColors.cinnabar
                        ),
                        title = {
                            if (screen == "home") {
                                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    Text("史镜 · 图文创作台", style = MaterialTheme.typography.headlineSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("AI 短剧成片 · $appVersion", style = MaterialTheme.typography.labelLarge,
                                        color = HistoryColors.muted)
                                }
                            } else Text(when (screen) {
                                "project" -> "制作此卷"
                                "script" -> "成文"
                                "shots" -> "生图"
                                "shot" -> "镜头详情"
                                "effects" -> "画面与章法"
                                "history" -> "旧作存档"
                                "api" -> "模型与接口"
                                "prompts" -> "提示词卷库"
                                "prompt_editor" -> "提示词版本"
                                "settings" -> "设置"
                                "audio_library" -> "音频库"
                                "cutout_models" -> "抠图模型"
                                "result" -> "成片结果"
                                "costs" -> "费用明细"
                                else -> "史镜 · 图文创作台（$appVersion）"
                            }, style = MaterialTheme.typography.titleLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }, navigationIcon = {
                            if (navigation.size > 1) {
                                IconButton(onClick = { goBack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        }, actions = {
                            if (screen == "home") {
                                Surface(onClick = { navigate("settings") }, shape = RoundedCornerShape(18.dp),
                                    color = HistoryColors.card, border = BorderStroke(1.dp, HistoryColors.line),
                                    modifier = Modifier.padding(end = 12.dp).size(52.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = HistoryColors.cinnabar,
                                            modifier = Modifier.size(26.dp))
                                    }
                                }
                            }
                            if (screen == "history") {
                                IconButton(onClick = { historySearch = !historySearch }) {
                                    Icon(if (historySearch) Icons.Filled.Close else Icons.Filled.Search,
                                        contentDescription = if (historySearch) "关闭搜索" else "搜索项目")
                                }
                                Button(onClick = ::newProject, shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 12.dp)) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("新建")
                                }
                            }
                            if (screen == "project" && selected != null) {
                                OutlinedButton(onClick = { saveProject(selected); message = "草稿已保存" },
                                    enabled = selected.status != "running", shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(end = 12.dp)) { Text("保存草稿") }
                            }
                            if ((screen == "script" || screen == "effects") && selected != null) {
                                OutlinedButton(onClick = { saveProject(selected); message = "草稿已保存" },
                                    enabled = selected.status != "running", shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.padding(end = 10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                                    Text(if (screen == "script") "保存草稿" else "保存")
                                }
                            }
                            if (screen == "shot" && selected != null) {
                                val shotIndex = selected.shots.indexOfFirst { it.id == selectedShotId }
                                IconButton(onClick = { selectedShotId = selected.shots[shotIndex - 1].id },
                                    enabled = selected.status != "running" && shotIndex > 0) {
                                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一镜头")
                                }
                                Text("${shotIndex + 1}/${selected.shots.size}", style = MaterialTheme.typography.labelMedium)
                                IconButton(onClick = { selectedShotId = selected.shots[shotIndex + 1].id },
                                    enabled = selected.status != "running" && shotIndex >= 0 && shotIndex < selected.shots.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一镜头")
                                }
                            }
                        })
                }
        }) { inset ->
            key(pageKey) {
            pageStates.SaveableStateProvider(pageKey) {
            Column(
                Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).imePadding()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = HistoryColors.tealSoft)
                    ) {
                        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = HistoryColors.teal, style = MaterialTheme.typography.bodySmall)
                    }
                }
                when (screen) {
                    "home" -> {
                        HomeScreen(currentProject, runningProject, now, store,
                            projects.filter { it.title.isNotBlank() },
                            onNew = ::newProject, onHistory = { navigate("history") }, onCurrent = {
                                currentProject?.let {
                                    selectedId = it.id
                                    store.markCurrentProject(it.id); preferredCurrentId = it.id
                                    navigate(CurrentProjectRules.resumeScreen(it))
                                }
                            }, onRecent = { recent ->
                                selectedId = recent.id
                                store.markCurrentProject(recent.id); preferredCurrentId = recent.id
                                navigate("project")
                            })
                    }

                    "history" -> {
                        HistoryScreen(projects.filter { it.title.isNotBlank() }, store, historySearch,
                            onContinue = {
                                selectedId = it.id
                                store.markCurrentProject(it.id); preferredCurrentId = it.id
                                navigate("project")
                            },
                            onCosts = { selectedId = it.id; selectedCostCategory = "total"; navigate("costs") },
                            onResults = { selectedId = it.id; navigate("result") },
                            onDelete = { deleteTarget = it },
                            onPlay = { project ->
                                runCatching { playVideo(project) }.onSuccess { message = it }
                                    .onFailure { message = it.message.orEmpty() }
                            }, onShare = { project ->
                                runCatching { share(project) }.onSuccess { message = it }
                                    .onFailure { message = it.message.orEmpty() }
                            })
                    }

                    "api" -> ApiSettingsScreen(settings) { message = it }
                    "prompts" -> PromptListScreen(settings, onOpen = { selectedPromptType = it; navigate("prompt_editor") })
                    "prompt_editor" -> PromptEditorScreen(settings, selectedPromptType) { message = it }
                    "settings" -> SettingsScreen(onApi = { navigate("api") }, onPrompts = { navigate("prompts") },
                        onModels = { navigate("cutout_models") }, onAudio = { navigate("audio_library") })
                    "audio_library" -> AudioLibrarySettingsScreen(store, projects,
                        busy = projects.any { it.status == "running" }, onMessage = { message = it })
                    "cutout_models" -> CutoutModelSettingsScreen(busy = projects.any { it.status == "running" })
                    "project" -> if (selected != null) ProjectOverviewScreen(
                        selected,
                        store = store,
                        onOpenScript = { navigate("script") },
                        onOpenShots = { navigate("shots") },
                        onOpenEffects = { navigate("effects") },
                        onRender = { runProject(selected, GenerationService.RENDER, 0) },
                        hasOutput = store.output(selected).length() > 1000,
                        onOpenResults = { navigate("result") },
                        onOpenCosts = { selectedCostCategory = "total"; navigate("costs") },
                        onFilterCosts = { selectedCostCategory = it; navigate("costs") },
                        onCancel = { startService(Intent(this@MainActivity, GenerationService::class.java).setAction(GenerationService.CANCEL)) }
                    )
                    "costs" -> if (selected != null) ProjectCostDetailsScreen(selected, store,
                        category = selectedCostCategory, onSelectCategory = { selectedCostCategory = it })
                    "result" -> if (selected != null) CompletedResultScreen(selected, store,
                        exporting = exportingAsset,
                        onSaveVideo = { exportProject(selected) }, onSaveCover = { exportProject(selected, cover = true) },
                        onBack = ::goBack)
                    "script" -> if (selected != null) ScriptScreen(
                        selected,
                        store,
                        onSave = ::saveProject,
                        onRun = ::runProject
                    )
                    "shots" -> if (selected != null) ShotsScreen(
                        selected,
                        store,
                        onSave = ::saveProject,
                        onRun = ::runProject,
                        onPlay = ::playShot,
                        onPlayVoice = ::playVoice,
                        onOpenShot = { shotId -> selectedShotId = shotId; navigate("shot") },
                        onExport = { exportProject(it) }
                    )
                    "shot" -> if (selected != null) {
                        selected.shots.firstOrNull { it.id == selectedShotId }?.let { shot ->
                            ShotDetailScreen(selected, shot, store, selected.status == "running",
                                onSave = ::saveProject, onRun = ::runProject, onPlay = ::playShot,
                                onNavigate = { nextId ->
                                    if (selected.shots.any { it.id == nextId }) selectedShotId = nextId
                                }, onInsert = { newText, newPrompt ->
                                    runCatching {
                                        val updated = VideoProject.from(selected.json())
                                        val inserted = store.insertShotAfter(updated, shot.id, newText, newPrompt)
                                        saveProject(updated)
                                        selectedShotId = inserted.id
                                        message = "已插入新镜头，原图片和配音已保留"
                                    }.onFailure { message = it.message.orEmpty() }.isSuccess
                                }, onMotionChange = { setting ->
                                    runCatching {
                                        val updated = VideoProject.from(selected.json())
                                        ShotMotionRules.setShot(updated, shot.id, setting)
                                        saveProject(updated)
                                        message = "镜头 ${updated.shotNumber(shot.id)} 的运动已保存，重新渲染后生效"
                                    }.onFailure { message = it.message.orEmpty() }
                                }, onSoundEffectsChange = { effects ->
                                    val updated = VideoProject.from(selected.json())
                                    updated.shots.firstOrNull { it.id == shot.id }?.soundEffects = effects.toMutableList()
                                    updated.renderRevision = java.util.UUID.randomUUID().toString()
                                    saveProject(updated)
                                    message = "本镜音效已保存，试听或重新渲染后生效"
                                }, onCutoutChange = { enabled ->
                                    runCatching {
                                        if (enabled) CutoutProcessor(store).requireReady()
                                        val updated = VideoProject.from(selected.json())
                                        ShotMotionRules.setCutout(updated, shot.id, enabled)
                                        saveProject(updated)
                                        message = if (enabled) "本镜抠图已开启，普通运动已清除" else "本镜抠图已取消"
                                    }.onFailure { message = it.message.orEmpty() }
                                })
                        }
                    }
                    "effects" -> if (selected != null) EffectsScreen(selected, ::saveProject)
                }
            }
            }
            }
        }
        deleteTarget?.let { project ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("删除历史项目？") },
                text = { Text("确定删除“${project.title}”吗？删除后项目中的图片、配音和视频也会一并删除。") },
                confirmButton = {
                    TextButton(onClick = {
                        store.delete(project.id)
                        projects = store.all()
                        if (selectedId == project.id) selectedId = ""
                        deleteTarget = null
                        message = "项目已删除"
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                }
            )
        }
        if (noBgmRenderProjectId.isNotBlank()) {
            val projectId = noBgmRenderProjectId
            val renderProject = projects.firstOrNull { it.id == projectId }
            AlertDialog(
                onDismissRequest = { noBgmRenderProjectId = "" },
                title = { Text("尚未选择背景音乐") },
                text = { Text("本卷没有选择 BGM，继续渲染后成片将不含背景音乐。") },
                confirmButton = {
                    TextButton(onClick = {
                        noBgmRenderProjectId = ""
                        renderProject?.let { launchProjectTask(it, GenerationService.RENDER) }
                    }) { Text("仍然渲染") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            noBgmRenderProjectId = ""
                            if (renderProject != null) {
                                selectedId = renderProject.id
                                navigate("effects")
                            }
                        }) { Text("去选择 BGM") }
                        TextButton(onClick = { noBgmRenderProjectId = "" }) { Text("取消") }
                    }
                }
            )
        }
    }
}

private fun imageSizeLabel(value: String) = when (value) {
    "1024x1024" -> "1:1 正方形"
    "1792x1024" -> "16:9 横屏"
    else -> "9:16 竖屏"
}

private fun projectStatusLabel(status: String) = when (status) {
    "done" -> "已完成"
    "running" -> "进行中"
    "review" -> "待确认"
    "paused" -> "已暂停"
    "failed" -> "失败"
    else -> "草稿"
}

private fun videoDurationLabel(duration: Double): String {
    val seconds = duration.toInt().coerceAtLeast(0)
    return "${seconds / 60}分${seconds % 60}秒"
}

@Composable
private fun HistoryScreen(projects: List<VideoProject>, store: ProjectStore, searchVisible: Boolean,
                          onContinue: (VideoProject) -> Unit, onCosts: (VideoProject) -> Unit,
                          onResults: (VideoProject) -> Unit, onDelete: (VideoProject) -> Unit,
                          onPlay: (VideoProject) -> Unit, onShare: (VideoProject) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    if (searchVisible) {
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索项目") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("all" to "全部", "done" to "已完成", "working" to "制作中", "draft" to "草稿").forEach { (id, label) ->
            Column(Modifier.weight(1f).selectable(filter == id, role = Role.Tab, onClick = { filter = id }),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelLarge,
                    color = if (filter == id) HistoryColors.cinnabar else HistoryColors.muted)
                Box(Modifier.width(28.dp).height(3.dp).background(
                    if (filter == id) HistoryColors.cinnabar else Color.Transparent, RoundedCornerShape(2.dp)))
            }
        }
    }
    val visible = projects.filter { project ->
        (!searchVisible || project.title.contains(query.trim(), ignoreCase = true)) && when (filter) {
            "done" -> project.status == "done"
            "draft" -> project.status == "draft"
            "working" -> project.status != "done" && project.status != "draft"
            else -> true
        }
    }
    if (visible.isEmpty()) {
        Text(if (projects.isEmpty()) "尚无旧作" else "暂无符合条件的项目",
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp), textAlign = TextAlign.Center,
            color = HistoryColors.muted)
    }
    visible.forEach { project ->
        key(project.id) {
            var more by remember { mutableStateOf(false) }
            val video = store.output(project)
            val hasVideo = video.length() > 1000
            val cover = store.cover(project)
            val thumbnail = if (cover.isFile) cover else project.shots.firstOrNull()?.let { store.image(project, it) }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(88.dp).height(110.dp).clip(RoundedCornerShape(8.dp))
                            .background(HistoryColors.subcard).clickable { if (hasVideo) onResults(project) else onContinue(project) }) {
                            if (thumbnail != null) ShotThumbnail(thumbnail, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(painterResource(R.drawable.ic_image), contentDescription = null,
                                modifier = Modifier.align(Alignment.Center), tint = HistoryColors.muted)
                            if (hasVideo) Text(videoDurationLabel(project.duration), modifier = Modifier.align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topStart = 6.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                                color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(project.title, modifier = Modifier.weight(1f), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Box {
                                    IconButton(onClick = { more = true }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作", tint = HistoryColors.muted)
                                    }
                                    DropdownMenu(more, onDismissRequest = { more = false }) {
                                        DropdownMenuItem(text = { Text("费用明细") }, onClick = { more = false; onCosts(project) })
                                        DropdownMenuItem(text = { Text("成片与封面") }, enabled = hasVideo,
                                            onClick = { more = false; onResults(project) })
                                        DropdownMenuItem(text = { Text("删除项目", color = HistoryColors.cinnabar) },
                                            enabled = project.status != "running", onClick = { more = false; onDelete(project) })
                                    }
                                }
                            }
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(project.createdAt)),
                                style = MaterialTheme.typography.labelSmall, color = HistoryColors.muted)
                            Text(projectStatusLabel(project.status), style = MaterialTheme.typography.labelMedium,
                                color = if (project.status == "done") HistoryColors.green else if (project.status == "running")
                                    HistoryColors.cinnabar else HistoryColors.muted)
                            Text("${videoDurationLabel(project.duration)} · ${project.shots.size} 镜 · ${video.length() / 1_048_576} MB",
                                style = MaterialTheme.typography.labelSmall, color = HistoryColors.muted)
                        }
                    }
                    ProjectCostSummary(project, store, framed = false, compact = true, onOpenDetails = { onCosts(project) })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onContinue(project) }, modifier = Modifier.weight(1.4f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), shape = RoundedCornerShape(8.dp)) {
                            Text("继续制作", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { onPlay(project) }, enabled = hasVideo, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("播放", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { onShare(project) }, enabled = hasVideo, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("分享", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(current: VideoProject?, running: VideoProject?, now: Long,
                       store: ProjectStore, recentProjects: List<VideoProject>,
                       onNew: () -> Unit, onHistory: () -> Unit, onCurrent: () -> Unit,
                       onRecent: (VideoProject) -> Unit) {
    val task = CurrentProjectRules.taskLabel(running ?: current)
    val active = running ?: current
    val progress = active?.takeIf { it.total > 0 }?.let {
        (it.completed.toFloat() / it.total.toFloat()).coerceIn(0f, 1f)
    }
    Card(onClick = onCurrent, enabled = current != null, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, HistoryColors.line),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("当前项目", modifier = Modifier.weight(1f), color = HistoryColors.muted,
                    style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(14.dp), color = if (running != null) HistoryColors.cinnabarSoft else HistoryColors.tealSoft) {
                    Text(if (running != null) "制作中" else if (current?.status == "done") "已完成" else "待继续",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (running != null) HistoryColors.cinnabar else HistoryColors.teal,
                        fontWeight = FontWeight.Bold)
                }
            }
            Text(current?.title?.takeIf { it.isNotBlank() } ?: "尚未建立项目",
                style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val elapsed = active?.taskStartedAt?.takeIf { it > 0L }?.let { " · 已执行 ${elapsedLabel(it, now)}" }.orEmpty()
            Text("当前任务 · $task$elapsed", color = HistoryColors.muted, style = MaterialTheme.typography.bodyLarge)
            if (progress != null && active != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (active.stage == "render" || active.status == "done") "合成进度" else "制作进度",
                        style = MaterialTheme.typography.titleSmall, color = HistoryColors.muted)
                    Text("${active.completed}/${active.total} · ${(progress * 100).toInt()}%",
                        color = HistoryColors.cinnabar, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = HistoryColors.cinnabar, trackColor = HistoryColors.subcard)
            }
            Button(onClick = onCurrent, enabled = current != null, modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) {
                Text(if (running != null) "继续制片" else "继续制作", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
    Text("快速入口", style = MaterialTheme.typography.headlineSmall, color = HistoryColors.ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(onClick = onNew, modifier = Modifier.weight(1f).height(174.dp), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, HistoryColors.line),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).background(HistoryColors.cinnabarSoft, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = HistoryColors.cinnabar, modifier = Modifier.size(30.dp))
                }
                Text("新建一卷", style = MaterialTheme.typography.titleLarge)
                Text("从题材、文案到成片", color = HistoryColors.muted)
            }
        }
        Card(onClick = onHistory, modifier = Modifier.weight(1f).height(174.dp), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, HistoryColors.line),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).background(HistoryColors.subcard, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = HistoryColors.muted, modifier = Modifier.size(28.dp))
                }
                Text("旧作存档", style = MaterialTheme.typography.titleLarge)
                Text("继续编辑已有项目", color = HistoryColors.muted)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("最近创作", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        Text("共 ${recentProjects.size} 卷", color = HistoryColors.muted, style = MaterialTheme.typography.titleMedium)
    }
    val recent = recentProjects.take(2)
    if (recent.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, HistoryColors.line), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Text("还没有历史项目", modifier = Modifier.padding(20.dp), color = HistoryColors.muted)
        }
    } else Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, HistoryColors.line), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column {
            recent.forEachIndexed { index, project ->
                Row(Modifier.fillMaxWidth().clickable { onRecent(project) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val firstShot = project.shots.firstOrNull()
                    if (firstShot != null) ShotThumbnail(store.image(project, firstShot),
                        Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(68.dp).background(HistoryColors.subcard, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = HistoryColors.muted)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.createdAt))} · ${projectStatusLabel(project.status)}",
                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = HistoryColors.line)
                }
                if (index < recent.lastIndex) HorizontalDivider(color = HistoryColors.line)
            }
        }
    }
}

@Composable
private fun SettingsScreen(onApi: () -> Unit, onPrompts: () -> Unit, onModels: () -> Unit, onAudio: () -> Unit) {
    Text("工作台设置", style = MaterialTheme.typography.headlineSmall)
    Card(onClick = onApi, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("模型与接口", style = MaterialTheme.typography.titleLarge)
            Text("文本、图片、豆包语音接口和密钥", color = HistoryColors.muted)
        }
    }
    Card(onClick = onPrompts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("提示词卷库", style = MaterialTheme.typography.titleLarge)
            Text("按文案、分镜和生图提示词分别编辑", color = HistoryColors.muted)
        }
    }
    Card(onClick = onModels, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("抠图模型", style = MaterialTheme.typography.titleLarge)
            Text("人物分割模型、背景修复模型", color = HistoryColors.muted)
        }
    }
    Card(onClick = onAudio, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("音频库", style = MaterialTheme.typography.titleLarge)
            Text("导入和管理 BGM、镜头音效", color = HistoryColors.muted)
        }
    }
}

@Composable
private fun AudioLibrarySettingsScreen(store: ProjectStore, projects: List<VideoProject>, busy: Boolean,
                                       onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember(context) { AudioLibrary(context) }
    var kind by rememberSaveable { mutableStateOf(AudioKind.BGM) }
    var revision by remember { mutableStateOf(0) }
    var renameAsset by remember { mutableStateOf<AudioAsset?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var importingKind by remember { mutableStateOf(AudioKind.BGM) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { library.importAudio(uri, importingKind) } }
            result.onSuccess { asset -> revision++; onMessage("已导入：${asset.name}") }
                .onFailure { error -> onMessage("导入失败：${error.message ?: error.javaClass.simpleName}") }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewJob?.cancel()
            player?.release()
        }
    }
    fun stopPreview() {
        previewJob?.cancel(); previewJob = null
        player?.release(); player = null
    }
    fun preview(asset: AudioAsset) {
        stopPreview()
        val file = library.resolve(asset.id)
        if (file == null) {
            onMessage("音频文件不存在：${asset.name}")
            return
        }
        runCatching {
            val media = MediaPlayer()
            player = media
            media.setDataSource(file.absolutePath)
            media.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    prepared.start()
                    previewJob = scope.launch {
                        delay(3000)
                        if (player === prepared) stopPreview()
                    }
                }
            }
            media.setOnCompletionListener { completed -> if (player === completed) stopPreview() }
            media.setOnErrorListener { failed, _, _ ->
                if (player === failed) stopPreview()
                onMessage("音频试听失败：${asset.name}")
                true
            }
            media.prepareAsync()
        }.onFailure {
            stopPreview()
            onMessage("音频试听失败：${it.message ?: it.javaClass.simpleName}")
        }
    }
    Text("音频库", style = MaterialTheme.typography.headlineSmall)
    Text("导入的音频保存在应用私有目录，可在制作项目时选择。试听播放前 3 秒。",
        style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(AudioKind.BGM, AudioKind.SFX).forEach { option ->
            if (option == kind) Button(onClick = { kind = option }, modifier = Modifier.weight(1f)) {
                Text(option.label)
            } else OutlinedButton(onClick = { kind = option }, modifier = Modifier.weight(1f)) {
                Text(option.label)
            }
        }
    }
    Button(onClick = {
        importingKind = kind
        picker.launch(arrayOf("audio/*"))
    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (kind == AudioKind.BGM) "导入背景音乐" else "导入音效")
    }
    val assets = remember(kind, revision) { library.assets(kind) }
    if (assets.isEmpty()) Text("暂无音频", color = HistoryColors.muted)
    assets.forEach { asset ->
        val inUse = library.isUsed(asset.id, projects)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(asset.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (asset.builtIn) "内置音频" else if (inUse) "项目正在使用" else "已导入",
                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                    }
                    OutlinedButton(onClick = { preview(asset) }, enabled = !busy) { Text("试听") }
                }
                if (!asset.builtIn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { renameAsset = asset; renameValue = asset.name }, enabled = !busy) {
                        Text("重命名")
                    }
                    TextButton(onClick = {
                        runCatching { library.delete(asset.id, projects) }
                            .onSuccess { revision++; onMessage("音频已删除") }
                            .onFailure { onMessage(it.message.orEmpty()) }
                    }, enabled = !busy && !inUse) { Text("删除") }
                }
            }
        }
    }
    renameAsset?.let { asset ->
        AlertDialog(onDismissRequest = { renameAsset = null }, title = { Text("重命名音频") },
            text = { OutlinedTextField(renameValue, { renameValue = it.take(80) }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { library.rename(asset.id, renameValue) }
                        .onSuccess { revision++; onMessage("音频名称已更新"); renameAsset = null }
                        .onFailure { onMessage(it.message.orEmpty()) }
                }) { Text("保存") }
            }, dismissButton = { TextButton(onClick = { renameAsset = null }) { Text("取消") } })
    }
}

@Composable
private fun CutoutModelSettingsScreen(busy: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val models = remember(context) { CutoutModels(context) }
    var modelToImport by remember { mutableStateOf<CutoutModel?>(null) }
    var importingModel by remember { mutableStateOf<CutoutModel?>(null) }
    var importMessage by remember { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var modelStateVersion by remember { mutableStateOf(0) }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val selectedModel = modelToImport
        modelToImport = null
        if (uri != null && selectedModel != null) {
            importingModel = selectedModel
            importFailed = false
            importMessage = "正在导入${selectedModel.label}"
            scope.launch {
                try {
                    models.importModel(selectedModel, uri) { detail ->
                        scope.launch(Dispatchers.Main) { importMessage = detail }
                    }
                    importMessage = "${selectedModel.label}已导入"
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    importFailed = true
                    importMessage = error.message ?: "模型导入失败"
                } finally {
                    modelStateVersion++
                    importingModel = null
                }
            }
        }
    }
    val imported = remember(modelStateVersion) { CutoutModel.values().filter(models::isImported).toSet() }
    CutoutModel.values().forEach { model ->
        val ready = model in imported
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card),
            border = BorderStroke(1.dp, HistoryColors.line)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(model.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(if (ready) "已导入" else "未导入", style = MaterialTheme.typography.labelMedium,
                        color = if (ready) HistoryColors.green else HistoryColors.muted)
                }
                Text(model.filename, style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                OutlinedButton(onClick = {
                    modelToImport = model
                    modelPicker.launch(arrayOf("*/*"))
                }, enabled = !busy && importingModel == null && modelToImport == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (importingModel == model) "正在导入" else if (ready) "重新导入" else "导入模型")
                }
            }
        }
    }
    if (importingModel != null) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (importMessage.isNotBlank()) Text(importMessage, style = MaterialTheme.typography.bodySmall,
        color = if (importFailed) HistoryColors.ochre else HistoryColors.green)
    if (busy) Text("任务结束后可导入模型", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
}

private fun elapsedLabel(startedAt: Long, now: Long): String {
    val seconds = ((now - startedAt).coerceAtLeast(0L) / 1000L)
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remain = seconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remain)
    else "%d:%02d".format(minutes, remain)
}

private fun limitCodePoints(value: String, limit: Int): String {
    val count = value.codePointCount(0, value.length)
    return if (count <= limit) value else value.substring(0, value.offsetByCodePoints(0, limit))
}

private fun stageLabel(stage: String) = when (stage) {
    "images" -> "生图"
    "tts" -> "配音"
    "render" -> "渲染"
    "script" -> "文案"
    "shots" -> "分镜"
    "topic" -> "成文"
    else -> "任务"
}

@Composable
private fun ApiInputField(label: String, value: String, onValueChange: (String) -> Unit,
                          enabled: Boolean, secret: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
        OutlinedTextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(8.dp), textStyle = MaterialTheme.typography.bodyLarge,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HistoryColors.card,
                unfocusedContainerColor = HistoryColors.card,
                focusedBorderColor = HistoryColors.cinnabar,
                unfocusedBorderColor = HistoryColors.line,
                cursorColor = HistoryColors.cinnabar
            )
        )
    }
}

@Composable
private fun ApiChoiceField(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
        Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp), color = HistoryColors.card,
            border = BorderStroke(1.dp, HistoryColors.line)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun ApiSettingsScreen(settings: AppSettings, onMessage: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("text") }
    var speed by rememberSaveable { mutableStateOf(settings.speed.toString()) }
    var concurrency by rememberSaveable { mutableStateOf(settings.concurrency.toString()) }
    var imageUnitPrice by rememberSaveable { mutableStateOf(settings.imageUnitPrice) }
    var testing by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(HistoryColors.subcard).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("text" to "文本接口", "image" to "图片接口", "tts" to "配音参数").forEach { (id, label) ->
            Surface(
                modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp))
                    .selectable(selected = selected == id, enabled = !testing, role = Role.Tab, onClick = { selected = id }),
                shape = RoundedCornerShape(6.dp),
                color = if (selected == id) HistoryColors.card else Color.Transparent,
                shadowElevation = if (selected == id) 1.dp else 0.dp
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == id) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected == id) HistoryColors.cinnabar else HistoryColors.muted,
                        maxLines = 1)
                }
            }
        }
    }
    key(selected) {
        val initial = settings.provider(selected)
        var interfaceType by rememberSaveable { mutableStateOf(initial.name) }
        var url by rememberSaveable { mutableStateOf(initial.baseUrl) }
        var model by rememberSaveable { mutableStateOf(initial.model) }
        var apiKey by remember { mutableStateOf("") }
        var groupId by rememberSaveable { mutableStateOf(initial.groupId) }
        var imageSize by rememberSaveable { mutableStateOf(initial.imageSize.ifBlank { "1024x1792" }) }
        var voiceId by rememberSaveable { mutableStateOf(settings.voiceId) }
        var imageSizeMenu by remember { mutableStateOf(false) }
        var hasSavedKey by remember { mutableStateOf(settings.hasApiKey(selected)) }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selected == "tts") {
                    ApiInputField("配音服务", interfaceType, { interfaceType = it }, enabled = !testing)
                    ApiInputField("接口地址", url, { url = it }, enabled = !testing)
                    ApiInputField("API Key", apiKey, { apiKey = it }, enabled = !testing, secret = true)
                    if (hasSavedKey) Text("密钥已保存", style = MaterialTheme.typography.bodySmall, color = HistoryColors.green)
                    ApiInputField("Group ID", groupId, { groupId = it }, enabled = !testing)
                    ApiInputField("模型 / Resource ID", model, { model = it }, enabled = !testing)
                    ApiInputField("音色 / 说话人 ID", voiceId, { voiceId = it }, enabled = !testing)
                    ApiInputField("语速 0.5～2.0", speed, { speed = it }, enabled = !testing)
                } else {
                    ApiInputField("接口地址", url, { url = it }, enabled = !testing)
                    ApiInputField("接口类型", interfaceType, { interfaceType = it }, enabled = !testing)
                    ApiInputField("模型", model, { model = it }, enabled = !testing)
                    ApiInputField("密钥", apiKey, { apiKey = it }, enabled = !testing, secret = true)
                    if (hasSavedKey) Text("密钥已保存", style = MaterialTheme.typography.bodySmall, color = HistoryColors.green)
                    if (selected == "image") {
                        Box(Modifier.fillMaxWidth()) {
                            ApiChoiceField("图片尺寸", imageSizeLabel(imageSize), enabled = !testing,
                                onClick = { imageSizeMenu = true })
                            DropdownMenu(expanded = imageSizeMenu, onDismissRequest = { imageSizeMenu = false }) {
                                listOf("1024x1792" to "9:16 竖屏", "1024x1024" to "1:1 正方形", "1792x1024" to "16:9 横屏")
                                    .forEach { (value, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            imageSize = value
                                            imageSizeMenu = false
                                        })
                                    }
                            }
                        }
                        ApiInputField("生图异步数 1～10", concurrency, { concurrency = it }, enabled = !testing)
                        ApiInputField("每张图片成本（元）", imageUnitPrice,
                            { imageUnitPrice = it }, enabled = !testing)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (selected == "image") {
                        val result = runCatching { settings.imageUnitPrice = imageUnitPrice.trim() }
                        if (result.isFailure) {
                            onMessage(result.exceptionOrNull()?.message.orEmpty())
                            return@Button
                        }
                    }
                    settings.saveProvider(selected, ProviderConfig(interfaceType, url, model, apiKey, groupId, imageSize))
                    if (selected == "tts") {
                        settings.voiceId = voiceId.trim()
                        settings.speed = (speed.toFloatOrNull() ?: 1f).coerceIn(.5f, 2f)
                    }
                    if (selected == "image") settings.concurrency = (concurrency.toIntOrNull() ?: 5).coerceIn(1, 10)
                    apiKey = ""
                    hasSavedKey = settings.hasApiKey(selected)
                    onMessage("设置已保存")
                }, enabled = !testing, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) { Text("保存设置") }

                OutlinedButton(onClick = {
                    val saved = settings.provider(selected)
                    val config = ProviderConfig(interfaceType, url, model, apiKey.ifBlank { saved.key }, groupId, imageSize)
                    val testSpeed = (speed.toFloatOrNull() ?: settings.speed).coerceIn(.5f, 2f)
                    testing = true
                    scope.launch {
                        val result = runCatching {
                            val api = CloudApi()
                            when (selected) {
                                "text" -> {
                                    withContext(Dispatchers.IO) { api.text(config, "你是接口测试助手，只回复连接成功。", "请回复：连接成功") }
                                    "文本接口连接成功"
                                }
                                "image" -> {
                                    val output = File(context.cacheDir, "api-test-image.png")
                                    try {
                                        withContext(Dispatchers.IO) { api.image(config, "一张简单的红色圆形测试图，无文字、无水印。", output) }
                                        "图片接口连接成功"
                                    } finally { output.delete() }
                                }
                                else -> {
                                    val output = File(context.cacheDir, "api-test-tts.mp3")
                                    withContext(Dispatchers.IO) { api.doubao(config, voiceId.trim(), testSpeed, "这是豆包语音接口测试。", output) }
                                    withContext(Dispatchers.Main) {
                                        mediaPlayer?.release()
                                        mediaPlayer = MediaPlayer().apply {
                                            setDataSource(output.absolutePath)
                                            setOnPreparedListener { it.start() }
                                            setOnCompletionListener { it.release(); mediaPlayer = null; output.delete() }
                                            setOnErrorListener { failed, _, _ ->
                                                failed.release(); mediaPlayer = null; output.delete()
                                                onMessage("豆包接口成功，但测试音频播放失败")
                                                true
                                            }
                                            prepareAsync()
                                        }
                                    }
                                    "豆包接口连接成功，正在播放测试语音"
                                }
                            }
                        }.getOrElse { error -> "连接失败：${error.message.orEmpty().take(160).ifBlank { "请检查配置" }}" }
                        testing = false
                        onMessage(result)
                    }
                }, enabled = !testing, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp)) {
                    Text(if (testing) "测试中..." else "测试连接")
                }
                }
        }
    }
}

@Composable
private fun PromptListScreen(settings: AppSettings, onOpen: (String) -> Unit) {
    Text("提示词卷库", style = MaterialTheme.typography.headlineSmall)
    Text("选择一套提示词进入编辑页面，恢复默认会还原内置内容。",
        style = MaterialTheme.typography.bodySmall)
    AppSettings.types.forEach { type ->
        val label = when (type) {
            "script" -> "文案提示词"
            "shots" -> "分镜提示词"
            "image_failure" -> "生图失败优化提示词"
            else -> "生图提示词"
        }
        Card(onClick = { onOpen(type) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text(settings.prompt(type).take(70).replace("\n", " "))
                Text("当前版本 V4 · 可编辑", style = MaterialTheme.typography.labelSmall, color = HistoryColors.copper)
            }
        }
    }
}

@Composable
private fun PromptEditorScreen(settings: AppSettings, type: String, onMessage: (String) -> Unit) {
    val label = when (type) {
        "script" -> "文案提示词"
        "shots" -> "分镜提示词"
        "image_failure" -> "生图失败优化提示词"
        else -> "生图提示词"
    }
    var prompt by rememberSaveable(type) { mutableStateOf(settings.prompt(type)) }
    Text(label, style = MaterialTheme.typography.headlineSmall)
    Text("生成时会直接使用这里保存的内容。", style = MaterialTheme.typography.bodySmall)
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, HistoryColors.line, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth().padding(6.dp), minLines = 6, maxLines = 12,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HistoryColors.cinnabar,
                unfocusedBorderColor = HistoryColors.line
            ))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { settings.savePrompt(type, prompt); onMessage("提示词已保存") },
            colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) { Text("保存") }
        OutlinedButton(onClick = {
            settings.resetPrompt(type)
            prompt = settings.defaultPrompt(type)
            onMessage("已恢复默认提示词")
        }) { Text("恢复默认") }
    }
}

@Composable
private fun PromptSettingsScreen(settings: AppSettings, onMessage: (String) -> Unit) {
    Text("提示词卷库", style = MaterialTheme.typography.headlineSmall)
    Text("恢复默认会还原内置提示词。")
    AppSettings.types.forEach { type ->
        key(type) {
            var prompt by remember { mutableStateOf(settings.prompt(type)) }
            Text(when (type) {
                "script" -> "文案"
                "shots" -> "分镜"
                "image_failure" -> "生图失败优化提示词"
                else -> "生图提示词"
            })
            OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    settings.savePrompt(type, prompt)
                    onMessage("提示词已保存")
                }) { Text("保存") }
                TextButton(onClick = {
                    settings.resetPrompt(type)
                    prompt = settings.defaultPrompt(type)
                    onMessage("已恢复默认提示词")
                }) { Text("恢复默认") }
            }
        }
    }
}

@Composable
private fun ShotDetailScreen(project: VideoProject, shot: Shot, store: ProjectStore, busy: Boolean,
                             onSave: (VideoProject) -> Unit,
                             onRun: (VideoProject, String, Int) -> Unit,
                             onPlay: (VideoProject, Shot) -> Unit,
                             onNavigate: (Int) -> Unit,
                             onInsert: (String, String) -> Boolean,
                             onMotionChange: (ShotMotionSetting?) -> Unit,
                             onSoundEffectsChange: (List<ShotSoundEffect>) -> Unit,
                             onCutoutChange: (Boolean) -> Unit) {
    var text by key(shot.text) { rememberSaveable { mutableStateOf(shot.text) } }
    var prompt by key(shot.imagePrompt) { rememberSaveable { mutableStateOf(shot.imagePrompt) } }
    var showImage by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(0f) }
    val imageFile = store.image(project, shot)
    val clipboard = LocalClipboardManager.current
    val shotIndex = project.shots.indexOfFirst { it.id == shot.id }
    var showInsert by rememberSaveable(project.id, shot.id) { mutableStateOf(false) }
    var insertedText by rememberSaveable(project.id, shot.id) { mutableStateOf("") }
    var insertedPrompt by rememberSaveable(project.id, shot.id) { mutableStateOf("") }
    val audioLibrary = remember(store) { AudioLibrary(store.context()) }
    var soundMenu by remember(project.id, shot.id) { mutableStateOf(false) }
    var selectedSoundEffects by remember(project.id, shot.id) { mutableStateOf(shot.soundEffects.toList()) }
    LaunchedEffect(shot.soundEffects) { selectedSoundEffects = shot.soundEffects.toList() }
    fun saveInputs(preserveImageError: Boolean = false) {
        val textChanged = shot.text != text
        val promptChanged = shot.imagePrompt != prompt
        shot.text = text; shot.imagePrompt = prompt
        if (textChanged) shot.audioStatus = "pending"
        if (promptChanged) {
            shot.imageStatus = "pending"
            if (!preserveImageError) shot.error = ""
        }
        onSave(project)
    }
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(shot.id, busy, project.shots.map { it.id }) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onHorizontalDrag = { _, amount -> dragDistance += amount },
                onDragEnd = {
                    if (!busy && abs(dragDistance) >= 100f) {
                        val nextIndex = shotIndex + if (dragDistance < 0f) 1 else -1
                        project.shots.getOrNull(nextIndex)?.let { onNavigate(it.id) }
                    }
                }
            )
        },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.02f).clip(RoundedCornerShape(26.dp)).background(HistoryColors.subcard)) {
            ShotThumbnail(imageFile, Modifier.fillMaxSize().pointerInput(imageFile.lastModified(), imageFile.length()) {
                detectTapGestures(onDoubleTap = { showImage = true })
            })
            Text("镜头 ${project.shotNumber(shot.id).toString().padStart(2, '0')}",
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge)
            Text("${"%.1f".format(shot.duration)} 秒",
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { showImage = true }, enabled = imageFile.isFile,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))) {
                Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "全屏看图", tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                shot.imageStatus == "failed" -> "图片生成失败"
                imageFile.isFile && imageFile.length() > 1000 -> "画面已生成"
                else -> "图片尚未生成"
            }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            StatusPill(imageSizeLabel(project.imageSize), if (shot.imageStatus == "failed") HistoryColors.cinnabar else HistoryColors.green)
        }
        if (shot.imageStatus == "failed" && shot.error.isNotBlank()) {
            Text("失败原因：${shot.error}", color = HistoryColors.cinnabar, style = MaterialTheme.typography.bodySmall)
        }
        Text("配音", style = MaterialTheme.typography.titleLarge)
        FormCard {
            CompactInput(text, { text = it }, enabled = !busy, minLines = 2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { saveInputs(); onRun(project, GenerationService.AUDIO, shot.id) },
                    enabled = !busy, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp)) {
                    Text("重新配此段")
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = "复制配音文字", tint = HistoryColors.muted)
                }
                IconButton(onClick = { onPlay(project, shot) }, enabled = !busy,
                    modifier = Modifier.background(HistoryColors.cinnabarSoft, CircleShape)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "试听配音和音效", tint = HistoryColors.cinnabar)
                }
            }
        }
        Text("画面设置", style = MaterialTheme.typography.titleLarge)
        ShotMotionSettingsCard(project, shot, busy, onChange = onMotionChange, onCutoutChange = onCutoutChange)
        FormCard {
            Text("本镜音效", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.fillMaxWidth()) {
                val selectedLabel = selectedSoundEffects.mapNotNull { effect -> audioLibrary.asset(effect.assetId)?.name }
                    .joinToString("、").ifBlank { "无" }
                ChoiceTile("音效", selectedLabel, onClick = { soundMenu = true }, enabled = !busy, dropdown = true)
                DropdownMenu(expanded = soundMenu && !busy, onDismissRequest = { soundMenu = false },
                    modifier = Modifier.fillMaxWidth(0.88f).heightIn(max = 380.dp)) {
                    DropdownMenuItem(text = { Text("无") },
                        leadingIcon = if (selectedSoundEffects.isEmpty()) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                        onClick = {
                            selectedSoundEffects = emptyList()
                            onSoundEffectsChange(emptyList()); soundMenu = false
                        })
                    audioLibrary.assets(AudioKind.SFX).forEach { asset ->
                        val selected = selectedSoundEffects.any { it.assetId == asset.id }
                        DropdownMenuItem(text = { Text(asset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                            onClick = {
                                val next = if (selected) selectedSoundEffects.filterNot { it.assetId == asset.id }
                                    else selectedSoundEffects + ShotSoundEffect(asset.id)
                                selectedSoundEffects = next
                                onSoundEffectsChange(next)
                            })
                    }
                }
            }
            Text("试听本镜会同步播放已选音效。", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
        FormCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("生图提示词", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { clipboard.setText(AnnotatedString(prompt)) }) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = "复制生图提示词", tint = HistoryColors.muted)
                }
            }
            CompactInput(prompt, { prompt = it }, enabled = !busy, minLines = 5)
            if (shot.imageStatus == "failed") {
                OutlinedButton(onClick = {
                    saveInputs(preserveImageError = true)
                    onRun(project, GenerationService.IMAGE_OPTIMIZE, shot.id)
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp)) {
                    Text("优化失败提示词并重生图")
                }
            }
            OutlinedButton(onClick = {
                saveInputs(); shot.error = ""; onSave(project)
                onRun(project, GenerationService.IMAGE, shot.id)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) {
                Text("生成新图片")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNavigate(project.shots[shotIndex - 1].id) },
                enabled = !busy && shotIndex > 0, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp),
                shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("上一镜", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Button(onClick = { saveInputs() }, enabled = !busy, modifier = Modifier.weight(1.3f),
                contentPadding = PaddingValues(6.dp), shape = RoundedCornerShape(24.dp)) { Text("保存本段", maxLines = 1) }
            OutlinedButton(onClick = { onNavigate(project.shots[shotIndex + 1].id) },
                enabled = !busy && shotIndex >= 0 && shotIndex < project.shots.lastIndex,
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp), shape = RoundedCornerShape(24.dp)) {
                Text("下一镜", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
    val currentInputsSaved = text == shot.text && prompt == shot.imagePrompt
    if (!currentInputsSaved) Text("请先保存当前镜头的修改，再插入新镜头",
        color = HistoryColors.muted, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { showInsert = true }, enabled = !busy && currentInputsSaved,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("在此镜头后插入新镜头")
    }
    if (showInsert) {
        AlertDialog(onDismissRequest = { showInsert = false },
            title = { Text("在镜头 ${shotIndex + 1} 后插入") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已有图片和配音会保留，只需生成新镜头的素材后重新渲染。",
                        style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                    InputTile("新镜头配音文字", insertedText, { insertedText = it }, minLines = 3)
                    InputTile("新镜头生图提示词", insertedPrompt, { insertedPrompt = it }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (onInsert(insertedText, insertedPrompt)) {
                        showInsert = false; insertedText = ""; insertedPrompt = ""
                    }
                }, enabled = !busy && insertedText.isNotBlank() && insertedPrompt.isNotBlank()) { Text("插入并打开") }
            },
            dismissButton = { TextButton(onClick = { showInsert = false }) { Text("取消") } })
    }
    if (showImage) {
        Dialog(onDismissRequest = { showImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).padding(12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    ShotThumbnail(imageFile, Modifier.fillMaxSize(), rotation)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rotation = (rotation + 90f) % 360f }) { Text("旋转") }
                    Button(onClick = { showImage = false }) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun ShotMotionSettingsCard(project: VideoProject, shot: Shot, busy: Boolean,
                                   onChange: (ShotMotionSetting?) -> Unit, onCutoutChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val cutoutModels = remember(context) { CutoutModels(context) }
    val modelsReady = CutoutModel.values().all(cutoutModels::isImported)
    val setting = if (shot.cutoutEnabled) null else ShotMotionRules.normalize(shot.motionSetting)
    val mode = setting?.mode ?: "default"
    val enabled = !busy && !shot.cutoutEnabled
    var modeMenu by remember(shot.id) { mutableStateOf(false) }
    var directionMenu by remember(shot.id) { mutableStateOf(false) }
    var zoomMenu by remember(shot.id) { mutableStateOf(false) }
    val modeChoices = listOf("default" to "使用默认运动") + ShotMotionRules.modes +
        listOf(ShotMotionRules.sequenceMode to "依次拉近推远")
    FormCard {
        Text("运动类型", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ChoiceTile("", if (shot.cutoutEnabled) "人物抠图已启用" else
                        modeChoices.firstOrNull { it.first == mode }?.second ?: "使用默认运动",
                        onClick = { modeMenu = true }, enabled = enabled, dropdown = true)
                    DropdownMenu(expanded = modeMenu && enabled, onDismissRequest = { modeMenu = false }) {
                        modeChoices.forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                modeMenu = false
                                val next = if (id == "default") null else {
                                    val direction = setting?.direction?.takeIf { value ->
                                        ShotMotionRules.directions.any { it.first == value }
                                    } ?: "left_to_right"
                                    ShotMotionSetting(id, direction,
                                        if (id == "zoom_pan") setting?.zoomDirection ?: "in" else "in")
                                }
                                if (ShotMotionRules.normalize(next) != setting) onChange(next)
                            })
                        }
                    }
                }
                OutlinedButton(onClick = { onCutoutChange(!shot.cutoutEnabled) },
                    enabled = !busy && (shot.cutoutEnabled || modelsReady),
                    shape = RoundedCornerShape(24.dp)) {
                    Text(if (shot.cutoutEnabled) "关闭抠图" else "人物抠图")
                }
            }
            if (!shot.cutoutEnabled && (mode == "pan" || mode == "zoom_pan")) {
                Box(Modifier.fillMaxWidth()) {
                    val directionLabel = ShotMotionRules.directions.firstOrNull { it.first == setting?.direction }?.second
                        ?: "随机方向"
                    ChoiceTile("移动方向", directionLabel, onClick = { directionMenu = true },
                        enabled = enabled, dropdown = true)
                    DropdownMenu(expanded = directionMenu && enabled, onDismissRequest = { directionMenu = false }) {
                        ShotMotionRules.directions.forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                directionMenu = false
                                setting?.copy(direction = id)?.let { next ->
                                    if (next != setting) onChange(next)
                                }
                            })
                        }
                    }
                }
            }
            if (!shot.cutoutEnabled && mode == "zoom_pan") {
                Box(Modifier.fillMaxWidth()) {
                    ChoiceTile("缩放方式", if (setting?.zoomDirection == "out") "拉远" else "推近",
                        onClick = { zoomMenu = true }, enabled = enabled, dropdown = true)
                    DropdownMenu(expanded = zoomMenu && enabled, onDismissRequest = { zoomMenu = false }) {
                        listOf("in" to "推近", "out" to "拉远").forEach { (id, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                zoomMenu = false
                                setting?.copy(zoomDirection = id)?.let { next ->
                                    if (next != setting) onChange(next)
                                }
                            })
                        }
                    }
                }
            }
            Text(if (shot.cutoutEnabled) "已选择成片抠图，取消抠图后可设置普通图片运动" else
                if (mode == ShotMotionRules.sequenceMode) "从本镜开始：本镜拉近，后续默认运动镜头交替推远、拉近；手动运动和抠图镜头跳过" else
                if (!modelsReady) "普通运动选择后自动保存；如需抠图，请先在设置中导入模型" else
                "选择后自动保存，仅影响当前镜头，重新渲染后生效",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    }
}

@Composable
private fun ShotThumbnail(file: File, modifier: Modifier = Modifier.width(128.dp).height(160.dp),
                          rotation: Float = 0f, previewSize: Int = 640, contentScale: ContentScale = ContentScale.Fit) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        file.absolutePath,
        file.lastModified(),
        file.length(),
        previewSize
    ) {
        value = withContext(Dispatchers.IO) {
            if (!file.isFile) null else runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                    val sample = maxOf(1, maxOf(bounds.outWidth / (previewSize * 3 / 4), bounds.outHeight / previewSize))
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(loadedBitmap, "", modifier.graphicsLayer { rotationZ = rotation }, contentScale = contentScale)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { Text("暂无图片", color = HistoryColors.muted) }
    }
}

@Composable
private fun ProjectProgress(project: VideoProject) {
    val busy = project.status == "running"
    Text(project.detail.ifBlank { "输入题材" }, color = HistoryColors.muted)
    if (project.total > 0 && (busy || project.status == "done")) {
        val progress = (project.completed.toFloat() / project.total.toFloat()).coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${project.completed}/${project.total}", style = MaterialTheme.typography.labelMedium)
            Text("${(progress * 100).toInt()}%", color = HistoryColors.cinnabar, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = HistoryColors.copper,
            trackColor = HistoryColors.subcard
        )
    } else if (busy) {
        Text("${stageLabel(project.stage)}处理中，请保持应用和通知权限开启", color = HistoryColors.muted)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = HistoryColors.copper, trackColor = HistoryColors.subcard)
    } else if (project.total > 0) {
        Text("进度：${project.completed}/${project.total}", color = HistoryColors.muted)
    }
}

@Composable
private fun FlowStepBar(project: VideoProject) {
    val steps = listOf("成文", "生图", "章法", "成片")
    val current = when {
        project.status == "done" || project.stage == "render" -> 3
        project.stage == "effects" -> 2
        project.stage in setOf("shots", "images", "tts") -> 1
        else -> 0
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { index, label ->
            val done = index < current || project.status == "done"
            val active = index == current
            Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).background(
                        if (done) HistoryColors.green else if (active) HistoryColors.cinnabar else HistoryColors.subcard,
                        CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    else Text("${index + 1}", color = if (active) Color.White else HistoryColors.muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = if (active) HistoryColors.cinnabar else HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun projectCostState(project: VideoProject, store: ProjectStore): Result<ProjectCostSnapshot>? {
    val ledger = remember(store) { ProjectCosts(store) }
    val file = ledger.file(project.id)
    val loaded by produceState<Result<ProjectCostSnapshot>?>(ledger.cached(project.id)?.let { Result.success(it) },
        project.id, file.lastModified(), file.length()) {
        value = withContext(Dispatchers.IO) { runCatching { ledger.read(project.id) } }
    }
    return loaded
}

@Composable
private fun CostNotes(snapshot: ProjectCostSnapshot) {
    if (snapshot.unknownCalls > 0) Text("${snapshot.unknownCalls} 次调用用量未知，费用待核实",
        style = MaterialTheme.typography.bodySmall, color = HistoryColors.ochre)
    if (snapshot.hasEarlierUntrackedCalls) Text("旧版调用未记录，以上为更新后的累计费用",
        style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
}

@Composable
private fun ProjectCostSummary(project: VideoProject, store: ProjectStore, framed: Boolean = true,
                               compact: Boolean = false, onOpenDetails: (() -> Unit)? = null,
                               selectedCategory: String = "total", onSelectCategory: ((String) -> Unit)? = null) {
    val loaded = projectCostState(project, store)
    val body: @Composable () -> Unit = {
        val snapshot = loaded?.getOrNull()
        if (snapshot != null) {
            val title = if (snapshot.unknownCalls > 0 || snapshot.hasEarlierUntrackedCalls) "已记录成本" else "累计成本"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (compact) "$title ${costMoney(snapshot.subtotal())}" else "费用统计 · $title",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = if (compact) HistoryColors.cinnabar else HistoryColors.ink)
                if (onOpenDetails != null) Button(onClick = onOpenDetails,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("费用明细", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!compact) {
                val labels = listOf("text" to "文案", "image" to "生图", "voice" to "配音", "total" to "合计")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { (category, label) ->
                        val entries = snapshot.entries.filter { it.category == category }
                        val selected = onSelectCategory != null && selectedCategory == category
                        val selection = if (onSelectCategory == null) Modifier else Modifier.selectable(
                            selected = selected, role = Role.Tab, onClick = { onSelectCategory(category) })
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (category == "total" || selected) HistoryColors.cinnabarSoft else HistoryColors.paper)
                            .then(selection).border(1.dp, if (selected) HistoryColors.cinnabar else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (category != "total") Icon(painterResource(when (category) {
                                "text" -> R.drawable.ic_copy
                                "image" -> R.drawable.ic_image
                                else -> R.drawable.ic_voice
                            }), contentDescription = null, modifier = Modifier.size(20.dp), tint = HistoryColors.ink)
                            else Spacer(Modifier.height(20.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium,
                                color = if (category == "total") HistoryColors.cinnabar else HistoryColors.muted)
                            Text(costMoney(snapshot.subtotal(if (category == "total") null else category)),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (category == "total") HistoryColors.cinnabar else HistoryColors.ink,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when (category) {
                                "image" -> "${entries.size} 张"
                                "voice" -> "${entries.size} 次"
                                "text" -> "${entries.size} 次调用"
                                else -> "累计"
                            }, style = MaterialTheme.typography.labelSmall, color = HistoryColors.muted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            CostNotes(snapshot)
        } else if (loaded?.isFailure == true) {
            Text("成本记录读取失败：${loaded?.exceptionOrNull()?.message.orEmpty()}", color = HistoryColors.ochre)
        } else Text("正在读取成本", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
    }
    if (framed) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { body() }
        }
    } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) { body() }
}

@Composable
private fun ProjectCostDetailsScreen(project: VideoProject, store: ProjectStore,
                                     category: String, onSelectCategory: (String) -> Unit) {
    var previewEntryId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var contentEntryId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var rotation by remember { mutableStateOf(0f) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingEntryId by remember { mutableStateOf<String?>(null) }
    var audioError by remember { mutableStateOf("") }
    val ledger = remember(store) { ProjectCosts(store) }
    DisposableEffect(Unit) {
        onDispose { audioPlayer?.release() }
    }
    fun stopAudio() {
        audioPlayer?.release(); audioPlayer = null; playingEntryId = null
    }
    fun playAudio(entry: CostEntry, file: File) {
        if (playingEntryId == entry.id) { stopAudio(); return }
        stopAudio()
        audioError = ""
        runCatching {
            require(file.isFile && file.length() > 1000) { "配音文件不存在或为空" }
            val media = MediaPlayer()
            audioPlayer = media
            playingEntryId = entry.id
            media.apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { if (audioPlayer === it) it.start() }
                setOnCompletionListener { if (audioPlayer === it) stopAudio() }
                setOnErrorListener { failed, what, extra ->
                    if (audioPlayer === failed) {
                        stopAudio(); audioError = "配音播放失败：$what / $extra"
                    }
                    true
                }
                prepareAsync()
            }
        }.onFailure { stopAudio(); audioError = "配音播放失败：${it.message.orEmpty()}" }
    }
    Text(project.title.ifBlank { "未命名项目" }, style = MaterialTheme.typography.titleLarge)
    ProjectCostSummary(project, store, selectedCategory = category, onSelectCategory = {
        stopAudio(); onSelectCategory(it); audioError = ""
    })
    val loaded = projectCostState(project, store)
    val snapshot = loaded?.getOrNull()
    if (audioError.isNotBlank()) Text(audioError, color = HistoryColors.cinnabar, style = MaterialTheme.typography.bodySmall)
    if (snapshot != null) {
        val visibleEntries = snapshot.entries.filter { category == "total" || it.category == category }.asReversed()
        Text("${when (category) { "text" -> "文案"; "image" -> "生图"; "voice" -> "配音"; else -> "全部" }}记录 · ${visibleEntries.size} 条",
            style = MaterialTheme.typography.titleMedium)
        if (visibleEntries.isEmpty()) Text("暂无此类费用记录", modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            textAlign = TextAlign.Center, color = HistoryColors.muted)
        visibleEntries.forEach { entry ->
            key(entry.id) {
                val archived = ledger.mediaFile(project.id, entry)
                val currentShot = if (entry.shotId != null) null else {
                    val shotId = Regex("第\\s*(\\d+)\\s*镜").find(entry.label)?.groupValues?.get(1)?.toIntOrNull()
                    project.shots.firstOrNull { it.id == shotId }
                }
                val currentMedia = currentShot?.let { shot ->
                    when (entry.category) { "image" -> store.image(project, shot); "voice" -> store.audio(project, shot); else -> null }
                }?.takeIf { it.isFile && it.length() > 1000 }
                val media = archived ?: currentMedia
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.label, style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance().format(Date(entry.createdAt)),
                            style = MaterialTheme.typography.labelSmall, color = HistoryColors.muted)
                        val detail = when (entry.category) {
                            "text" -> "输入 ${entry.inputTokens?.toString() ?: "未知"} Token · 输出 ${entry.outputTokens?.toString() ?: "未知"} Token\n${entry.rate}"
                            "image" -> "${entry.quantity} 张 × ¥${entry.rate}/张"
                            else -> "${entry.quantity} 字符 × ¥${entry.rate}/万字符"
                        }
                        Text(detail, style = MaterialTheme.typography.bodySmall)
                        Text(entry.amount?.toBigDecimalOrNull()?.let(::costMoney) ?: "费用待核实",
                            style = MaterialTheme.typography.titleMedium, color = HistoryColors.cinnabar)
                        if (archived == null && currentMedia != null) Text("旧记录未保存当次素材，以下为当前镜头版本",
                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.ochre)
                        when (entry.category) {
                            "image" -> {
                                if (media != null) Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                                    .background(HistoryColors.subcard).clickable { rotation = 0f; previewEntryId = entry.id }) {
                                    ShotThumbnail(media, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "放大图片",
                                        tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(6.dp))
                                } else Text("本次图片素材未保存", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                                if (entry.content.isNotBlank()) TextButton(onClick = { contentEntryId = entry.id }) { Text("查看本次生图提示词") }
                            }
                            "voice" -> {
                                val spoken = entry.content.ifBlank { currentShot?.text.orEmpty() }
                                if (spoken.isNotBlank()) Text(spoken, style = MaterialTheme.typography.bodyMedium)
                                else Text("此记录未保存配音文字", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                                if (media != null) OutlinedButton(onClick = { playAudio(entry, media) }, shape = RoundedCornerShape(8.dp)) {
                                    if (playingEntryId == entry.id) {
                                        Icon(painterResource(R.drawable.ic_pause), contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                    } else {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (playingEntryId == entry.id) "停止试听" else "试听此配音")
                                } else Text("本次配音素材未保存", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                            }
                            "text" -> {
                                if (entry.content.isNotBlank()) {
                                    Text(entry.content, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { contentEntryId = entry.id }) { Text("查看完整结果") }
                                } else Text("此调用未保存返回内容", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                            }
                        }
                        if (entry.assetError.isNotBlank()) Text("历史素材保存失败：${entry.assetError}",
                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.ochre)
                    }
                }
            }
        }
        val previewEntry = snapshot.entries.firstOrNull { it.id == previewEntryId }
        val previewFile = previewEntry?.let { entry ->
            ledger.mediaFile(project.id, entry) ?: if (entry.shotId == null) {
                val shotId = Regex("第\\s*(\\d+)\\s*镜").find(entry.label)?.groupValues?.get(1)?.toIntOrNull()
                project.shots.firstOrNull { it.id == shotId }?.let { store.image(project, it) }
                    ?.takeIf { it.isFile && it.length() > 1000 }
            } else null
        }
        if (previewFile != null) Dialog(onDismissRequest = { previewEntryId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    ShotThumbnail(previewFile, Modifier.fillMaxSize(), rotation = rotation, previewSize = 1920)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rotation = (rotation + 90f) % 360f },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("旋转") }
                    Button(onClick = { previewEntryId = null }) { Text("关闭") }
                }
            }
        }
        snapshot.entries.firstOrNull { it.id == contentEntryId }?.let { entry ->
            AlertDialog(onDismissRequest = { contentEntryId = null }, title = { Text(entry.label) },
                text = { Text(entry.content, modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { contentEntryId = null }) { Text("关闭") } })
        }
    }
}

@Composable
private fun ProjectOverviewScreen(project: VideoProject,
                                  store: ProjectStore,
                                  onOpenScript: () -> Unit,
                                  onOpenShots: () -> Unit,
                                  onOpenEffects: () -> Unit,
                                  onRender: () -> Unit,
                                  hasOutput: Boolean,
                                  onOpenResults: () -> Unit,
                                  onOpenCosts: () -> Unit,
                                  onFilterCosts: (String) -> Unit,
                                  onCancel: () -> Unit) {
    val busy = project.status == "running"
    val firstImage = project.shots.firstOrNull()?.let { store.image(project, it) }
    var confirmCancel by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().aspectRatio(1.72f).clip(RoundedCornerShape(24.dp)).background(HistoryColors.subcard)) {
        if (firstImage != null && firstImage.isFile) ShotThumbnail(firstImage, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Text(project.title.ifBlank { "未命名项目" }, modifier = Modifier.align(Alignment.BottomStart).padding(18.dp).padding(end = 64.dp),
            color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onOpenScript, enabled = !busy,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color.White, RoundedCornerShape(14.dp))) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑题目", tint = HistoryColors.ink)
        }
    }
    FlowStepBar(project)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, HistoryColors.line),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (project.stage == "render" || project.status == "done") "合成进度" else "制作进度",
                        style = MaterialTheme.typography.titleLarge)
                    Text(project.detail.ifBlank { "继续完成本卷内容" }, style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(projectStatusLabel(project.status),
                    if (project.status == "done") HistoryColors.green else if (busy) HistoryColors.cinnabar else HistoryColors.teal)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${project.completed} / ${project.total.coerceAtLeast(project.shots.size)} 镜",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                val percent = if (project.total > 0) (project.completed * 100 / project.total).coerceIn(0, 100) else 0
                StatusPill("$percent%", HistoryColors.cinnabar)
            }
            val progress = if (project.total > 0) (project.completed.toFloat() / project.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp),
                color = HistoryColors.cinnabar, trackColor = HistoryColors.subcard)
            if (hasOutput) Row(Modifier.fillMaxWidth().clickable(onClick = onOpenResults), verticalAlignment = Alignment.CenterVertically) {
                Text("查看视频与封面", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = HistoryColors.muted)
            }
        }
    }
    ProjectCostSummary(project, store, onOpenDetails = onOpenCosts,
        selectedCategory = "", onSelectCategory = onFilterCosts)
    Text("本卷内容", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
    val items = listOf(
        ProjectContentItem("成文", if (project.script.isBlank()) "题目、文案和封面文案" else "正文已保存 · ${project.script.length} 字", onOpenScript,
            project.script.isNotBlank()),
        ProjectContentItem("生图", "${project.shots.size} 个镜头 · 图片与配音", onOpenShots, project.shots.isNotEmpty()),
        ProjectContentItem("画面与章法", "字幕、转场、封面与开头节奏", onOpenEffects, project.shots.isNotEmpty())
    )
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, HistoryColors.line),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column {
            items.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().clickable(onClick = item.action).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(if (item.ready) HistoryColors.tealSoft else HistoryColors.subcard, CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (item.ready) Icon(Icons.Filled.Check, contentDescription = null, tint = HistoryColors.green, modifier = Modifier.size(20.dp))
                        else Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                    }
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = HistoryColors.line)
                }
                if (index < items.lastIndex) HorizontalDivider(color = HistoryColors.line)
            }
        }
    }
    Button(onClick = onRender, enabled = !busy && project.shots.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(30.dp)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "正在制作中…" else if (hasOutput) "重新渲染视频" else "渲染视频")
    }
    if (busy) OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)) { Text("暂停制作") }
    if (hasOutput) {
        Card(onClick = onOpenResults, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, HistoryColors.line),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("成片结果", style = MaterialTheme.typography.titleLarge)
                Box(Modifier.fillMaxWidth().aspectRatio(1.65f).clip(RoundedCornerShape(18.dp)).background(HistoryColors.subcard)) {
                    val coverFile = store.cover(project)
                    val previewFile = coverFile.takeIf { it.isFile && it.length() > 1000 } ?: firstImage
                    if (previewFile != null && previewFile.isFile) ShotThumbnail(previewFile, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.Center).size(54.dp).background(Color.White.copy(alpha = 0.92f), CircleShape),
                        contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, contentDescription = "打开成片", tint = HistoryColors.cinnabar) }
                    Text(if (project.status == "done") "已生成" else "待更新",
                        modifier = Modifier.align(Alignment.TopEnd).padding(9.dp)
                            .background(if (project.status == "done") HistoryColors.tealSoft else HistoryColors.subcard, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (project.status == "done") HistoryColors.green else HistoryColors.muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                Text("成片 · ${imageSizeLabel(project.imageSize)}", style = MaterialTheme.typography.titleMedium)
                Text("${store.output(project).length() / 1_048_576} MB · ${videoDurationLabel(project.duration)}",
                    color = HistoryColors.muted, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenResults, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) { Text("播放成片") }
                    OutlinedButton(onClick = onOpenResults, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp)) { Text("下载") }
                }
            }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
        title = { Text("暂停当前任务？") }, text = { Text("已生成的素材会保留，可以稍后继续制作。") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; onCancel() }) { Text("暂停") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("继续制作") } })
}

@Composable
private fun CompletedResultScreen(project: VideoProject, store: ProjectStore, exporting: Boolean,
                                  onSaveVideo: () -> Unit, onSaveCover: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val video = store.output(project)
    val first = project.shots.firstOrNull { it.id == 1 }?.let { store.image(project, it) }
    val cover = store.cover(project)
    val scope = rememberCoroutineScope()
    var creating by remember(project.id) { mutableStateOf(false) }
    var coverReady by remember(project.id) { mutableStateOf(false) }
    var coverError by remember(project.id) { mutableStateOf("") }
    var coverVersion by remember(project.id) { mutableStateOf(0) }
    val busy = project.status == "running"
    suspend fun prepareCover() {
        creating = true
        coverReady = false
        coverError = ""
        try {
            withContext(Dispatchers.IO) { CompletedCover(store).generate(project) }
            coverReady = true
            coverVersion++
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            coverError = "封面生成失败：${error.message ?: error.javaClass.simpleName}"
        } finally { creating = false }
    }
    LaunchedEffect(project.id, project.title, project.coverText, busy, video.lastModified(), video.length(),
        first?.lastModified(), first?.length()) {
        if (!busy && video.length() > 1000) prepareCover()
    }
    Text(project.title, style = MaterialTheme.typography.titleLarge)
    Text("视频", style = MaterialTheme.typography.titleMedium)
    if (video.length() > 1000) {
        CompletedVideoPreview(video, project.imageSize)
        Button(onClick = onSaveVideo, enabled = !busy && !exporting,
            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp)) {
            Text(if (exporting) "正在保存" else "保存视频")
        }
    } else Text("成片尚未生成", color = HistoryColors.muted)
    HorizontalDivider(color = HistoryColors.line)
    Text("封面", style = MaterialTheme.typography.titleMedium)
    if (creating) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (coverReady) {
        key(coverVersion) {
            ShotThumbnail(cover, Modifier.fillMaxWidth().aspectRatio(0.75f), previewSize = 1440)
        }
    }
    if (coverError.isNotBlank()) {
        Text(coverError, color = HistoryColors.ochre, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { scope.launch { prepareCover() } }, enabled = !creating && !busy,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("重试生成封面") }
    }
    Button(onClick = onSaveCover, enabled = coverReady && !creating && !busy && !exporting,
        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(8.dp)) {
        Text(if (exporting) "正在保存" else "保存封面")
    }
}

@Composable
private fun CompletedVideoPreview(file: File, imageSize: String) {
    val context = LocalContext.current
    val version = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
    var error by remember(version) { mutableStateOf("") }
    var playing by remember(version) { mutableStateOf(false) }
    var prepared by remember(version) { mutableStateOf(false) }
    var position by rememberSaveable(version) { mutableStateOf(0f) }
    var videoDuration by remember(version) { mutableStateOf(0f) }
    var seeking by remember(version) { mutableStateOf(false) }
    val view = remember(version) { VideoView(context) }
    DisposableEffect(view) {
        onDispose { view.stopPlayback() }
    }
    LaunchedEffect(view) {
        while (true) {
            if (prepared && !seeking) position = view.currentPosition.toFloat().coerceIn(0f, videoDuration)
            delay(250)
        }
    }
    val ratio = when (imageSize.lowercase()) {
        "16:9", "1792x1024", "1920x1080" -> 16f / 9f
        "1:1", "1024x1024", "1440x1440" -> 1f
        else -> 9f / 16f
    }
    key(view) {
        AndroidView(factory = {
            view.apply {
                isFocusable = false
                isFocusableInTouchMode = false
                setOnPreparedListener { media ->
                    videoDuration = media.duration.coerceAtLeast(1).toFloat()
                    prepared = true
                    seekTo(position.toInt().coerceIn(1, videoDuration.toInt()))
                }
                setOnCompletionListener { playing = false }
                setOnErrorListener { _, what, extra ->
                    playing = false
                    prepared = false
                    error = "视频预览失败：$what / $extra，仍可保存视频"
                    true
                }
                setVideoPath(file.absolutePath)
            }
        }, modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) view.pause() else {
                if (position >= videoDuration - 500f) { view.seekTo(0); position = 0f }
                view.start()
            }
            playing = !playing
        }, enabled = prepared) {
            if (playing) Icon(painterResource(R.drawable.ic_pause), contentDescription = "暂停视频")
            else Icon(Icons.Filled.PlayArrow, contentDescription = "播放视频")
        }
        Slider(value = position.coerceIn(0f, videoDuration.coerceAtLeast(1f)), onValueChange = {
            seeking = true; position = it
        }, onValueChangeFinished = { view.seekTo(position.toInt()); seeking = false },
            valueRange = 0f..videoDuration.coerceAtLeast(1f), enabled = prepared, modifier = Modifier.weight(1f))
        Text("${(position / 1000).toInt()} / ${(videoDuration / 1000).toInt()}秒", style = MaterialTheme.typography.labelSmall)
    }
    if (error.isNotBlank()) Text(error, color = HistoryColors.ochre, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, HistoryColors.line),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun StepCaption(step: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = HistoryColors.cinnabarSoft) {
            Text("第 $step 步", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = HistoryColors.cinnabar, fontWeight = FontWeight.Bold)
        }
        Text(text, color = HistoryColors.muted, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    val background = when (color) {
        HistoryColors.green -> HistoryColors.tealSoft
        HistoryColors.cinnabar -> HistoryColors.cinnabarSoft
        else -> HistoryColors.subcard
    }
    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactInput(value: String, onValueChange: (String) -> Unit, enabled: Boolean = true,
                        minLines: Int = 1, textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    BasicTextField(value, onValueChange, enabled = enabled,
        modifier = Modifier.fillMaxWidth().background(HistoryColors.subcard, RoundedCornerShape(14.dp)).padding(14.dp),
        textStyle = textStyle, minLines = minLines, maxLines = if (minLines == 1) 1 else maxOf(minLines, 10),
        singleLine = minLines == 1)
}

@Composable
private fun InputTile(label: String, value: String, onValueChange: (String) -> Unit,
                      enabled: Boolean = true, minLines: Int = 1, maxLength: Int? = null,
                      modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = modifier.border(1.dp, HistoryColors.line, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.titleMedium, color = HistoryColors.ink)
            BasicTextField(
                    value = value,
                    onValueChange = { incoming ->
                        val bounded = maxLength?.let { limit ->
                            val count = incoming.codePointCount(0, incoming.length)
                            if (count > limit) incoming.substring(0, incoming.offsetByCodePoints(0, limit)) else incoming
                        } ?: incoming
                        onValueChange(bounded)
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().background(HistoryColors.subcard, RoundedCornerShape(6.dp)).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = minLines,
                    maxLines = if (minLines == 1) 1 else maxOf(minLines, 12),
                    singleLine = minLines == 1
            )
        }
    }
}

@Composable
private fun ChoiceTile(label: String, value: String, onClick: () -> Unit, enabled: Boolean = true,
                       modifier: Modifier = Modifier.fillMaxWidth(), dropdown: Boolean = false) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = HistoryColors.subcard
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, color = HistoryColors.ink)
                if (dropdown) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun ScriptScreen(project: VideoProject,
                         store: ProjectStore,
                         onSave: (VideoProject) -> Unit,
                         onRun: (VideoProject, String, Int) -> Unit) {
    var title by key(project.title) { rememberSaveable { mutableStateOf(project.title) } }
    var script by key(project.script) { rememberSaveable { mutableStateOf(project.script) } }
    var coverText by key(project.coverText) { rememberSaveable { mutableStateOf(limitCodePoints(project.coverText, 5)) } }
    val audioLibrary = remember(store) { AudioLibrary(store.context()) }
    var bgmId by rememberSaveable(project.id, project.bgmId) { mutableStateOf(project.bgmId) }
    var bgmStartShotId by rememberSaveable(project.id, project.bgmStartShotId) { mutableStateOf(project.bgmStartShotId) }
    var granularity by rememberSaveable(project.id, project.storyboardGranularity) { mutableStateOf(project.storyboardGranularity) }
    var bgmVolume by rememberSaveable(project.id, project.bgmVolume) { mutableStateOf((project.bgmVolume * 100).toInt().toString()) }
    val scope = rememberCoroutineScope()
    var bgmMenu by remember(project.id) { mutableStateOf(false) }
    var bgmStartMenu by remember(project.id) { mutableStateOf(false) }
    var granularityMenu by remember(project.id) { mutableStateOf(false) }
    val bgmChoices = audioLibrary.assets(AudioKind.BGM).map { it.id to it.name }
    var previewError by remember { mutableStateOf("") }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var confirmSplit by remember(project.id) { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            previewJob?.cancel()
            previewPlayer?.release()
        }
    }
    fun stopPreview() {
        previewJob?.cancel(); previewJob = null
        previewPlayer?.release(); previewPlayer = null
    }
    fun preview(id: String) {
        stopPreview()
        previewError = ""
        if (id == "none") return
        runCatching {
            val file = audioLibrary.resolve(id) ?: error("找不到音频文件")
            val media = MediaPlayer()
            previewPlayer = media
            media.apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { prepared ->
                    if (previewPlayer === prepared) {
                        prepared.start()
                        previewJob = scope.launch {
                            delay(3000)
                            if (previewPlayer === prepared) {
                                previewPlayer = null
                                prepared.release()
                                previewJob = null
                            }
                        }
                    }
                }
                setOnCompletionListener { completed ->
                    if (previewPlayer === completed) stopPreview()
                }
                setOnErrorListener { failed, _, _ ->
                    if (previewPlayer === failed) {
                        stopPreview()
                        previewError = "音频试听失败"
                    }
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            stopPreview()
            previewError = it.message.orEmpty()
        }
    }
    val busy = CurrentProjectRules.inProgress(project)
    fun saveInputs(saveScript: Boolean = false): VideoProject {
        // Use a snapshot so saving local edits cannot mutate the project's polled UI state.
        val updated = VideoProject.from(project.json())
        updated.title = title.trim()
        updated.selectedTopic = null
        updated.topicQuery = ""
        updated.coverText = limitCodePoints(normalizeCoverText(coverText), 5)
        updated.bgmId = bgmId
        updated.bgmStartShotId = project.shots.firstOrNull { it.id == bgmStartShotId }?.id
            ?: project.shots.firstOrNull()?.id ?: bgmStartShotId
        updated.bgmVolume = ((bgmVolume.toFloatOrNull() ?: 18f) / 100f).coerceIn(0f, 1f)
        updated.storyboardGranularity = granularity
        if (saveScript && updated.script != script) {
            updated.script = script
            updated.stage = "script"
            updated.status = "review"
            updated.detail = if (updated.shots.isEmpty()) "正文已保存" else "正文已保存，请重新拆分镜头"
            updated.renderRevision = java.util.UUID.randomUUID().toString()
            updated.completed = 0; updated.total = 0
            updated.duration = 0.0
            updated.transitionPlans.clear(); updated.transitionPlanSignature = ""
        }
        onSave(updated)
        return updated
    }
    ProjectProgress(project)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepCaption(1, "定题目、写文案，再拆成镜头", Modifier.weight(1f))
        OutlinedButton(onClick = { saveInputs(saveScript = true) }, enabled = !busy,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), shape = RoundedCornerShape(22.dp)) {
            Text("保存草稿")
        }
    }
    FormCard {
        Text("题目", style = MaterialTheme.typography.titleSmall, color = HistoryColors.muted)
        CompactInput(title, { title = it }, enabled = !busy, minLines = 1, textStyle = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("建议 12–22 字，越具体越容易出爆款", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
            Text("${title.codePointCount(0, title.length)} 字", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    }
    FormCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("封面文案", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = HistoryColors.muted)
            Surface(shape = RoundedCornerShape(10.dp), color = HistoryColors.subcard) {
                Text("可选", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall, color = HistoryColors.muted)
            }
        }
        CompactInput(coverText, { coverText = limitCodePoints(it, 5) }, enabled = !busy, minLines = 1)
        Text("会在封面与开头 0.5 秒处同步出现，建议 2–6 字。",
            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
    }
    Text("背景音乐", style = MaterialTheme.typography.titleLarge)
    FormCard {
        Text("BGM", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
        Box(Modifier.fillMaxWidth()) {
            ChoiceTile("", bgmChoices.firstOrNull { it.first == bgmId }?.second ?: "无",
                onClick = { bgmMenu = true }, enabled = !busy, dropdown = true)
            DropdownMenu(expanded = bgmMenu, onDismissRequest = { bgmMenu = false }) {
                (listOf("none" to "无") + bgmChoices).forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        bgmMenu = false; bgmId = id; saveInputs(); preview(id)
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("音量", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
            Text("${(bgmVolume.toFloatOrNull() ?: 18f).toInt()}%", color = HistoryColors.cinnabar,
                style = MaterialTheme.typography.titleSmall)
        }
        Slider(value = ((bgmVolume.toFloatOrNull() ?: 18f) / 100f).coerceIn(0f, 1f),
            onValueChange = { value -> bgmVolume = (value * 100).toInt().toString() },
            onValueChangeFinished = { saveInputs() }, enabled = !busy, colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = HistoryColors.card, activeTrackColor = HistoryColors.cinnabar,
                inactiveTrackColor = HistoryColors.line))
        if (project.shots.isNotEmpty()) {
            val startShot = project.shots.firstOrNull { it.id == bgmStartShotId } ?: project.shots.first()
            Text("BGM 起播分镜", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
            Box(Modifier.fillMaxWidth()) {
                ChoiceTile("", "第 ${project.shotNumber(startShot.id)} 镜 · ${startShot.text.take(24)}",
                    onClick = { bgmStartMenu = true }, enabled = !busy && bgmId != "none", dropdown = true)
                DropdownMenu(expanded = bgmStartMenu && !busy, onDismissRequest = { bgmStartMenu = false },
                    modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 360.dp)) {
                    project.shots.forEach { shot ->
                        DropdownMenuItem(text = { Text("第 ${project.shotNumber(shot.id)} 镜 · ${shot.text.take(36)}",
                            maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = {
                            bgmStartShotId = shot.id; bgmStartMenu = false; saveInputs()
                        })
                    }
                }
            }
        } else Text("拆分镜头后选择起播位置", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
    }
    if (previewError.isNotBlank()) Text(previewError, color = MaterialTheme.colorScheme.error)
    FormCard {
        Text("生成设置", style = MaterialTheme.typography.titleLarge)
        Text("分镜粒度", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
        val options = listOf("fine" to "精细", "balanced" to "平衡", "coarse" to "粗略")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HistoryColors.subcard).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (id, label) ->
                val selected = granularity == id
                Surface(onClick = { granularity = id; saveInputs() }, enabled = !busy,
                    modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(20.dp),
                    color = if (selected) HistoryColors.card else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) HistoryColors.ink else HistoryColors.muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        Text(when (granularity) {
            "fine" -> "精细：每镜约 12–22 字，节奏更快。"
            "coarse" -> "粗略：每镜约 40–65 字，减少镜头数量。"
            else -> "平衡：按语义切 20–35 字，适合口播短剧。"
        }, style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
    }
    FormCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("正文", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text("${script.codePointCount(0, script.length)} 字", color = HistoryColors.muted)
        }
        CompactInput(script, { script = it }, enabled = !busy, minLines = 7)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { saveInputs(saveScript = true) },
                enabled = !busy && script.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(28.dp)) { Text("保存文案") }
            Button(onClick = {
                if (project.shots.isNotEmpty()) confirmSplit = true else {
                    val updated = saveInputs(saveScript = true)
                    stopPreview(); onRun(updated, GenerationService.SHOTS, 0)
                }
            }, enabled = !busy && script.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(28.dp)) { Text("拆分镜头") }
        }
        Button(onClick = {
            val updated = saveInputs(saveScript = true)
            stopPreview(); onRun(updated, GenerationService.SCRIPT, 0)
        }, enabled = !busy && title.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.card, contentColor = HistoryColors.ink),
            shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, HistoryColors.line)) {
            Text(if (script.isBlank()) "生成文案" else "重新生成正文")
        }
    }
    if (confirmSplit) {
        AlertDialog(
            onDismissRequest = { confirmSplit = false },
            title = { Text("重新拆分镜头") },
            text = { Text("将按当前正文重新划分镜头。变化镜头的图片和配音需要重新生成，视频需要重新渲染。拆镜失败会保留原分镜。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSplit = false
                    val updated = saveInputs(saveScript = true)
                    stopPreview()
                    onRun(updated, GenerationService.SHOTS, 0)
                }, enabled = !busy) { Text("重新拆镜") }
            },
            dismissButton = { TextButton(onClick = { confirmSplit = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ShotsScreen(project: VideoProject, store: ProjectStore,
                        onSave: (VideoProject) -> Unit,
                        onRun: (VideoProject, String, Int) -> Unit,
                        onPlay: (VideoProject, Shot) -> Unit,
                        onPlayVoice: (VideoProject) -> Unit,
                        onOpenShot: (Int) -> Unit,
                        onExport: (VideoProject) -> Unit) {
    val audioLibrary = remember(store) { AudioLibrary(store.context()) }
    val busy = project.status == "running"
    var missingImagesOnly by rememberSaveable(project.id) { mutableStateOf(false) }
    val missingImageIds = project.shots.filter { shot ->
        val image = store.image(project, shot)
        shot.imageStatus != "done" || !image.isFile || image.length() <= 1000
    }.map { it.id }.toSet()
    val visibleShots = if (missingImagesOnly) project.shots.filter { it.id in missingImageIds } else project.shots
    ProjectProgress(project)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepCaption(2, "图片按镜头逐张生成，配音按正文批量生成", Modifier.weight(1f))
        Text("${project.shots.size} 镜", style = MaterialTheme.typography.titleMedium, color = HistoryColors.muted)
    }
    if (project.shots.isEmpty()) {
        FormCard { Text("尚未拆镜，请先在成文页拆分镜头。", color = HistoryColors.muted) }
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HistoryColors.subcard).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "全部镜头 · ${project.shots.size}", true to "未生成图片 · ${missingImageIds.size}")
                .forEach { (missingOnly, label) ->
                    val selected = missingImagesOnly == missingOnly
                    Surface(modifier = Modifier.weight(1f).height(46.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { missingImagesOnly = missingOnly }),
                        shape = RoundedCornerShape(22.dp), color = if (selected) HistoryColors.card else Color.Transparent,
                        shadowElevation = if (selected) 1.dp else 0.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) HistoryColors.cinnabar else HistoryColors.muted)
                        }
                    }
                }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onRun(project, GenerationService.IMAGE_ALL, 0) },
                enabled = !busy, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) { Text("生成图片") }
            OutlinedButton(onClick = { onRun(project, GenerationService.VOICE_ALL, 0) },
                enabled = !busy, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(28.dp)) { Text("生成配音") }
        }
        val voiceReady = project.shots.all { it.audioStatus == "done" }
        OutlinedButton(onClick = { onPlayVoice(project) },
            enabled = !busy && voiceReady, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Text("试听全部 · 配音、BGM和镜头音效")
        }
        if (missingImagesOnly && visibleShots.isEmpty()) {
            Text("所有镜头图片均已生成", modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center, color = HistoryColors.muted)
        }
        visibleShots.forEach { shot ->
            key(project.id, shot.id) {
                ShotListItem(shot, project.shotNumber(shot.id), store.image(project, shot), busy, audioLibrary,
                    onClick = { onOpenShot(shot.id) }, onPlay = { onPlay(project, shot) })
            }
        }
    }
    if (project.status == "done") {
        OutlinedButton(onClick = { onExport(project) }, modifier = Modifier.fillMaxWidth()) {
            Text("保存 MP4 到相册")
        }
    }
}

@Composable
private fun ShotListItem(shot: Shot, displayNumber: Int, imageFile: File, busy: Boolean,
                         audioLibrary: AudioLibrary, onClick: () -> Unit, onPlay: () -> Unit) {
    val imageReady = shot.imageStatus == "done" && imageFile.isFile && imageFile.length() > 1000
    val soundLabel = shot.soundEffects.mapNotNull { audioLibrary.asset(it.assetId)?.name }.joinToString("、").ifBlank { "无人声效" }
    Card(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().border(1.dp, HistoryColors.line, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(102.dp).clip(RoundedCornerShape(18.dp)).background(HistoryColors.subcard)) {
                ShotThumbnail(imageFile, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text(displayNumber.toString().padStart(2, '0'), modifier = Modifier.align(Alignment.TopStart)
                    .padding(6.dp).background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("镜头 ${displayNumber.toString().padStart(2, '0')}", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    StatusPill(when {
                        shot.imageStatus == "failed" -> "待重生成"
                        imageReady -> "已生成"
                        else -> "未生成"
                    }, if (imageReady) HistoryColors.green else HistoryColors.muted)
                }
                Text(shot.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${"%.1f".format(shot.duration)} 秒 · $soundLabel", style = MaterialTheme.typography.bodySmall,
                    color = HistoryColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onPlay, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("试听 ›", color = HistoryColors.cinnabar)
                    }
                    TextButton(onClick = onClick, enabled = !busy, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(if (imageReady) "查看 ›" else "生成 ›", color = HistoryColors.cinnabar)
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectsScreen(project: VideoProject, onSave: (VideoProject) -> Unit) {
    val context = LocalContext.current
    var showCoverPicker by remember(project.id) { mutableStateOf(false) }
    var transitionMenu by remember { mutableStateOf(false) }
    var subtitle by remember(project.id, project.subtitlesEnabled) { mutableStateOf(project.subtitlesEnabled) }
    var transition by remember(project.id, project.transitionMode) { mutableStateOf(project.transitionMode) }
    var openingTemplate by remember(project.id, project.openingTemplate) { mutableStateOf(project.openingTemplate) }
    var introImageSeconds by remember(project.id, project.introImageSeconds) { mutableStateOf(project.introImageSeconds.toString()) }
    var coverDelay by rememberSaveable(project.id) { mutableStateOf(OpeningTiming.normalizeDelay(project.coverTitleDelaySeconds).toString()) }
    val transitionChoices = listOf("random" to "逐镜随机", "none" to "无", "fade" to "柔和叠化",
        "dissolve" to "颗粒溶解", "smoothleft" to "平滑左推", "zoomin" to "推近切换", "fadeblack" to "黑场淡化")
    val busy = project.status == "running"
    StepCaption(3, "字幕、转场、封面与开头节奏")
    FormCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("字幕", style = MaterialTheme.typography.titleMedium)
                Text("成片自动烧录，随配音逐句出现", style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
            }
            Switch(checked = subtitle, onCheckedChange = {
                subtitle = it; project.subtitlesEnabled = it; onSave(project)
            }, enabled = !busy)
        }
    }
    Text("画面动势", style = MaterialTheme.typography.titleLarge)
    FormCard {
        Text("转场特效", style = MaterialTheme.typography.labelLarge, color = HistoryColors.muted)
            Box(Modifier.fillMaxWidth()) {
                val normalized = if (transition == "slideleft") "smoothleft" else transition
                ChoiceTile("转场特效", transitionChoices.firstOrNull { it.first == normalized }?.second ?: "逐镜随机",
                    onClick = { transitionMenu = true }, enabled = !busy, dropdown = true)
                DropdownMenu(expanded = transitionMenu, onDismissRequest = { transitionMenu = false }) {
                    transitionChoices.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            transitionMenu = false
                            if (transition != id) {
                                transition = id; project.transitionMode = id
                                project.transitionPlans.clear(); project.transitionPlanSignature = ""
                                onSave(project)
                            }
                        })
                    }
                }
            }
    }
    Text("封面", style = MaterialTheme.typography.titleLarge)
    FormCard {
            val coverShot = project.shots.firstOrNull { it.id == project.coverShotId }
            if (coverShot != null) ShotThumbnail(ProjectStore(context).image(project, coverShot),
                Modifier.fillMaxWidth().aspectRatio(1.55f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            OutlinedButton(onClick = { showCoverPicker = true }, enabled = !busy && project.shots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) {
                Text(if (coverShot == null) "选择封面图片" else "选择封面图片 · 镜头 ${project.shotNumber(coverShot.id)}")
            }
            val delayValue = coverDelay.toFloatOrNull()
            val validDelay = delayValue != null && delayValue.isFinite() && delayValue in 0f..OpeningTiming.MAX_DELAY
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("文案与人物出现时间", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = HistoryColors.muted)
                Text("${"%.1f".format(delayValue ?: project.coverTitleDelaySeconds)} 秒", color = HistoryColors.cinnabar,
                    style = MaterialTheme.typography.titleSmall)
            }
            Slider(value = (delayValue ?: project.coverTitleDelaySeconds).coerceIn(0f, OpeningTiming.MAX_DELAY),
                onValueChange = { coverDelay = "%.1f".format(it) }, onValueChangeFinished = {
                    delayValue?.let { value ->
                        project.coverTitleDelaySeconds = OpeningTiming.normalizeDelay(value)
                        onSave(project); coverDelay = project.coverTitleDelaySeconds.toString()
                    }
                }, enabled = !busy, valueRange = 0f..OpeningTiming.MAX_DELAY)
            Text("从首镜开始计时，封面文字与首镜抠图人物同步弹出；默认 0.5 秒，重新渲染后生效。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
            val firstShot = project.shots.firstOrNull()
            if (firstShot != null && firstShot.audioStatus == "done" && firstShot.duration > 0.0) {
                val effectiveStart = OpeningTiming.start(project.coverTitleDelaySeconds, firstShot.duration)
                if (effectiveStart + 0.001 < project.coverTitleDelaySeconds) {
                    Text("首镜较短，实际在 ${"%.2f".format(effectiveStart)} 秒出现，预留弹出动画时间。",
                        style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                }
            }
    }
    Text("开头模板", style = MaterialTheme.typography.titleLarge)
    FormCard {
            val templates = listOf(
                "none" to "无",
                "life_copy_fast_cut" to "快速切图",
                "life_copy_expand_cut" to "展开切入",
                "life_copy_flash_horizontal" to "横向闪切",
                "life_copy_flash_vertical" to "纵向闪切",
                "life_copy_staggered_mask" to "错落蒙版",
                "life_copy_mosaic_collage" to "马赛克拼贴",
                "life_copy_cinematic_reveal" to "电影感揭幕",
                "life_copy_tile_build_impact" to "方块拼合冲击"
            )
            val templateLabel = templates.firstOrNull { it.first == openingTemplate }?.second ?: "无"
            ChoiceTile("首镜内部动态", templateLabel, onClick = {
                val next = (templates.indexOfFirst { it.first == openingTemplate } + 1) % templates.size
                openingTemplate = templates[next].first
                project.openingTemplate = openingTemplate
                onSave(project)
            }, enabled = !busy)
            val tempo = introImageSeconds.toFloatOrNull()?.coerceIn(0.08f, 3f) ?: project.introImageSeconds
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("每张图片节奏", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = HistoryColors.muted)
                Text("${"%.1f".format(tempo)} 秒", color = HistoryColors.cinnabar, style = MaterialTheme.typography.titleSmall)
            }
            Slider(value = tempo, onValueChange = { introImageSeconds = "%.2f".format(it) },
                onValueChangeFinished = {
                    val value = introImageSeconds.toFloatOrNull()?.coerceIn(0.08f, 3f) ?: project.introImageSeconds
                    project.introImageSeconds = value; onSave(project); introImageSeconds = value.toString()
                }, enabled = !busy, valueRange = 0.08f..3f)
            Text("模板最多使用前 5 张分镜图；节奏范围 0.08–3 秒，默认 0.3 秒。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    if (showCoverPicker) {
        ImagePickerDialog("选择封面图片", project, multiSelect = false,
            selected = setOf(project.coverShotId), onDismiss = { showCoverPicker = false },
            onConfirm = { selected ->
                selected.firstOrNull()?.let { project.coverShotId = it; onSave(project) }
                showCoverPicker = false
            })
    }

}

@Composable
private fun ImagePickerDialog(title: String, project: VideoProject, multiSelect: Boolean,
                             selected: Set<Int>, onDismiss: () -> Unit,
                             onConfirm: (Set<Int>) -> Unit,
                             canSelect: (Shot) -> Boolean = { true },
                             selectionNote: ((Shot) -> String)? = null) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    val eligibleIds = project.shots.filter(canSelect).map { it.id }.toSet()
    var checked by remember(project.id, title, selected, eligibleIds) { mutableStateOf(selected.intersect(eligibleIds)) }
    var previewId by remember(project.id) { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    fun select(shotId: Int, value: Boolean) {
        if (project.status == "running") return
        if (value && shotId !in eligibleIds) return
        checked = if (value) {
            if (multiSelect) checked + shotId else setOf(shotId)
        } else checked - shotId
    }
    fun back() {
        if (previewId != null) previewId = null else onDismiss()
    }
    val preview = project.shots.firstOrNull { it.id == previewId }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = HistoryColors.ink)
                    }
                    Text(if (preview == null) title else "镜头 ${project.shotNumber(preview.id)}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (preview == null) {
                        TextButton(onClick = { checked = emptySet() }, enabled = checked.isNotEmpty()) { Text("清空") }
                    } else {
                        val previewFile = store.image(project, preview)
                        ImageSelectionCheck(preview.id in checked, enabled = project.status != "running" &&
                            preview.id in eligibleIds && (previewFile.isFile && previewFile.length() > 1000 || preview.id in checked),
                            onCheckedChange = { select(preview.id, it) })
                    }
                }
                HorizontalDivider(color = HistoryColors.line)
                if (preview != null) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center) {
                        ShotThumbnail(store.image(project, preview), Modifier.fillMaxSize(), previewSize = 1920)
                    }
                    selectionNote?.let { note ->
                        Text(note(preview), modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2), state = gridState, modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(project.shots, key = { it.id }) { shot ->
                            val imageFile = store.image(project, shot)
                            val available = imageFile.isFile && imageFile.length() > 1000
                            val selectable = shot.id in eligibleIds
                            val isChecked = shot.id in checked
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F2))
                                    .border(if (isChecked) 2.dp else 1.dp,
                                        if (isChecked) HistoryColors.green else Color(0xFFE5E5E5), RoundedCornerShape(8.dp))) {
                                    if (available) {
                                        ShotThumbnail(imageFile, Modifier.fillMaxSize().clickable { previewId = shot.id }, contentScale = ContentScale.Crop)
                                    } else {
                                        Text("尚未生成图片", Modifier.align(Alignment.Center).padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                                    }
                                    if (!selectable) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.45f)))
                                    ImageSelectionCheck(isChecked, enabled = project.status != "running" && selectable && (available || isChecked),
                                        onCheckedChange = { select(shot.id, it) },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                                }
                                Text("镜头 ${project.shotNumber(shot.id)}", style = MaterialTheme.typography.bodyMedium,
                                    color = HistoryColors.ink, maxLines = 1)
                                selectionNote?.let { note ->
                                    Text(note(shot), style = MaterialTheme.typography.bodySmall,
                                        color = if (selectable) HistoryColors.muted else HistoryColors.cinnabar,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = HistoryColors.line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已选 ${checked.size} 镜", modifier = Modifier.weight(1f), color = HistoryColors.muted)
                    Button(onClick = { onConfirm(checked) }, enabled = project.status != "running" &&
                        (multiSelect || checked.size == 1) && checked.all { id ->
                            project.shots.firstOrNull { it.id == id }?.let { canSelect(it) && store.image(project, it).length() > 1000 } == true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.green),
                        shape = RoundedCornerShape(8.dp)) {
                        Text(if (checked.isEmpty()) "完成" else "完成（${checked.size}）")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSelectionCheck(checked: Boolean, enabled: Boolean,
                                onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).toggleable(value = checked, enabled = enabled,
        role = Role.Checkbox, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).background(if (checked) HistoryColors.green else Color.White, CircleShape)
            .border(2.dp, if (checked) HistoryColors.green else if (enabled) Color(0xFF888888) else Color(0xFFCCCCCC), CircleShape),
            contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = "已选择", tint = Color.White,
                modifier = Modifier.size(22.dp))
        }
    }
}
