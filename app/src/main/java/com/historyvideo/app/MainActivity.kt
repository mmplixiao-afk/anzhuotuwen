package com.historyvideo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Typography
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
    val paper = Color(0xFFF4EFE6)
    val card = Color(0xFFFBF8F1)
    val subcard = Color(0xFFEDE5D8)
    val ink = Color(0xFF24201C)
    val muted = Color(0xFF746A60)
    val line = Color(0xFFD8CDBD)
    val cinnabar = Color(0xFFA94235)
    val cinnabarSoft = Color(0xFFF1DAD4)
    val teal = Color(0xFF465B59)
    val tealSoft = Color(0xFFDCE7E2)
    val copper = Color(0xFFB18A56)
    val green = Color(0xFF4F6B57)
    val ochre = Color(0xFFA66A3F)
}

private val HistoryTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(color = HistoryColors.ink),
    bodyMedium = TextStyle(color = HistoryColors.ink),
    bodySmall = TextStyle(color = HistoryColors.muted),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium)
)

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
            MaterialTheme(colorScheme = historyScheme, typography = HistoryTypography) {
                Surface(color = HistoryColors.paper) { App() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
            "第 ${shot.id} 段配音尚未生成，请先完成配音"
        }
        return playFile(file, "正在试听第 ${shot.id} 段配音")
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
        var navigation by remember { mutableStateOf(listOf("home")) }
        var selectedId by remember { mutableStateOf("") }
        var selectedShotId by remember { mutableStateOf(0) }
        var selectedPromptType by remember { mutableStateOf("script") }
        var projects by remember { mutableStateOf(store.all()) }
        var message by remember { mutableStateOf("") }
        var exportingAsset by remember { mutableStateOf(false) }
        var deleteTarget by remember { mutableStateOf<VideoProject?>(null) }
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        val screen = navigation.last()

        fun navigate(to: String) {
            navigation = navigation + to
            message = ""
        }

        fun goBack() {
            if (navigation.size > 1) {
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
                                else -> "史镜 · 图文创作台（$appVersion）"
                            }, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
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
                        })
                }
        }) { inset ->
            Column(
                Modifier.fillMaxSize().padding(inset).consumeWindowInsets(inset).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = HistoryColors.tealSoft)
                    ) {
                        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = HistoryColors.teal, style = MaterialTheme.typography.bodySmall)
                    }
                }
                when (screen) {
                    "home" -> {
                        HomeScreen(currentProject, runningProject, now,
                            onNew = {
                                val project = store.create("")
                                selectedId = project.id
                                navigate("project")
                            }, onHistory = { navigate("history") }, onCurrent = {
                                currentProject?.let {
                                    selectedId = it.id
                                    navigate("project")
                                }
                            })
                    }

                    "history" -> {
                        Text("旧作存档", style = MaterialTheme.typography.headlineSmall)
                        val historyProjects = projects.filter { it.title.isNotBlank() }
                        if (historyProjects.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("尚无旧作", style = MaterialTheme.typography.titleLarge)
                                    Text("新建一卷，开始你的第一条视频", color = HistoryColors.muted)
                                }
                            }
                        }
                        historyProjects.forEach { project ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(project.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${DateFormat.getDateTimeInstance().format(Date(project.createdAt))}  ·  ${project.status}  ·  ${project.duration.toInt()}秒",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(onClick = {
                                            selectedId = project.id
                                            navigate("project")
                                        }) { Text("继续制作") }
                                        Button(
                                            onClick = { deleteTarget = project },
                                            enabled = project.status != "running",
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = HistoryColors.subcard,
                                                contentColor = HistoryColors.muted
                                            )
                                        ) { Text("删除") }
                                    }
                                    if (store.output(project).isFile) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                selectedId = project.id
                                                navigate("result")
                                            }) { Text("成片与封面") }
                                            TextButton(onClick = {
                                                runCatching { share(project) }.onSuccess { message = it }
                                                    .onFailure { message = it.message.orEmpty() }
                                            }) { Text("分享") }
                                            TextButton(onClick = {
                                                runCatching { playVideo(project) }.onSuccess { message = it }
                                                    .onFailure { message = it.message.orEmpty() }
                                            }) { Text("播放") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "api" -> ApiSettingsScreen(settings) { message = it }
                    "prompts" -> PromptListScreen(settings, onOpen = { selectedPromptType = it; navigate("prompt_editor") })
                    "prompt_editor" -> PromptEditorScreen(settings, selectedPromptType) { message = it }
                    "settings" -> SettingsScreen(onApi = { navigate("api") }, onPrompts = { navigate("prompts") },
                        onModels = { navigate("cutout_models") })
                    "cutout_models" -> CutoutModelSettingsScreen(busy = projects.any { it.status == "running" })
                    "project" -> if (selected != null) ProjectOverviewScreen(
                        selected,
                        onOpenScript = { navigate("script") },
                        onOpenShots = { navigate("shots") },
                        onOpenEffects = { navigate("effects") },
                        onRender = { runProject(selected, GenerationService.RENDER, 0) },
                        hasOutput = store.output(selected).length() > 1000,
                        onOpenResults = { navigate("result") }
                    )
                    "result" -> if (selected != null) CompletedResultScreen(selected, store,
                        exporting = exportingAsset,
                        onSaveVideo = { exportProject(selected) }, onSaveCover = { exportProject(selected, cover = true) })
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
                                })
                        }
                    }
                    "effects" -> if (selected != null) EffectsScreen(selected, ::saveProject)
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

