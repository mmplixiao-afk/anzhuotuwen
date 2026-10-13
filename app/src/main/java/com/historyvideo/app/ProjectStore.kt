package com.historyvideo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class MotionPoint(
    val zoom: Double,
    val x: Double,
    val y: Double
) {
    fun json() = JSONObject().apply {
        put("zoom", zoom); put("x", x); put("y", y)
    }

    companion object {
        fun from(value: JSONObject) = MotionPoint(
            zoom = value.optDouble("zoom", 1.0),
            x = value.optDouble("x", 0.0),
            y = value.optDouble("y", 0.0)
        )
    }
}

data class MotionPlan(
    val mode: String,
    val start: MotionPoint,
    val end: MotionPoint
) {
    fun json() = JSONObject().apply {
        put("mode", mode); put("start", start.json()); put("end", end.json())
    }

    companion object {
        fun from(value: JSONObject) = MotionPlan(
            mode = value.optString("mode", "zoom_in"),
            start = MotionPoint.from(value.optJSONObject("start") ?: JSONObject()),
            end = MotionPoint.from(value.optJSONObject("end") ?: JSONObject())
        )
    }
}

data class TransitionPlan(val type: String, val frames: Int) {
    fun json() = JSONObject().apply { put("type", type); put("frames", frames) }

    companion object {
        fun from(value: JSONObject) = TransitionPlan(
            type = value.optString("type", "none"),
            frames = value.optInt("frames", 0).coerceAtLeast(0)
        )
    }
}

data class Shot(
    val id: Int,
    var text: String,
    var imagePrompt: String,
    var duration: Double = 3.0,
    var shotType: String = "environment",
    var hasPerson: Boolean = false,
    var cameraMotion: String = "static",
    var soundEffect: String = "",
    var transition: String = "cut",
    var imageStatus: String = "pending",
    var audioStatus: String = "pending",
    var error: String = "",
    var cutoutEnabled: Boolean = false,
    var subtitleChunks: MutableList<String> = mutableListOf(),
    var motionPlan: MotionPlan? = null,
    var cutoutMirrorX: Boolean? = null,
    var cutoutBackgroundPlan: MotionPlan? = null,
    var cutoutForegroundPlan: MotionPlan? = null,
    var visual: String = "",
    var imageRevision: String = "",
    var audioRevision: String = "",
    var motionSetting: ShotMotionSetting? = null,
    var motionPlanSetting: ShotMotionSetting? = null
) {
    fun json() = JSONObject().apply {
        put("shot_id", id); put("text", text); put("image_prompt", imagePrompt)
        put("duration", duration); put("shot_type", shotType); put("has_person", hasPerson)
        put("camera_motion", cameraMotion); put("sound_effect", soundEffect)
        put("transition", transition); put("image_status", imageStatus)
        put("audio_status", audioStatus); put("error", error); put("cutout_enabled", cutoutEnabled)
        put("subtitle_chunks", JSONArray(subtitleChunks))
        put("motion_plan", motionPlan?.json() ?: JSONObject.NULL)
        put("motion_setting", if (cutoutEnabled) JSONObject.NULL else motionSetting?.json() ?: JSONObject.NULL)
        put("motion_plan_setting", if (cutoutEnabled) JSONObject.NULL else motionPlanSetting?.json() ?: JSONObject.NULL)
        put("cutout_mirror_x", cutoutMirrorX ?: JSONObject.NULL)
        put("cutout_background_plan", cutoutBackgroundPlan?.json() ?: JSONObject.NULL)
        put("cutout_foreground_plan", cutoutForegroundPlan?.json() ?: JSONObject.NULL)
        put("visual", visual); put("image_revision", imageRevision); put("audio_revision", audioRevision)
    }

    companion object {
        fun from(value: JSONObject) = Shot(
            id = value.optInt("shot_id", value.optInt("id")),
            text = value.optString("text", value.optString("voiceover")),
            imagePrompt = value.optString("image_prompt", value.optString("visual")),
            duration = value.optDouble("duration", 3.0),
            shotType = value.optString("shot_type", "environment"),
            hasPerson = value.optBoolean("has_person"),
            cameraMotion = value.optString("camera_motion", "static"),
            soundEffect = value.optString("sound_effect"),
            transition = value.optString("transition", "cut"),
            imageStatus = value.optString("image_status", "pending"),
            audioStatus = value.optString("audio_status", "pending"),
            error = value.optString("error"),
            cutoutEnabled = value.optBoolean("cutout_enabled", false),
            subtitleChunks = mutableListOf<String>().apply {
                val chunks = value.optJSONArray("subtitle_chunks") ?: JSONArray()
                for (index in 0 until chunks.length()) {
                    val item = chunks.opt(index)
                    val text = when (item) {
                        is JSONObject -> item.optString("text", item.optString("subtitle"))
                        else -> item?.toString().orEmpty()
                    }.trim()
                    if (text.isNotBlank()) add(text)
                }
            },
            motionPlan = value.optJSONObject("motion_plan")?.let { MotionPlan.from(it) },
            cutoutMirrorX = if (value.isNull("cutout_mirror_x")) null else value.optBoolean("cutout_mirror_x"),
            cutoutBackgroundPlan = value.optJSONObject("cutout_background_plan")?.let { MotionPlan.from(it) },
            cutoutForegroundPlan = value.optJSONObject("cutout_foreground_plan")?.let { MotionPlan.from(it) },
            visual = value.optString("visual"),
            imageRevision = value.optString("image_revision"),
            audioRevision = value.optString("audio_revision"),
            motionSetting = if (value.optBoolean("cutout_enabled")) null else ShotMotionSetting.from(value.optJSONObject("motion_setting")),
            motionPlanSetting = if (value.optBoolean("cutout_enabled")) null else ShotMotionSetting.from(value.optJSONObject("motion_plan_setting"))
        )
    }
}

