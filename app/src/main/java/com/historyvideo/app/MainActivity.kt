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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureStatusBar()
        store = ProjectStore(this)
        store.all().filter { it.status == "running" && it.id != GenerationService.activeProjectId }
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
        if (action == GenerationService.IMAGE) {
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
        var message by remember { mutableStateOf("") }
        var exportingAsset by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<VideoProject?>(null) }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        val screen = navigation.last()
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
            while (true) {
                projects = store.all()
                delay(1200)
            }
        }
        val selected = projects.firstOrNull { it.id == selectedId }
        val titledProjects = projects.filter { it.title.isNotBlank() }
        val runningProject = projects.firstOrNull { it.status == "running" && it.title.isNotBlank() }
        val currentProject = runningProject ?: titledProjects.firstOrNull()
        LaunchedEffect(runningProject?.id, runningProject?.status, runningProject?.taskStartedAt) {
            if (runningProject != null) {
                while (runningProject.status == "running") {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
        }

        fun saveProject(project: VideoProject) {
            store.save(project)
            projects = store.all()
        }

        fun runProject(project: VideoProject, action: String, shotId: Int = 0) {
            val promptName = when (action) {
                GenerationService.TOPICS -> "topic"
                GenerationService.SCRIPT -> "script"
                GenerationService.SHOTS -> "shots"
                GenerationService.IMAGE_ALL, GenerationService.IMAGE -> "image"
                else -> null
            }
            val prompt = promptName?.let { settings.prompt(it) }.orEmpty()
            runCatching { start(project, action, shotId, prompt) }
                .onFailure { message = it.message.orEmpty() }
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
            runCatching { play(project, shot) }.onSuccess { message = it }
                .onFailure { message = it.message.orEmpty() }
        }

        fun playVoice(project: VideoProject) {
            message = "正在合并配音，请稍候..."
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { buildVoicePreview(project) }
                }
                result.onSuccess { file ->
                    runCatching { playFile(file, "正在试听配音完成版") }
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
                            Text(when (screen) {
                                "home" -> "史镜 · 图文创作台（$appVersion）"
                                "project" -> "制作此卷"
                                "script" -> "立题成文"
                                "shots" -> "拆镜成卷"
                                "shot" -> "镜头详情"
                                "effects" -> "画面与章法"
                                "history" -> "旧作存档"
                                "api" -> "模型与接口"
                                "prompts" -> "提示词卷库"
                                "prompt_editor" -> "提示词版本"
                                "settings" -> "设置"
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
                                IconButton(onClick = { navigate("settings") }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "设置")
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
                        HomeScreen(currentProject, runningProject, now,
                            onNew = ::newProject, onHistory = { navigate("history") }, onCurrent = {
                                currentProject?.let {
                                    selectedId = it.id
                                    navigate("project")
                                }
                            })
                    }

                    "history" -> {
                        HistoryScreen(projects.filter { it.title.isNotBlank() }, store, historySearch,
                            onContinue = { selectedId = it.id; navigate("project") },
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
                        onModels = { navigate("cutout_models") })
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
                       onNew: () -> Unit, onHistory: () -> Unit, onCurrent: () -> Unit) {
    val task = running?.detail ?: current?.detail?.ifBlank { null } ?: "空闲"
    val elapsed = running?.taskStartedAt?.takeIf { it > 0L }?.let { " · 已执行 ${elapsedLabel(it, now)}" }.orEmpty()
    Card(
        onClick = onCurrent,
        enabled = current != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("当前项目", style = MaterialTheme.typography.titleLarge, color = HistoryColors.ink)
                Text(if (running != null) "制片中" else "待继续",
                    color = if (running != null) HistoryColors.copper else HistoryColors.teal,
                    fontWeight = FontWeight.Bold)
            }
            Text(current?.title ?: "尚未建立项目", style = MaterialTheme.typography.headlineSmall)
            Text("当前任务：$task$elapsed", color = HistoryColors.muted)
            if (current != null) {
                Button(onClick = onCurrent, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) {
                    Text("继续制片")
                }
            }
        }
    }
    Text("快速入口", style = MaterialTheme.typography.titleLarge, color = HistoryColors.ink)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(onClick = onNew, modifier = Modifier.weight(1f).height(132.dp), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = HistoryColors.cinnabar)
                Text("新建一卷", style = MaterialTheme.typography.titleLarge)
                Text("从题材、文案到成片", color = HistoryColors.muted)
            }
        }
        Card(onClick = onHistory, modifier = Modifier.weight(1f).height(132.dp), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = HistoryColors.muted)
                Text("旧作存档", style = MaterialTheme.typography.titleLarge)
                Text("继续编辑已有项目", color = HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun SettingsScreen(onApi: () -> Unit, onPrompts: () -> Unit, onModels: () -> Unit) {
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
            Text("按选题、文案、分镜和生图提示词分别编辑", color = HistoryColors.muted)
        }
    }
    Card(onClick = onModels, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("抠图模型", style = MaterialTheme.typography.titleLarge)
            Text("人物分割模型、背景修复模型", color = HistoryColors.muted)
        }
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

private fun stageLabel(stage: String) = when (stage) {
    "images" -> "生图"
    "tts" -> "配音"
    "render" -> "渲染"
    "script" -> "文案"
    "shots" -> "分镜"
    "topic" -> "选题"
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
    Text("选择一套提示词进入编辑页面。恢复默认会使用电脑端历史工作台提示词。",
        style = MaterialTheme.typography.bodySmall)
    AppSettings.types.forEach { type ->
        val label = when (type) {
            "topic" -> "选题提示词"
            "script" -> "文案提示词"
            "shots" -> "分镜提示词"
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
        "topic" -> "选题提示词"
        "script" -> "文案提示词"
        "shots" -> "分镜提示词"
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
    Text("恢复默认会使用历史工作台中的四组提示词。")
    AppSettings.types.forEach { type ->
        key(type) {
            var prompt by remember { mutableStateOf(settings.prompt(type)) }
            Text(when (type) {
                "topic" -> "选题"
                "script" -> "文案"
                "shots" -> "分镜"
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
                             onInsert: (String, String) -> Boolean) {
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
    fun saveInputs() {
        val textChanged = shot.text != text
        val promptChanged = shot.imagePrompt != prompt
        shot.text = text; shot.imagePrompt = prompt
        if (textChanged) shot.audioStatus = "pending"
        if (promptChanged) { shot.imageStatus = "pending"; shot.error = "" }
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
        Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(8.dp)).background(HistoryColors.subcard)) {
            ShotThumbnail(imageFile, Modifier.fillMaxSize().pointerInput(imageFile.lastModified(), imageFile.length()) {
                detectTapGestures(onDoubleTap = { showImage = true })
            })
            IconButton(onClick = { showImage = true }, enabled = imageFile.isFile,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))) {
                Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = "全屏看图", tint = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth().background(HistoryColors.card, RoundedCornerShape(8.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("镜头 ${(shotIndex + 1).toString().padStart(2, '0')}", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, color = HistoryColors.cinnabar)
            Text("时长 ${"%.1f".format(shot.duration)} 秒", style = MaterialTheme.typography.labelMedium, color = HistoryColors.muted)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_voice), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("配音文字", style = MaterialTheme.typography.titleMedium)
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8,
                    enabled = !busy, textStyle = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { saveInputs(); onRun(project, GenerationService.AUDIO, shot.id) },
                        enabled = !busy, shape = RoundedCornerShape(8.dp)) {
                        Icon(painterResource(R.drawable.ic_voice), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("重配此段")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = "复制配音文字", tint = HistoryColors.muted)
                    }
                    IconButton(onClick = { onPlay(project, shot) }, enabled = !busy,
                        modifier = Modifier.background(HistoryColors.cinnabarSoft, CircleShape)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "试听此段", tint = HistoryColors.cinnabar)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_image), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("生图提示词", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { clipboard.setText(AnnotatedString(prompt)) }) {
                Icon(painterResource(R.drawable.ic_copy), contentDescription = "复制生图提示词", tint = HistoryColors.muted)
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 12,
                    enabled = !busy, textStyle = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = {
                    saveInputs(); shot.error = ""; onSave(project)
                    onRun(project, GenerationService.IMAGE, shot.id)
                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Icon(painterResource(R.drawable.ic_image), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("生成新图片")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNavigate(project.shots[shotIndex - 1].id) },
                enabled = !busy && shotIndex > 0, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp),
                shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("上一镜", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Button(onClick = ::saveInputs, enabled = !busy, modifier = Modifier.weight(1.3f),
                contentPadding = PaddingValues(6.dp), shape = RoundedCornerShape(8.dp)) { Text("保存本段", maxLines = 1) }
            OutlinedButton(onClick = { onNavigate(project.shots[shotIndex + 1].id) },
                enabled = !busy && shotIndex >= 0 && shotIndex < project.shots.lastIndex,
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp), shape = RoundedCornerShape(8.dp)) {
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
    val steps = listOf("立题成文", "拆镜成卷", "画面与章法", "合卷成片")
    val current = when {
        project.status == "done" -> 3
        project.stage == "render" -> 3
        project.stage == "images" || project.stage == "tts" -> 2
        project.stage == "shots" -> 1
        else -> 0
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, label ->
            val done = index < current || project.status == "done"
            val active = index == current
            Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(if (done) HistoryColors.green else if (active) HistoryColors.cinnabar else HistoryColors.subcard,
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
    Box(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(8.dp)).background(HistoryColors.ink)) {
        if (firstImage != null && firstImage.isFile) ShotThumbnail(firstImage, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(Modifier.align(Alignment.CenterStart).padding(16.dp).padding(end = 62.dp)) {
            Text(project.title.ifBlank { "未命名项目" }, color = Color.White, style = MaterialTheme.typography.titleLarge,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOpenScript, enabled = !busy, modifier = Modifier.align(Alignment.BottomEnd)) {
            Icon(Icons.Filled.Edit, contentDescription = "编辑题材", tint = Color.White)
        }
    }
    FlowStepBar(project)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (project.stage == "render" || project.status == "done") "合成进度" else "制作进度",
                style = MaterialTheme.typography.titleMedium)
            ProjectProgress(project)
            if (hasOutput) TextButton(onClick = onOpenResults) { Text("查看视频与封面") }
        }
    }
    ProjectCostSummary(project, store, onOpenDetails = onOpenCosts,
        selectedCategory = "", onSelectCategory = onFilterCosts)
    Text("本卷内容", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    val items = listOf(
        Triple("立题成文", if (project.script.isBlank()) "题材、正文与封面文案" else "正文已保存", onOpenScript),
        Triple("拆镜成卷", "${project.shots.size} 个镜头 · 图片与配音", onOpenShots),
        Triple("画面与章法", "字幕、动势、转场、封面与抠图", onOpenEffects)
    )
    items.forEachIndexed { index, (title, detail, action) ->
        Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val ready = when (index) {
                0 -> project.script.isNotBlank()
                1 -> project.shots.isNotEmpty()
                else -> project.shots.isNotEmpty()
            }
            Box(Modifier.size(26.dp).background(if (ready) HistoryColors.green else HistoryColors.subcard, CircleShape),
                contentAlignment = Alignment.Center) {
                if (ready) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                else Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = HistoryColors.muted)
        }
        if (index < items.lastIndex) HorizontalDivider(color = HistoryColors.line)
    }
    Button(onClick = onRender, enabled = !busy && project.shots.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(8.dp)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "正在制作中…" else if (hasOutput) "重新渲染视频" else "渲染视频")
    }
    if (busy) OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)) { Text("暂停制作") }
    if (hasOutput) {
        Card(onClick = onOpenResults, modifier = Modifier.fillMaxWidth().height(82.dp), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("成片结果", style = MaterialTheme.typography.titleMedium)
                Text("视频与主页封面", color = HistoryColors.muted)
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
private fun InputTile(label: String, value: String, onValueChange: (String) -> Unit,
                      enabled: Boolean = true, minLines: Int = 1, maxLength: Int? = null,
                      modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = modifier.border(1.dp, HistoryColors.line, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = HistoryColors.ink)
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
    var coverText by key(project.coverText) { rememberSaveable { mutableStateOf(project.coverText) } }
    var bgmId by rememberSaveable(project.id, project.bgmId) { mutableStateOf(project.bgmId) }
    var introSfxId by rememberSaveable(project.id, project.introSfxId) { mutableStateOf(project.introSfxId) }
    var bgmVolume by rememberSaveable(project.id, project.bgmVolume) { mutableStateOf((project.bgmVolume * 100).toInt().toString()) }
    var sfxVolume by rememberSaveable(project.id, project.introSfxVolume) { mutableStateOf((project.introSfxVolume * 100).toInt().toString()) }
    val scope = rememberCoroutineScope()
    var bgmMenu by remember(project.id) { mutableStateOf(false) }
    var introSfxMenu by remember(project.id) { mutableStateOf(false) }
    val bgmChoices = listOf("none" to "无", "sad" to "悲伤 BGM", "爽文" to "爽文 BGM")
    val introSfxChoices = listOf("none" to "无", "gong" to "铜锣")
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
            val file = store.bundledAudio(id) ?: error("找不到音频文件")
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
    val busy = project.status == "running"
    ProjectProgress(project)
    InputTile("题材", title, { title = it }, enabled = !busy)
    InputTile("封面文案", coverText, { coverText = it }, enabled = !busy, maxLength = 40)
    Text("声音与配乐", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            ChoiceTile("背景音乐", bgmChoices.firstOrNull { it.first == bgmId }?.second ?: "无",
                onClick = { bgmMenu = true }, enabled = !busy, dropdown = true)
            DropdownMenu(expanded = bgmMenu, onDismissRequest = { bgmMenu = false }) {
                bgmChoices.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        bgmMenu = false
                        bgmId = id
                        preview(id)
                    })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            ChoiceTile("开头音效", introSfxChoices.firstOrNull { it.first == introSfxId }?.second ?: "无",
                onClick = { introSfxMenu = true }, enabled = !busy, dropdown = true)
            DropdownMenu(expanded = introSfxMenu, onDismissRequest = { introSfxMenu = false }) {
                introSfxChoices.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        introSfxMenu = false
                        introSfxId = id
                        preview(id)
                    })
                }
            }
        }
    }
    if (previewError.isNotBlank()) Text(previewError, color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InputTile("BGM 音量 %", bgmVolume, { bgmVolume = it.filter(Char::isDigit).take(3) }, enabled = !busy, modifier = Modifier.weight(1f))
        InputTile("音效音量 %", sfxVolume, { sfxVolume = it.filter(Char::isDigit).take(3) }, enabled = !busy, modifier = Modifier.weight(1f))
    }
    fun saveInputs(saveScript: Boolean = false) {
        project.title = title.trim(); project.coverText = normalizeCoverText(coverText)
        project.bgmId = bgmId; project.introSfxId = introSfxId
        project.bgmVolume = ((bgmVolume.toFloatOrNull() ?: 18f) / 100f).coerceIn(0f, 1f)
        project.introSfxVolume = ((sfxVolume.toFloatOrNull() ?: 100f) / 100f).coerceIn(0f, 2f)
        if (saveScript && project.script != script) {
            project.script = script
            project.stage = "script"
            project.status = "review"
            project.detail = if (project.shots.isEmpty()) "正文已保存" else "正文已保存，请重新拆分镜头"
            project.renderRevision = java.util.UUID.randomUUID().toString()
            project.completed = 0; project.total = 0
            project.duration = 0.0
            project.transitionPlans.clear(); project.transitionPlanSignature = ""
        }
        onSave(project)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { saveInputs(); onRun(project, GenerationService.TOPICS, 0) },
            enabled = !busy) { Text("AI帮我选题") }
        Button(onClick = {
            saveInputs()
            onRun(project, GenerationService.SCRIPT, 0)
        }, enabled = !busy && title.isNotBlank()) {
            Text(if (script.isBlank()) "生成正文" else "重新生成正文")
        }
    }
    if (project.topicCandidates.isNotEmpty()) {
        Text("候选题材", fontWeight = FontWeight.Medium)
        project.topicCandidates.forEach { candidate ->
            OutlinedButton(onClick = {
                title = candidate
                project.title = candidate
                onSave(project)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(candidate) }
        }
    }
    Text(if (script.isBlank()) "正文" else "正文 · ${script.length} 字", style = MaterialTheme.typography.titleLarge)
    InputTile("口播文案", script, { script = it }, enabled = !busy, minLines = 6)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { saveInputs(saveScript = true) },
            enabled = !busy && script.isNotBlank()) { Text("保存文案") }
        Button(onClick = {
            if (project.shots.isNotEmpty()) confirmSplit = true else {
                saveInputs(saveScript = true)
                stopPreview()
                onRun(project, GenerationService.SHOTS, 0)
            }
        }, enabled = !busy && script.isNotBlank()) { Text("拆镜成卷") }
    }
    if (confirmSplit) {
        AlertDialog(
            onDismissRequest = { confirmSplit = false },
            title = { Text("重新拆分镜头") },
            text = { Text("将按当前正文重新划分镜头。变化镜头的图片和配音需要重新生成，视频需要重新渲染。拆镜失败会保留原分镜。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSplit = false
                    saveInputs(saveScript = true)
                    stopPreview()
                    onRun(project, GenerationService.SHOTS, 0)
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
    val busy = project.status == "running"
    var missingImagesOnly by rememberSaveable(project.id) { mutableStateOf(false) }
    val missingImageIds = project.shots.filter { shot ->
        val image = store.image(project, shot)
        shot.imageStatus != "done" || !image.isFile || image.length() <= 1000
    }.map { it.id }.toSet()
    val visibleShots = if (missingImagesOnly) project.shots.filter { it.id in missingImageIds } else project.shots
    ProjectProgress(project)
    Text("${project.shots.size} 镜", style = MaterialTheme.typography.titleLarge)
    if (project.shots.isEmpty()) {
        Text("尚未拆镜，请先在立题成文页面输入或生成正文。", color = HistoryColors.muted)
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(HistoryColors.subcard).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(false to "全部镜头 · ${project.shots.size}", true to "未生成图片 · ${missingImageIds.size}")
                .forEach { (missingOnly, label) ->
                    val selected = missingImagesOnly == missingOnly
                    Surface(modifier = Modifier.weight(1f).height(48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { missingImagesOnly = missingOnly }),
                        shape = RoundedCornerShape(6.dp), color = if (selected) HistoryColors.card else Color.Transparent) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) HistoryColors.cinnabar else HistoryColors.muted)
                        }
                    }
                }
        }
        Button(onClick = { onRun(project, GenerationService.VOICE_ALL, 0) },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.cinnabar)) { Text("生成配音") }
        Button(onClick = { onRun(project, GenerationService.IMAGE_ALL, 0) },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = HistoryColors.teal)) { Text("生成图片") }
        val voiceReady = project.shots.all { it.audioStatus == "done" }
        OutlinedButton(onClick = { onPlayVoice(project) },
            enabled = !busy && voiceReady, modifier = Modifier.fillMaxWidth()) {
            Text(if (voiceReady) "试听配音完成版" else "配音完成后可试听完整版本")
        }
        if (missingImagesOnly && visibleShots.isEmpty()) {
            Text("所有镜头图片均已生成", modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                textAlign = TextAlign.Center, color = HistoryColors.muted)
        }
        visibleShots.forEach { shot ->
            key(project.id, shot.id) {
                ShotListItem(shot, project.shotNumber(shot.id), store.image(project, shot), busy,
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
                         onClick: () -> Unit, onPlay: () -> Unit) {
    val imageReady = shot.imageStatus == "done" && imageFile.isFile && imageFile.length() > 1000
    Card(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().border(1.dp, HistoryColors.line, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(82.dp).height(104.dp).clip(RoundedCornerShape(8.dp)).background(HistoryColors.subcard)) {
                ShotThumbnail(imageFile, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("镜头 ${displayNumber.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge, color = HistoryColors.cinnabar)
                Text(shot.text.take(70), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text(when {
                    shot.imageStatus == "failed" -> "图片生成失败"
                    imageReady -> "画面已生成"
                    else -> "画面待生成"
                }, color = if (imageReady) HistoryColors.green else if (shot.imageStatus == "failed") HistoryColors.cinnabar else HistoryColors.muted,
                    style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onPlay, enabled = !busy) { Text("试听此段") }
            }
        }
    }
}

@Composable
private fun EffectsScreen(project: VideoProject, onSave: (VideoProject) -> Unit) {
    val context = LocalContext.current
    var showCutoutPicker by remember(project.id) { mutableStateOf(false) }
    var showCoverPicker by remember(project.id) { mutableStateOf(false) }
    var motionMenu by remember { mutableStateOf(false) }
    var transitionMenu by remember { mutableStateOf(false) }
    val cutoutModels = remember(context) { CutoutModels(context) }
    val modelsReady = remember { CutoutModel.values().all(cutoutModels::isImported) }
    var subtitle by remember(project.id, project.subtitlesEnabled) { mutableStateOf(project.subtitlesEnabled) }
    var motion by remember(project.id, project.motionMode) { mutableStateOf(project.motionMode) }
    var transition by remember(project.id, project.transitionMode) { mutableStateOf(project.transitionMode) }
    var openingTemplate by remember(project.id, project.openingTemplate) { mutableStateOf(project.openingTemplate) }
    var introImageSeconds by remember(project.id, project.introImageSeconds) { mutableStateOf(project.introImageSeconds.toString()) }
    var coverDelay by rememberSaveable(project.id) { mutableStateOf(OpeningTiming.normalizeDelay(project.coverTitleDelaySeconds).toString()) }
    val motionChoices = listOf("random" to "逐镜随机", "zoom_in" to "推近", "zoom_out" to "拉远", "pan" to "平移", "zoom_pan" to "边推拉边平移")
    val transitionChoices = listOf("random" to "逐镜随机", "none" to "无", "fade" to "柔和叠化",
        "dissolve" to "颗粒溶解", "smoothleft" to "平滑左推", "zoomin" to "推近切换", "fadeblack" to "黑场淡化")
    val busy = project.status == "running"
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("字幕", style = MaterialTheme.typography.titleLarge)
            ChoiceTile("当前状态", if (subtitle) "开启" else "关闭", onClick = {
                subtitle = !subtitle; project.subtitlesEnabled = subtitle; onSave(project)
            }, enabled = !busy)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("画面动势", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.fillMaxWidth()) {
                ChoiceTile("图片运动", motionChoices.firstOrNull { it.first == motion }?.second ?: "逐镜随机",
                    onClick = { motionMenu = true }, enabled = !busy, dropdown = true)
                DropdownMenu(expanded = motionMenu, onDismissRequest = { motionMenu = false }) {
                    motionChoices.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            motionMenu = false
                            if (motion != id) {
                                motion = id; project.motionMode = id
                                project.shots.forEach { it.motionPlan = null }
                                onSave(project)
                            }
                        })
                    }
                }
            }
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
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("封面", style = MaterialTheme.typography.titleLarge)
            val coverShot = project.shots.firstOrNull { it.id == project.coverShotId }
            if (coverShot != null) ShotThumbnail(ProjectStore(context).image(project, coverShot),
                Modifier.fillMaxWidth().height(180.dp))
            Button(onClick = { showCoverPicker = true }, enabled = !busy && project.shots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (coverShot == null) "选择封面图片" else "选择封面图片 · 镜头 ${project.shotNumber(coverShot.id)}")
            }
            InputTile("文案与人物出现时间（秒）", coverDelay, { value ->
                coverDelay = value.filter { it.isDigit() || it == '.' }.take(5)
            }, enabled = !busy)
            val delayValue = coverDelay.toFloatOrNull()
            val validDelay = delayValue != null && delayValue.isFinite() && delayValue in 0f..OpeningTiming.MAX_DELAY
            if (!validDelay) Text("请输入 0–5 秒", color = HistoryColors.cinnabar,
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                delayValue?.let {
                    project.coverTitleDelaySeconds = OpeningTiming.normalizeDelay(it)
                    onSave(project)
                    coverDelay = project.coverTitleDelaySeconds.toString()
                }
            }, enabled = !busy && validDelay, modifier = Modifier.fillMaxWidth()) {
                Text(if (validDelay && delayValue == project.coverTitleDelaySeconds) "出现时间已保存" else "保存出现时间")
            }
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
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("开头模板", style = MaterialTheme.typography.titleLarge)
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
            InputTile("每张图片节奏（秒）", introImageSeconds, { value ->
                introImageSeconds = value.filter { it.isDigit() || it == '.' }.take(4)
                introImageSeconds.toFloatOrNull()?.let {
                    project.introImageSeconds = it.coerceIn(0.08f, 3f)
                    onSave(project)
                }
            }, enabled = !busy)
            Text("模板最多使用前 5 张分镜图；节奏范围 0.08–3 秒，默认 0.3 秒。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("成片抠图", style = MaterialTheme.typography.titleLarge)
            val selectedCutoutCount = project.shots.count { it.cutoutEnabled }
            Button(onClick = { showCutoutPicker = true },
                enabled = !busy && modelsReady && project.shots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (!modelsReady) "请先导入抠图模型" else if (selectedCutoutCount == 0) "选择抠图图片" else "选择抠图图片 · 已选 $selectedCutoutCount 镜")
            }
            if (selectedCutoutCount > 0) {
                OutlinedButton(onClick = {
                    project.shots.forEach { it.cutoutEnabled = false }
                    project.cutoutEnabled = false
                    onSave(project)
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("清除全部镜头的抠图选择（$selectedCutoutCount 镜）")
                }
            }
        }
    }
    if (showCoverPicker) {
        ImagePickerDialog("选择封面图片", project, multiSelect = false,
            selected = setOf(project.coverShotId), onDismiss = { showCoverPicker = false },
            onConfirm = { selected ->
                selected.firstOrNull()?.let { project.coverShotId = it; onSave(project) }
                showCoverPicker = false
            })
    }
    if (showCutoutPicker) {
        ImagePickerDialog("选择抠图图片", project, multiSelect = true,
            selected = project.shots.filter { it.cutoutEnabled }.map { it.id }.toSet(),
            onDismiss = { showCutoutPicker = false },
            onConfirm = { selected ->
                project.shots.forEach { it.cutoutEnabled = it.id in selected }
                project.cutoutEnabled = project.shots.any { it.cutoutEnabled }
                onSave(project)
                showCutoutPicker = false
            })
    }
}