@Composable
private fun HomeScreen(current: VideoProject?, running: VideoProject?, now: Long,
                       onNew: () -> Unit, onHistory: () -> Unit, onCurrent: () -> Unit) {
    val task = running?.detail ?: current?.detail?.ifBlank { null } ?: "空闲"
    val elapsed = running?.taskStartedAt?.takeIf { it > 0L }?.let { " · 已执行 ${elapsedLabel(it, now)}" }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("史镜", style = MaterialTheme.typography.headlineLarge, color = HistoryColors.cinnabar)
        Text("一卷成章 · AI图文视频创作", style = MaterialTheme.typography.bodyMedium, color = HistoryColors.muted)
    }
    Card(
        onClick = onCurrent,
        enabled = current != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("当前项目 · 卷宗", style = MaterialTheme.typography.titleMedium, color = HistoryColors.cinnabar)
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
        Card(onClick = onNew, modifier = Modifier.weight(1f).height(132.dp), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = HistoryColors.cinnabar)
                Text("新建一卷", style = MaterialTheme.typography.titleLarge)
                Text("从题材、文案到成片", color = HistoryColors.muted)
            }
        }
        Card(onClick = onHistory, modifier = Modifier.weight(1f).height(132.dp), shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("档", color = HistoryColors.teal, fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("旧作存档", style = MaterialTheme.typography.titleLarge)
                Text("继续编辑已有项目", color = HistoryColors.muted)
            }
        }
    }
    Text("模型与接口、提示词卷库都在右上角设置中。", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SettingsScreen(onApi: () -> Unit, onPrompts: () -> Unit, onModels: () -> Unit) {
    Text("工作台设置", style = MaterialTheme.typography.headlineSmall)
    Card(onClick = onApi, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("模型与接口", style = MaterialTheme.typography.titleLarge)
            Text("文本、图片、豆包语音接口和密钥", color = HistoryColors.muted)
        }
    }
    Card(onClick = onPrompts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
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
    var selected by remember { mutableStateOf("text") }
    var speed by remember { mutableStateOf(settings.speed.toString()) }
    var concurrency by remember { mutableStateOf(settings.concurrency.toString()) }
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
        var interfaceType by remember { mutableStateOf(initial.name) }
        var url by remember { mutableStateOf(initial.baseUrl) }
        var model by remember { mutableStateOf(initial.model) }
        var apiKey by remember { mutableStateOf("") }
        var groupId by remember { mutableStateOf(initial.groupId) }
        var imageSize by remember { mutableStateOf(initial.imageSize.ifBlank { "1024x1792" }) }
        var voiceId by remember { mutableStateOf(settings.voiceId) }
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
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
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
        Card(onClick = { onOpen(type) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
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
    var prompt by remember(type) { mutableStateOf(settings.prompt(type)) }
    Text(label, style = MaterialTheme.typography.headlineSmall)
    Text("生成时会直接使用这里保存的内容。", style = MaterialTheme.typography.bodySmall)
    Card(modifier = Modifier.fillMaxWidth().border(1.dp, HistoryColors.line, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth().padding(6.dp), minLines = 14,
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
                             onNavigate: (Int) -> Unit) {
    var text by remember(shot.id, shot.text) { mutableStateOf(shot.text) }
    var prompt by remember(shot.id, shot.imagePrompt) { mutableStateOf(shot.imagePrompt) }
    var showImage by remember { mutableStateOf(false) }
    var rotation by remember { mutableStateOf(0f) }
    val imageFile = store.image(project, shot)
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(shot.id, busy) {
            var dragDistance = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragDistance = 0f },
                onHorizontalDrag = { _, amount -> dragDistance += amount },
                onDragEnd = {
                    if (!busy && abs(dragDistance) >= 100f) {
                        onNavigate(if (dragDistance < 0f) shot.id + 1 else shot.id - 1)
                    }
                }
            )
        },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShotThumbnail(
            imageFile,
            Modifier.fillMaxWidth().height(280.dp).pointerInput(imageFile.lastModified(), imageFile.length()) {
                detectTapGestures(onDoubleTap = { showImage = true })
            }
        )
            Text("镜头 ${shot.id.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge, color = HistoryColors.cinnabar)
        Text("${shot.shotType} · ${"%.1f".format(shot.duration)} 秒", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(text, { text = it }, label = { Text("配音文字") },
            modifier = Modifier.fillMaxWidth(), minLines = 4, enabled = !busy)
        OutlinedTextField(prompt, { prompt = it }, label = { Text("生图提示词") },
            modifier = Modifier.fillMaxWidth(), minLines = 4, enabled = !busy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val textChanged = shot.text != text
                val promptChanged = shot.imagePrompt != prompt
                shot.text = text; shot.imagePrompt = prompt
                if (textChanged) shot.audioStatus = "pending"
                if (promptChanged) { shot.imageStatus = "pending"; shot.error = "" }
                onSave(project)
            }, enabled = !busy) { Text("保存本段") }
            OutlinedButton(onClick = {
                shot.imagePrompt = prompt; shot.error = ""; onSave(project)
                onRun(project, GenerationService.IMAGE, shot.id)
            }, enabled = !busy) { Text("重生图片") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                shot.text = text; onSave(project)
                onRun(project, GenerationService.AUDIO, shot.id)
            }, enabled = !busy) { Text("重配此段") }
            OutlinedButton(onClick = { onPlay(project, shot) }, enabled = !busy) { Text("试听此段") }
        }
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
                          rotation: Float = 0f, previewSize: Int = 640) {
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
        Image(loadedBitmap, "", modifier.graphicsLayer { rotationZ = rotation }, contentScale = ContentScale.Fit)
    } else {
        Box(modifier) { Text("暂无图片") }
    }
}

@Composable
private fun ProjectProgress(project: VideoProject) {
    val busy = project.status == "running"
    Text("当前阶段：${project.detail.ifBlank { "输入题材" }}", color = HistoryColors.muted)
    if (busy && project.total > 0) {
        val progress = (project.completed.toFloat() / project.total.toFloat()).coerceIn(0f, 1f)
        Text("${stageLabel(project.stage)}进度：${project.completed}/${project.total}", fontWeight = FontWeight.SemiBold)
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
            val done = index < current
            val active = index == current
            Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.width(32.dp).height(26.dp)
                        .background(if (done) HistoryColors.green else if (active) HistoryColors.cinnabar else HistoryColors.subcard,
                            RoundedCornerShape(13.dp)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    else Text("0${index + 1}", color = if (active) Color.White else HistoryColors.muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = if (active) HistoryColors.cinnabar else HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun ProjectOverviewScreen(project: VideoProject,
                                  onOpenScript: () -> Unit,
                                  onOpenShots: () -> Unit,
                                  onOpenEffects: () -> Unit,
                                  onRender: () -> Unit,
                                  hasOutput: Boolean,
                                  onOpenResults: () -> Unit) {
    FlowStepBar(project)
    ProjectProgress(project)
    Text(project.title, style = MaterialTheme.typography.headlineSmall)
    Card(onClick = onOpenScript, modifier = Modifier.fillMaxWidth().height(102.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("立题成文", style = MaterialTheme.typography.titleLarge)
            Text(if (project.script.isBlank()) "包含题材，尚无正文" else "包含题材，正文已保存",
                color = HistoryColors.muted)
        }
    }
    Card(onClick = onOpenShots, modifier = Modifier.fillMaxWidth().height(102.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("拆镜成卷", style = MaterialTheme.typography.titleLarge)
            Text(if (project.shots.isEmpty()) "尚未拆分镜头" else "${project.shots.size} 个镜头，等待画面与配音",
                color = HistoryColors.muted)
        }
    }
    Card(onClick = onOpenEffects, modifier = Modifier.fillMaxWidth().height(102.dp), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("画面与章法", style = MaterialTheme.typography.titleLarge)
            Text("字幕、画面动势、转场、封面和成片抠图", color = HistoryColors.muted)
        }
    }
    Card(
        onClick = onRender,
        enabled = project.status != "running" && project.shots.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(102.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.cinnabarSoft)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("合卷成片", style = MaterialTheme.typography.titleLarge, color = HistoryColors.cinnabar)
            Text(if (project.status == "running") "当前任务处理中" else "将配音、图片、字幕和效果合成为视频",
                color = HistoryColors.muted)
        }
    }
    if (hasOutput) {
        Card(onClick = onOpenResults, modifier = Modifier.fillMaxWidth().height(82.dp), shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("成片结果", style = MaterialTheme.typography.titleMedium)
                Text("视频与主页封面", color = HistoryColors.muted)
            }
        }
    }
}

@Composable
private fun CompletedResultScreen(project: VideoProject, store: ProjectStore, exporting: Boolean,
                                  onSaveVideo: () -> Unit, onSaveCover: () -> Unit) {
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
    var error by remember(file.absolutePath, file.lastModified()) { mutableStateOf("") }
    val view = remember(file.absolutePath, file.lastModified(), file.length()) {
        VideoView(context).apply {
            setVideoPath(file.absolutePath)
            setMediaController(MediaController(context).also { it.setAnchorView(this) })
            setOnPreparedListener { seekTo(1) }
            setOnErrorListener { _, what, extra ->
                error = "视频预览失败：$what / $extra，仍可保存视频"
                true
            }
        }
    }
    DisposableEffect(view) { onDispose { view.stopPlayback() } }
    val ratio = when (imageSize.lowercase()) {
        "16:9", "1792x1024", "1920x1080" -> 16f / 9f
        "1:1", "1024x1024", "1440x1440" -> 1f
        else -> 9f / 16f
    }
    key(view) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black))
    }
    if (error.isNotBlank()) Text(error, color = HistoryColors.ochre, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun InputTile(label: String, value: String, onValueChange: (String) -> Unit,
                      enabled: Boolean = true, minLines: Int = 1, maxLength: Int? = null,
                      modifier: Modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = modifier.border(1.dp, HistoryColors.line, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = HistoryColors.ink)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = HistoryColors.subcard)
            ) {
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
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = minLines,
                    singleLine = minLines == 1
                )
            }
        }
    }
}