data class VideoProject(
    val id: String,
    var title: String,
    val createdAt: Long = System.currentTimeMillis(),
    var taskStartedAt: Long = 0L,
    var stage: String = "topic",
    var status: String = "draft",
    var detail: String = "",
    var topicCandidates: MutableList<String> = mutableListOf(),
    var script: String = "",
    var shots: MutableList<Shot> = mutableListOf(),
    var completed: Int = 0,
    var total: Int = 0,
    var duration: Double = 0.0,
    var imageSize: String = "9:16",
    var subtitlesEnabled: Boolean = true,
    var motionMode: String = "random",
    var transitionMode: String = "random",
    var transitionPlans: MutableList<TransitionPlan> = mutableListOf(),
    var transitionPlanSignature: String = "",
    var coverShotId: Int = 1,
    var coverText: String = "",
    var openingTemplate: String = "none",
    var introImageSeconds: Float = 0.3f,
    var cutoutEnabled: Boolean = false,
    var bgmId: String = "none",
    var introSfxId: String = "none",
    var bgmVolume: Float = 0.18f,
    var introSfxVolume: Float = 1.0f,
    var ttsVolume: Float = 1.0f,
    var storyboardScript: String = "",
    var renderRevision: String = "",
    var coverTitleDelaySeconds: Float = OpeningTiming.DEFAULT_DELAY,
    var topicQuery: String = "",
    var topicIdeas: MutableList<TopicIdea> = mutableListOf(),
    var selectedTopic: TopicIdea? = null
) {
    fun json() = JSONObject().apply {
        put("schema_version", 1); put("id", id); put("title", title)
        put("created_at", createdAt); put("task_started_at", taskStartedAt)
        put("stage", stage); put("status", status)
        put("detail", detail); put("script", script); put("completed", completed)
        put("topic_candidates", JSONArray(topicCandidates))
        put("topic_query", topicQuery)
        put("topic_ideas", JSONArray().apply { topicIdeas.forEach { put(it.json()) } })
        put("selected_topic", selectedTopic?.json() ?: JSONObject.NULL)
        put("total", total); put("duration", duration)
        put("image_size", imageSize); put("subtitles_enabled", subtitlesEnabled)
        put("motion_mode", motionMode); put("transition_mode", transitionMode)
        put("transition_plans", JSONArray().apply { transitionPlans.forEach { put(it.json()) } })
        put("transition_plan_signature", transitionPlanSignature)
        put("cover_shot_id", coverShotId); put("cover_text", coverText)
        put("cover_title_delay_seconds", OpeningTiming.normalizeDelay(coverTitleDelaySeconds))
        put("opening_template", openingTemplate); put("intro_image_seconds", introImageSeconds)
        put("cutout_enabled", cutoutEnabled)
        put("bgm_id", bgmId); put("intro_sfx_id", introSfxId)
        put("bgm_volume", bgmVolume); put("intro_sfx_volume", introSfxVolume)
        put("tts_volume", ttsVolume)
        put("storyboard_script", storyboardScript); put("render_revision", renderRevision)
        put("shots", JSONArray().apply { shots.forEach { put(it.json()) } })
    }

    companion object {
        fun from(value: JSONObject) = VideoProject(
            id = value.getString("id"), title = value.optString("title"),
            createdAt = value.optLong("created_at"), taskStartedAt = value.optLong("task_started_at"),
            stage = value.optString("stage", "topic"),
            status = value.optString("status", "draft"), detail = value.optString("detail"),
            topicCandidates = mutableListOf<String>().apply {
                val array = value.optJSONArray("topic_candidates") ?: JSONArray()
                for (i in 0 until array.length()) add(array.optString(i))
            },
            script = value.optString("script"),
            shots = mutableListOf<Shot>().apply {
                val array = value.optJSONArray("shots") ?: JSONArray()
                for (i in 0 until array.length()) add(Shot.from(array.getJSONObject(i)))
            },
            completed = value.optInt("completed"), total = value.optInt("total"),
            duration = value.optDouble("duration"),
            imageSize = value.optString("image_size", "9:16"),
            subtitlesEnabled = value.optBoolean("subtitles_enabled", true),
            motionMode = value.optString("motion_mode", "random"),
            transitionMode = value.optString("transition_mode", "random"),
            transitionPlans = mutableListOf<TransitionPlan>().apply {
                val plans = value.optJSONArray("transition_plans") ?: JSONArray()
                for (index in 0 until plans.length()) {
                    plans.optJSONObject(index)?.let { add(TransitionPlan.from(it)) }
                }
            },
            transitionPlanSignature = value.optString("transition_plan_signature"),
            coverShotId = value.optInt("cover_shot_id", 1),
            coverText = value.optString("cover_text"),
            openingTemplate = value.optString("opening_template", "none"),
            introImageSeconds = value.optDouble("intro_image_seconds", 0.3).toFloat().coerceIn(0.08f, 3f),
            cutoutEnabled = value.optBoolean("cutout_enabled", false),
            bgmId = value.optString("bgm_id", "none"),
            introSfxId = value.optString("intro_sfx_id", "none"),
            bgmVolume = value.optDouble("bgm_volume", 0.18).toFloat().coerceIn(0f, 1f),
            introSfxVolume = value.optDouble("intro_sfx_volume", 1.0).toFloat().coerceIn(0f, 2f),
            ttsVolume = value.optDouble("tts_volume", 1.0).toFloat().coerceIn(0f, 2f),
            storyboardScript = value.optString("storyboard_script", value.optString("script")),
            renderRevision = value.optString("render_revision"),
            coverTitleDelaySeconds = OpeningTiming.normalizeDelay(value.optDouble("cover_title_delay_seconds", OpeningTiming.DEFAULT_DELAY.toDouble()).toFloat()),
            topicQuery = value.optString("topic_query"),
            topicIdeas = TopicIdeas.storedIdeas(value),
            selectedTopic = value.optJSONObject("selected_topic")?.let { TopicIdea.from(it, "selected-topic") }
        )
    }
}

