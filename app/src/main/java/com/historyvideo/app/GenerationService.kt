package com.historyvideo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: ProjectStore
    private lateinit var settings: AppSettings
    private val api = CloudApi()
    private var job: Job? = null
    private var activeProject = ""

    override fun onCreate() {
        super.onCreate()
        store = ProjectStore(this)
        settings = AppSettings(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL, "视频生成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "显示选题、文案、分镜、配音和视频生成状态"
            }
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action.orEmpty()
        if (action == CANCEL) {
            job?.cancel()
            FFmpegKit.cancel()
            store.get(activeProject)?.let { project ->
                project.status = "paused"; project.detail = "已暂停，可继续制作"; store.save(project)
                notifyStatus(project.detail, false)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val id = intent?.getStringExtra(EXTRA_PROJECT) ?: return START_NOT_STICKY
        val promptOverride = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
        activeProject = id
        activeProjectId = id
        val notification = notification("正在准备视频任务")
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (Build.VERSION.SDK_INT >= 35 && (action == FINISH || action == VOICE_ALL || action == IMAGE_ALL || action == RENDER))
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(1, notification, type)
        } else startForeground(1, notification)
        job = scope.launch {
            val project = store.get(id)
            if (project == null) { stopSelf(); return@launch }
            project.taskStartedAt = System.currentTimeMillis()
            project.status = "running"
            project.detail = "正在准备视频任务"
            store.save(project)
            notifyStatus(project.detail)
            try {
                when (action) {
                    TOPICS -> generateTopics(project, promptOverride)
                    SCRIPT -> generateScript(project, promptOverride)
                    SHOTS -> generateShots(project, promptOverride)
                    FINISH -> {
                        generateAudio(project)
                        generateImages(project)
                        render(project)
                    }
                    VOICE_ALL -> generateAudio(project)
                    IMAGE_ALL -> generateImages(project, promptOverride)
                    RENDER -> render(project)
                    IMAGE -> regenerateImage(project, intent.getIntExtra(EXTRA_SHOT, 0), promptOverride)
                    AUDIO -> regenerateAudio(project, intent.getIntExtra(EXTRA_SHOT, 0))
                }
            } catch (error: Exception) {
                if (isActive) {
                    project.status = "failed"
                    val detail = userFacingError(error)
                    if (detail.contains("素材未齐全")) {
                        project.stage = "render"
                        project.completed = 0
                        project.total = 0
                    }
                    project.detail = if (detail.contains("timeout", ignoreCase = true) ||
                        detail.contains("timed out", ignoreCase = true)) {
                        "文本接口响应超时，分镜输出较长，请稍后重试"
                    } else detail.take(180).ifBlank { "任务失败" }
                    store.save(project)
                    notifyStatus(project.detail, false)
                }
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun userFacingError(error: Exception): String {
        val detail = error.message.orEmpty()
        val normalized = detail.lowercase()
        return when {
            normalized.contains("content moderation") ||
                normalized.contains("moderation") ||
                normalized.contains("内容审核") ->
                "图片提示词未通过内容审核，请进入对应分镜详情修改生图提示词后重试"
            else -> detail
        }
    }

    private fun update(project: VideoProject, stage: String, status: String, detail: String,
                       completed: Int = 0, total: Int = 0) {
        project.stage = stage; project.status = status; project.detail = detail
        project.completed = completed; project.total = total
        store.save(project)
        notifyStatus(detail, status == "running")
    }

    private fun configuredPrompt(name: String, override: String = ""): String {
        val value = override.trim().ifBlank { settings.prompt(name).trim() }
        require(value.isNotBlank()) { "请先在提示词设置中填写${promptLabel(name)}" }
        return value
    }

    private fun promptLabel(name: String) = when (name) {
        "topic" -> "选题提示词"
        "script" -> "文案提示词"
        "shots" -> "分镜提示词"
        "image" -> "生图提示词"
        else -> "提示词"
    }

    private fun scriptTitleInstruction(title: String): String =
        "【标题硬约束】文案第一句必须逐字等于视频题材“${title.trim()}”，不得改写、扩展、替换同义词或另起一个相似标题；从第二句开始再写钩子和正文。"

    private fun ensureScriptStartsWithTitle(title: String, script: String): String {
        val exactTitle = title.trim()
        val body = script.trim()
        if (exactTitle.isBlank() || body.startsWith(exactTitle)) return body
        val separator = if (exactTitle.lastOrNull() in listOf('。', '！', '？', '.', '!', '?')) "" else "。"
        return exactTitle + separator + body
    }

    private fun generateScript(project: VideoProject, promptOverride: String = "") {
        val prompt = configuredPrompt("script", promptOverride)
        update(project, "script", "running", "正在生成文案")
        val script = ensureScriptStartsWithTitle(project.title,
            api.text(settings.provider("text"), prompt, project.title, scriptTitleInstruction(project.title)))
        project.script = script
        project.shots.clear()
        update(project, "script", "review", "文案生成成功，请确认或修改")
    }

    private fun generateTopics(project: VideoProject, promptOverride: String = "") {
        val prompt = configuredPrompt("topic", promptOverride)
        update(project, "topic", "running", "正在生成候选题材")
        project.topicCandidates = api.topics(
            settings.provider("text"), prompt, project.title
        )
        update(project, "topic", "review", "选题生成成功，请选择题材")
    }

    private fun generateShots(project: VideoProject, promptOverride: String = "") {
        require(project.script.isNotBlank()) { "请先生成文案" }
        val prompt = configuredPrompt("shots", promptOverride)
        update(project, "shots", "running", "正在拆分分镜")
        val storyboardInput = "主题：${project.title}\n\n完整口播文案：\n${project.script}"
        project.shots = api.shots(
            settings.provider("text"), prompt, storyboardInput, project.script
        )
        update(project, "shots", "review", "分镜拆分成功", project.shots.size, project.shots.size)
    }

    private suspend fun generateImages(project: VideoProject, promptOverride: String = "") {
        require(project.shots.isNotEmpty()) { "请先确认分镜" }
        val imagePrompt = configuredPrompt("image", promptOverride)
        val imageProvider = settings.provider("image")
        project.imageSize = imageProvider.imageSize
        val semaphore = Semaphore(settings.concurrency.coerceIn(1, 10))
        update(project, "images", "running", "正在生成图片", 0, project.shots.size)
        project.shots.map { shot ->
            scope.async {
                semaphore.withPermit {
                    val target = store.image(project, shot)
                    var regenerated = false
                    if (target.length() < 1000 || shot.imageStatus != "done") {
                        var last: Exception? = null
                        for (attempt in 0..2) {
                            if (!isActive) return@async
                            try {
                                api.image(imageProvider, "$imagePrompt\n${shot.imagePrompt}", target)
                                last = null
                                regenerated = true
                                break
                            } catch (error: Exception) {
                                last = error
                                val detail = error.message.orEmpty().lowercase()
                                val retryable = detail.contains("timeout") ||
                                    detail.contains("timed out") ||
                                    detail.contains("连接") ||
                                    detail.contains("connection") ||
                                    detail.contains("http 408") ||
                                    detail.contains("http 429")
                                if (attempt < 2 && retryable) delay((attempt + 1) * 2000L) else break
                            }
                        }
                        if (last != null) {
                            shot.imageStatus = "failed"
                            shot.error = last?.message.orEmpty().take(120)
                        }
                    }
                    if (shot.imageStatus != "failed" && target.length() > 1000) {
                        shot.imageStatus = "done"; shot.error = ""
                        if (regenerated) File(store.directory(project.id), "temp/clip_%03d.mp4".format(shot.id)).delete()
                    }
                    synchronized(project) {
                        val count = project.shots.count { it.imageStatus == "done" }
                        update(project, "images", "running", "生成图片 $count/${project.shots.size}", count, project.shots.size)
                    }
                }
            }
        }.awaitAll()
        require(project.shots.all { it.imageStatus == "done" && store.image(project, it).length() > 1000 }) {
            "部分图片失败，请继续制作或单张重试"
        }
        update(project, "images", "review", "图片生成完成，请继续生成配音", project.shots.size, project.shots.size)
    }

    private suspend fun generateAudio(project: VideoProject) {
        require(project.shots.isNotEmpty()) { "请先确认分镜" }
        val tts = settings.provider("tts")
        project.shots.forEachIndexed { index, shot ->
            val target = store.audio(project, shot)
            update(project, "tts", "running", "正在配音 ${index + 1}/${project.shots.size}", index, project.shots.size)
            if (target.length() < 1000 || shot.audioStatus != "done") {
                val replacement = File(target.parentFile, target.name + ".new.mp3")
                api.doubao(tts, settings.voiceId, settings.speed, shot.text, replacement)
                Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                File(store.directory(project.id), "temp/clip_%03d.mp4".format(shot.id)).delete()
            }
            shot.duration = VideoRenderer(store).duration(target)
            shot.audioStatus = "done"
            update(project, "tts", "running", "配音完成 ${index + 1}/${project.shots.size}", index + 1, project.shots.size)
        }
        update(project, "tts", "review", "配音生成完成，请继续生成图片", project.shots.size, project.shots.size)
    }

    private fun regenerateImage(project: VideoProject, shotId: Int, promptOverride: String = "") {
        val shot = project.shots.firstOrNull { it.id == shotId } ?: error("镜头不存在")
        val imagePrompt = configuredPrompt("image", promptOverride)
        update(project, "images", "running", "正在重生成第 $shotId 张图片", 0, 1)
        val target = store.image(project, shot)
        val replacement = File(target.parentFile, target.name + ".new")
        api.image(settings.provider("image"), "$imagePrompt\n${shot.imagePrompt}", replacement)
        Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        File(store.directory(project.id), "temp/clip_%03d.mp4".format(shot.id)).delete()
        shot.imageStatus = "done"; shot.error = ""
        update(project, "shots", "review", "图片已更新，可继续制作", 1, 1)
    }

    private fun regenerateAudio(project: VideoProject, shotId: Int) {
        val shot = project.shots.firstOrNull { it.id == shotId } ?: error("镜头不存在")
        val target = store.audio(project, shot)
        val replacement = File(target.parentFile, target.name + ".new.mp3")
        update(project, "tts", "running", "正在重生成第 $shotId 段配音", 0, 1)
        api.doubao(settings.provider("tts"), settings.voiceId, settings.speed, shot.text, replacement)
        val newDuration = VideoRenderer(store).duration(replacement)
        Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        shot.duration = newDuration
        shot.audioStatus = "done"
        File(store.directory(project.id), "temp/clip_%03d.mp4".format(shot.id)).delete()
        update(project, "shots", "review", "第 $shotId 段配音已更新，请重新剪辑", 1, 1)
    }

    private fun render(project: VideoProject) {
        // Files are the source of truth after an app restart; status flags can
        // remain stale when a previous generation was interrupted.
        project.shots.forEach { shot ->
            if (store.image(project, shot).length() > 1000) shot.imageStatus = "done"
            if (store.audio(project, shot).length() > 1000) shot.audioStatus = "done"
        }
        val missingImages = project.shots.count { store.image(project, it).length() <= 1000 }
        val missingAudio = project.shots.count { store.audio(project, it).length() <= 1000 }
        require(missingImages == 0 && missingAudio == 0) {
            buildString {
                append("素材未齐全：")
                if (missingImages > 0) append("缺图片 ${missingImages} 个")
                if (missingImages > 0 && missingAudio > 0) append("，")
                if (missingAudio > 0) append("缺配音 ${missingAudio} 段")
                append("。请先生成缺少的素材")
            }
        }
        update(project, "render", "running", "正在剪辑视频", 0, project.shots.size)
        VideoRenderer(store).render(project, { count, total ->
            update(project, "render", "running", "正在生成视频片段 $count/$total", count, total)
        }) { detail ->
            update(project, "render", "running", detail, 0, 0)
        }
        update(project, "done", "done", "视频生成完成", project.shots.size, project.shots.size)
    }

    private fun notification(detail: String, active: Boolean = true): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val pause = PendingIntent.getService(this, 1, Intent(this, GenerationService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (active) "正在生成视频" else "视频任务完成")
            .setContentIntent(open)
            .setContentText(detail)
            .setPriority(if (active) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(!active)
            .setOngoing(active)
            .setOnlyAlertOnce(active)
        if (active) builder.addAction(android.R.drawable.ic_media_pause, "暂停", pause)
        return builder.build()
    }

    private fun notifyStatus(detail: String, active: Boolean = true) {
        val id = if (active) FOREGROUND_NOTIFICATION_ID else RESULT_NOTIFICATION_ID
        getSystemService(NotificationManager::class.java).notify(id, notification(detail, active))
    }

    override fun onDestroy() {
        if (activeProjectId == activeProject) activeProjectId = ""
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROJECT = "project_id"
        const val EXTRA_SHOT = "shot_id"
        const val EXTRA_PROMPT = "prompt_override"
        const val SCRIPT = "script"
        const val TOPICS = "topics"
        const val SHOTS = "shots"
        const val FINISH = "finish"
        const val VOICE_ALL = "voice_all"
        const val IMAGE_ALL = "image_all"
        const val RENDER = "render"
        const val IMAGE = "image"
        const val AUDIO = "audio"
        const val CANCEL = "cancel"
        private const val NOTIFICATION_CHANNEL = "generation_v2"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        @Volatile var activeProjectId: String = ""
    }
}