@Composable
private fun ChoiceTile(label: String, value: String, onClick: () -> Unit, enabled: Boolean = true,
                       modifier: Modifier = Modifier.fillMaxWidth(), dropdown: Boolean = false) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.border(1.dp, HistoryColors.line, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
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
    var title by remember(project.id) { mutableStateOf(project.title) }
    var script by remember(project.id, project.script) { mutableStateOf(project.script) }
    var coverText by remember(project.id, project.coverText) { mutableStateOf(project.coverText) }
    var bgmId by remember(project.id, project.bgmId) { mutableStateOf(project.bgmId) }
    var introSfxId by remember(project.id, project.introSfxId) { mutableStateOf(project.introSfxId) }
    var bgmVolume by remember(project.id, project.bgmVolume) { mutableStateOf((project.bgmVolume * 100).toInt().toString()) }
    var sfxVolume by remember(project.id, project.introSfxVolume) { mutableStateOf((project.introSfxVolume * 100).toInt().toString()) }
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
        project.coverShotId = 1
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
    InputTile("口播文案", script, { script = it }, enabled = !busy, minLines = 10)
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
    ProjectProgress(project)
    Text("${project.shots.size} 镜", style = MaterialTheme.typography.titleLarge)
    if (project.shots.isEmpty()) {
        Text("尚未拆镜，请先在立题成文页面输入或生成正文。", color = HistoryColors.muted)
    } else {
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
        project.shots.forEach { shot ->
            key(project.id, shot.id) {
                ShotListItem(shot, store.image(project, shot), busy,
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
private fun ShotListItem(shot: Shot, imageFile: File, busy: Boolean,
                         onClick: () -> Unit, onPlay: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().border(1.dp, HistoryColors.line, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HistoryColors.card)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(82.dp).height(104.dp).background(HistoryColors.subcard, RoundedCornerShape(10.dp))) {
                ShotThumbnail(imageFile, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("镜头 ${shot.id.toString().padStart(2, '0')}", style = MaterialTheme.typography.titleLarge, color = HistoryColors.cinnabar)
                Text(shot.text.take(70), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                Text(if (shot.imageStatus == "done") "画面已生成" else "画面待生成", color = if (shot.imageStatus == "done") HistoryColors.green else HistoryColors.muted,
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
    val cutoutModels = remember(context) { CutoutModels(context) }
    val modelsReady = remember { CutoutModel.values().all(cutoutModels::isImported) }
    var subtitle by remember(project.id, project.subtitlesEnabled) { mutableStateOf(project.subtitlesEnabled) }
    var motion by remember(project.id, project.motionMode) { mutableStateOf(project.motionMode) }
    var transition by remember(project.id, project.transitionMode) { mutableStateOf(project.transitionMode) }
    var openingTemplate by remember(project.id, project.openingTemplate) { mutableStateOf(project.openingTemplate) }
    var introImageSeconds by remember(project.id, project.introImageSeconds) { mutableStateOf(project.introImageSeconds.toString()) }
    val busy = project.status == "running"
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("字幕", style = MaterialTheme.typography.titleLarge)
            ChoiceTile("当前状态", if (subtitle) "开启" else "关闭", onClick = {
                subtitle = !subtitle; project.subtitlesEnabled = subtitle; onSave(project)
            }, enabled = !busy)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("画面动势", style = MaterialTheme.typography.titleLarge)
            ChoiceTile("图片运动", mapOf("random" to "逐镜随机", "zoom_in" to "推近", "zoom_out" to "拉远", "pan" to "平移", "zoom_pan" to "边推拉边平移")[motion].orEmpty(), onClick = {
                motion = when (motion) { "random" -> "zoom_in"; "zoom_in" -> "zoom_out"; "zoom_out" -> "pan"; "pan" -> "zoom_pan"; else -> "random" }
                project.motionMode = motion; onSave(project)
            }, enabled = !busy)
            ChoiceTile("转场特效", mapOf(
                "fade" to "柔和叠化", "dissolve" to "颗粒溶解", "smoothleft" to "平滑左推", "slideleft" to "平滑左推",
                "zoomin" to "推近切换", "fadeblack" to "黑场淡化", "random" to "逐镜随机", "none" to "无"
            )[transition].orEmpty(), onClick = {
                transition = when (transition) {
                    "fade" -> "dissolve"; "dissolve" -> "smoothleft"; "smoothleft" -> "zoomin"
                    "zoomin" -> "fadeblack"; "fadeblack" -> "random"; "random" -> "none"; else -> "fade"
                }
                project.transitionMode = transition; onSave(project)
            }, enabled = !busy)
            OutlinedButton(onClick = {
                project.shots.forEach {
                    it.motionPlan = null
                    it.cutoutBackgroundPlan = null
                    it.cutoutForegroundPlan = null
                    it.cutoutMirrorX = null
                }
                project.transitionPlans.clear()
                project.transitionPlanSignature = ""
                onSave(project)
            }, enabled = !busy && (project.motionMode == "random" || project.transitionMode == "random")) {
                Text("重抽运动和转场")
            }
            Text("普通渲染复用已保存的随机方案；按此按钮后，下次渲染重新抽取。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("封面", style = MaterialTheme.typography.titleLarge)
            Text("背景固定使用第 1 镜画面；封面文案在立题成文页面填写。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = HistoryColors.card)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("成片抠图", style = MaterialTheme.typography.titleLarge)
            Text(if (modelsReady) "模型已就绪，可选择需要抠图的镜头。"
                else "请先到设置 → 抠图模型导入两份模型。",
                style = MaterialTheme.typography.bodySmall,
                color = if (modelsReady) HistoryColors.green else HistoryColors.ochre)
            Text("模型只需导入一次，保存在本机应用数据中；卸载应用后需重新导入。开启的镜头会在渲染前处理并缓存。",
                style = MaterialTheme.typography.bodySmall, color = HistoryColors.muted)
            val selectedCutoutCount = project.shots.count { it.cutoutEnabled }
            Button(onClick = { showCutoutPicker = true },
                enabled = !busy && modelsReady && project.shots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedCutoutCount == 0) "选择抠图图片" else "选择抠图图片 · 已选 $selectedCutoutCount 镜")
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
                    Text(if (preview == null) title else "镜头 ${preview.id}",
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
                        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().weight(1f),
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
                                Text("镜头 ${shot.id}", style = MaterialTheme.typography.bodyMedium,
                                    color = HistoryColors.ink, maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(color = HistoryColors.line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已选 ${checked.size} 镜", modifier = Modifier.weight(1f), color = HistoryColors.muted)
                    Button(onClick = { onConfirm(checked) }, enabled = project.status != "running",
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