/** Display order is independent of the immutable ID used in asset paths. */
internal fun VideoProject.shotNumber(shotId: Int): Int = shots.indexOfFirst { it.id == shotId } + 1

class ProjectStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val audioRoot = File(context.filesDir, "audio_assets").apply { mkdirs() }

    internal fun context(): Context = appContext

    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id)
    }

    fun create(title: String): VideoProject = synchronized(this) {
        val project = VideoProject(UUID.randomUUID().toString(), title.trim())
        save(project)
        ProjectCosts(this).initialize(project.id)
        project
    }

    fun get(id: String): VideoProject? = synchronized(this) {
        val file = File(directory(id), "project.json")
        if (!file.isFile) null else runCatching { VideoProject.from(JSONObject(file.readText())) }.getOrNull()
    }

    fun all(): List<VideoProject> = synchronized(this) {
        root.listFiles().orEmpty().mapNotNull { dir ->
            runCatching { get(dir.name) }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    fun save(project: VideoProject) = synchronized(this) {
        val dir = directory(project.id).apply { mkdirs() }
        listOf("images", "audio", "subtitles", "sfx", "temp", "output").forEach {
            File(dir, it).mkdirs()
        }
        val pending = File(dir, "project.json.tmp")
        pending.writeText(project.json().toString(2))
        Files.move(pending.toPath(), File(dir, "project.json").toPath(),
            StandardCopyOption.REPLACE_EXISTING)
        File(dir, "script.txt").writeText(project.script)
        File(dir, "shots.json").writeText(JSONArray().apply { project.shots.forEach { put(it.json()) } }.toString(2))
    }

    fun delete(id: String) = synchronized(this) { directory(id).deleteRecursively() }
    private fun revisionSuffix(revision: String): String {
        require(revision.isEmpty() || revision.matches(Regex("[a-f0-9-]{36}")))
        return if (revision.isEmpty()) "" else "_$revision"
    }

    fun image(project: VideoProject, shot: Shot) = File(directory(project.id),
        "images/%03d%s.jpg".format(shot.id, revisionSuffix(shot.imageRevision)))
    fun audio(project: VideoProject, shot: Shot) = File(directory(project.id),
        "audio/%03d%s.mp3".format(shot.id, revisionSuffix(shot.audioRevision)))
    fun output(project: VideoProject) = File(directory(project.id),
        "output/final${revisionSuffix(project.renderRevision)}.mp4")
    fun cover(project: VideoProject) = File(directory(project.id), "output/completed_cover.png")
    fun completedInfo(project: VideoProject) = File(directory(project.id), "output/制作信息.json")
    fun exportInfo(project: VideoProject) = File(directory(project.id), "output/album_exports.json")

    fun applyStoryboard(project: VideoProject, generated: List<Shot>) {
        val previous = project.shots.associateBy { it.id }
        // Separate asset revisions prevent an old numbered file being used for a new beat.
        project.shots = generated.map { next ->
            val old = previous[next.id]
            val sameVoice = old != null && old.text == next.text
            val sameImage = sameVoice && old!!.imagePrompt == next.imagePrompt
            next.copy(
                imageRevision = if (sameImage) old!!.imageRevision else UUID.randomUUID().toString(),
                audioRevision = if (sameVoice) old!!.audioRevision else UUID.randomUUID().toString(),
                imageStatus = if (sameImage && old!!.imageStatus == "done") "done" else "pending",
                audioStatus = if (sameVoice && old!!.audioStatus == "done") "done" else "pending",
                duration = if (sameVoice) old!!.duration else next.duration,
                cutoutEnabled = if (sameImage) old!!.cutoutEnabled else false,
                motionPlan = if (sameImage) old!!.motionPlan else null,
                motionSetting = if (sameImage && !old!!.cutoutEnabled) ShotMotionRules.normalize(old!!.motionSetting) else null,
                motionPlanSetting = if (sameImage && !old!!.cutoutEnabled) ShotMotionRules.normalize(old!!.motionPlanSetting) else null,
                cutoutMirrorX = if (sameImage) old!!.cutoutMirrorX else null,
                cutoutBackgroundPlan = if (sameImage) old!!.cutoutBackgroundPlan else null,
                cutoutForegroundPlan = if (sameImage) old!!.cutoutForegroundPlan else null
            )
        }.toMutableList()
        project.storyboardScript = project.script
        project.renderRevision = UUID.randomUUID().toString()
        project.duration = 0.0
        project.coverShotId = 1
        project.transitionPlans.clear()
        project.transitionPlanSignature = ""
    }

    fun insertShotAfter(project: VideoProject, afterShotId: Int, text: String, imagePrompt: String): Shot {
        require(project.status != "running") { "请等待当前任务完成后再插入镜头" }
        require(project.storyboardScript == project.script) { "正文有尚未同步到分镜的修改，请先处理后再插入镜头" }
        val cleanText = text.trim()
        val cleanPrompt = imagePrompt.trim()
        require(cleanText.isNotBlank()) { "请输入新增镜头的配音文字" }
        require(cleanPrompt.isNotBlank()) { "请输入新增镜头的生图提示词" }
        val position = project.shots.indexOfFirst { it.id == afterShotId }
        require(position >= 0) { "找不到要插入位置的镜头" }
        val newId = (project.shots.maxOfOrNull { it.id } ?: 0) + 1
        require(newId > 0) { "镜头数量超出范围" }
        val shot = Shot(
            id = newId, text = cleanText, imagePrompt = cleanPrompt, duration = 0.0,
            subtitleChunks = mutableListOf(cleanText),
            imageRevision = UUID.randomUUID().toString(), audioRevision = UUID.randomUUID().toString()
        )
        // Keep existing IDs and revisions: their audio/image paths and cost records remain valid.
        project.shots.add(position + 1, shot)
        project.script = project.shots.joinToString("") { it.text }
        project.storyboardScript = project.script
        project.renderRevision = UUID.randomUUID().toString()
        project.duration = 0.0
        project.status = "review"; project.stage = "shots"
        project.completed = 0; project.total = 0; project.taskStartedAt = 0L
        project.detail = "已插入镜头，请生成新镜头的图片和配音，再渲染视频"
        project.transitionPlans.clear(); project.transitionPlanSignature = ""
        return shot
    }

    fun bundledAudio(id: String): File? {
        val assetName = when (id) {
            "sad" -> "悲伤BGM.mp3"
            "爽文", "爽文BGM" -> "爽文BGM_1.mp3"
            "gong" -> "铜锣_爱给网_aigei_com.mp3"
            else -> return null
        }
        val target = File(audioRoot, assetName)
        if (!target.isFile || target.length() < 1000) {
            appContext.assets.open("audio/$assetName").use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return target.takeIf { it.isFile && it.length() > 1000 }
    }

    fun keywordAudio(keyword: String): File? {
        val assetName = when (keyword) {
            "放屁" -> "放屁.mp3"
            "哼", "他娘的" -> "叮咚紧张.mp3"
            else -> return null
        }
        val target = File(audioRoot, "keyword_$assetName")
        if (!target.isFile || target.length() < 1000) {
            appContext.assets.open("audio/$assetName").use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return target.takeIf { it.isFile && it.length() > 1000 }
    }
    fun bundledFont(id: String): File {
        val assetName = when (id) {
            "cover" -> "STXINGKA.TTF"
            "subtitle" -> "msyhbd.ttc"
            else -> "simhei.ttf"
        }
        return bundledFontFile(assetName)
    }

    @Synchronized
    internal fun bundledFontFile(assetName: String): File {
        require(assetName in setOf("STXINGKA.TTF", "STKAITI.TTF", "simkai.ttf", "msyh.ttc", "msyhbd.ttc", "simhei.ttf"))
        val target = File(audioRoot, assetName)
        if (!target.isFile || target.length() < 1000) {
            val pending = File(audioRoot, "$assetName.pending")
            try {
                appContext.assets.open("fonts/$assetName").use { input -> pending.outputStream().use { input.copyTo(it) } }
                Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { pending.delete() }
        }
        return target
    }
}