@Composable
private fun ImagePickerDialog(title: String, project: VideoProject, multiSelect: Boolean,
                             selected: Set<Int>, onDismiss: () -> Unit,
                             onConfirm: (Set<Int>) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    var checked by remember(project.id, title, selected) { mutableStateOf(selected.toSet()) }
    var previewId by remember(project.id) { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    fun select(shotId: Int, value: Boolean) {
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
                        ImageSelectionCheck(preview.id in checked, enabled = true,
                            onCheckedChange = { select(preview.id, it) })
                    }
                }
                HorizontalDivider(color = HistoryColors.line)
                if (preview != null) {
                    Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF2F2F2)),
                        contentAlignment = Alignment.Center) {
                        ShotThumbnail(store.image(project, preview), Modifier.fillMaxSize(), previewSize = 1920)
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
                            val isChecked = shot.id in checked
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F2))
                                    .border(if (isChecked) 2.dp else 1.dp,
                                        if (isChecked) HistoryColors.green else Color(0xFFE5E5E5), RoundedCornerShape(8.dp))) {
                                    if (available) {
                                        ShotThumbnail(imageFile, Modifier.fillMaxSize().clickable { previewId = shot.id })
                                    } else {
                                        Text("尚未生成图片", Modifier.align(Alignment.Center).padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
                                    }
                                    ImageSelectionCheck(isChecked, enabled = available || isChecked,
                                        onCheckedChange = { select(shot.id, it) },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                                }
                                Text("镜头 ${project.shotNumber(shot.id)}", style = MaterialTheme.typography.bodyMedium,
                                    color = HistoryColors.ink, maxLines = 1)
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
                            project.shots.firstOrNull { it.id == id }?.let { store.image(project, it).length() > 1000 } == true
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
