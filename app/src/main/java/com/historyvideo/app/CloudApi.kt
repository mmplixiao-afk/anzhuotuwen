package com.historyvideo.app

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import android.graphics.BitmapFactory
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs

/** 单次分镜请求覆盖的 source unit 数量：分批控制输出长度，避免被服务端 max_tokens 截断。 */
private const val STORYBOARD_UNITS_PER_CALL = 40

private data class DoubaoResponseParts(
    val codes: List<Int>,
    val messages: List<String>,
    val audioChunks: List<String>,
)

private fun parseDoubaoResponse(response: String): DoubaoResponseParts {
    // Doubao normally returns concatenated JSON/SSE objects, but some responses
    // contain an invalid empty `message` value. Extract independent fields so a
    // valid audio chunk is not discarded because of that unrelated field.
    val codes = Regex("\\\"code\\\"\\s*:\\s*(-?\\d+)")
        .findAll(response)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .toList()
    val messages = Regex("\\\"(?:message|msg)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .findAll(response)
        .map { it.groupValues[1] }
        .filter { it.isNotBlank() }
        .toList()
    val audioChunks = Regex("\\\"data\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
        .findAll(response)
        .map { it.groupValues[1] }
        .filter { it.isNotBlank() }
        .toList()
    return DoubaoResponseParts(codes, messages, audioChunks)
}

private fun imageAspect(size: String): Double? = when (size.trim()) {
    "16:9", "1792x1024", "1536x864" -> 16.0 / 9.0
    "9:16", "1024x1792", "864x1536" -> 9.0 / 16.0
    "1:1", "1024x1024" -> 1.0
    else -> null
}

private fun cangyuanImageSize(config: ProviderConfig): String {
    val host = runCatching { URI(config.baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
    val model = config.model.trim().lowercase()
    val isCangyuan = host in setOf("ai.cangyuansuanli.cn", "www.ai.cangyuansuanli.cn", "direct-api.cangyuansuanli.cn")
    if (!isCangyuan || model !in setOf("gpt-image-2", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare")) {
        return config.imageSize.ifBlank { "1024x1792" }
    }
    return when (config.imageSize.trim()) {
        "16:9", "1792x1024" -> "1536x864"
        "9:16", "1024x1792" -> "864x1536"
        "1:1", "1024x1024" -> "1024x1024"
        else -> config.imageSize.ifBlank { "864x1536" }
    }
}

private fun imageRatioInstruction(size: String): String = when (imageAspect(size)) {
    16.0 / 9.0 -> "画幅硬性要求：最终图片必须是横屏 16:9，宽度明显大于高度，不要生成竖屏构图。"
    9.0 / 16.0 -> "画幅硬性要求：最终图片必须是竖屏 9:16，高度明显大于宽度。"
    1.0 -> "画幅硬性要求：最终图片必须是 1:1 正方形。"
    else -> ""
}

data class StoryboardResult(val title: String, val stylePreset: String, val shots: MutableList<Shot>)

class CloudApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .callTimeout(900, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val textClient = client.newBuilder().retryOnConnectionFailure(false).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun endpoint(base: String, suffix: String): String {
        val url = base.trim().trimEnd('/')
        require(url.startsWith("https://")) { "API Base URL 必须使用 HTTPS" }
        return when {
            url.endsWith(suffix) -> url
            url.endsWith("/v1") -> url + suffix.removePrefix("/v1")
            else -> url + suffix
        }
    }

    private fun post(url: String, body: JSONObject, key: String, doubao: Boolean = false,
                     model: String = "", requestClient: OkHttpClient = client): String {
        require(key.isNotBlank()) { "请先填写 API Key" }
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType)).apply {
                if (doubao) {
                    header("X-Api-Key", key)
                    header("X-Api-Resource-Id", model)
                    header("X-Api-Request-Id", UUID.randomUUID().toString().replace("-", ""))
                } else header("Authorization", "Bearer $key")
        }.build()
        requestClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = responseText.replace(Regex("\\s+"), " ").trim().take(300)
                error("API HTTP ${response.code}${detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
            }
            return responseText.ifBlank { error("API 响应为空") }
        }
    }

    private fun get(url: String, key: String): String {
        require(key.isNotBlank()) { "请先填写 API Key" }
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $key")
            .get().build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = responseText.replace(Regex("\\s+"), " ").trim().take(300)
                error("API HTTP ${response.code}${detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
            }
            return responseText.ifBlank { error("API 响应为空") }
        }
    }

    fun text(config: ProviderConfig, system: String, input: String, outputInstruction: String = "",
             onUsage: (TextUsage) -> Unit = {}): String {
        require(config.model.isNotBlank()) { "请设置文本模型 ID" }
        val taskPrompt = system.trim()
        require(taskPrompt.isNotBlank()) { "提示词不能为空" }
        val instruction = outputInstruction.trim()
        val userContent = buildString {
            append("请严格按照下面的【本次任务提示词】完成任务。它是本次请求的唯一内容规则，优先级高于模型默认风格；不得套用其他示例的字数、结构或主题。\n\n")
            append("【本次任务提示词】\n")
            append(taskPrompt)
            append("\n\n【本次输入】\n")
            append(input)
            if (instruction.isNotBlank()) {
                append("\n\n")
                append(instruction)
            }
        }
        val body = JSONObject().put("model", config.model).put("temperature", 0.6)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "你是一个严格执行用户任务提示词的生成器。"))
                .put(JSONObject().put("role", "user").put("content", userContent)))
        val url = endpoint(config.baseUrl, "/v1/chat/completions")
        require(config.key.isNotBlank()) { "请先填写 API Key" }
        val requestId = UUID.randomUUID().toString()
        onUsage(TextUsage(null, null, requestId))
        val response = post(url, body, config.key, requestClient = textClient)
        val result = JSONObject(response)
        onUsage(TextUsage.from(result).copy(requestId = requestId))
        val content = result.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
        require(content.isNotBlank()) { "模型返回了空内容" }
        return content
    }

    fun shots(config: ProviderConfig, prompt: String, input: String, expectedScript: String = input,
              granularity: String = "balanced",
              onRetry: () -> Unit = {}, onUsage: (TextUsage) -> Unit = {}): MutableList<Shot> {
        return storyboard(config, prompt, input, expectedScript, granularity, onRetry, onUsage).shots
    }

    fun storyboard(config: ProviderConfig, prompt: String, input: String, expectedScript: String = input,
                   granularity: String = "balanced",
                   onRetry: () -> Unit = {}, onUsage: (TextUsage) -> Unit = {}): StoryboardResult {
        val units = StoryboardRules.sourceUnits(expectedScript, granularity)
        // 长文按 source unit 分批；每批只在标点边界结束，合并后再校验全文分镜边界。
        val batches = mutableListOf<List<StoryboardRules.SourceUnit>>()
        var pendingBatch = mutableListOf<StoryboardRules.SourceUnit>()
        for (unit in units) {
            pendingBatch += unit
            if (pendingBatch.size >= STORYBOARD_UNITS_PER_CALL && StoryboardRules.endsAtPunctuation(unit.text)) {
                batches += pendingBatch.toList()
                pendingBatch = mutableListOf()
            }
        }
        if (pendingBatch.isNotEmpty()) batches += pendingBatch.toList()
        if (batches.isEmpty()) batches += units
        val merged = mutableListOf<Shot>()
        var title = ""
        var stylePreset = ""
        batches.forEachIndexed { batchIndex, batch ->
            // 批次内编号从 1 开始，模型只需要看本批的原文分段。
            val localUnits = batch.mapIndexed { index, unit ->
                StoryboardRules.SourceUnit(index + 1, unit.text, unit.fixedShot)
            }
            val result = try {
                storyboardBatch(config, prompt, input, expectedScript, localUnits, granularity,
                    batchIndex, batches.size, units.size, onRetry, onUsage)
            } catch (error: IllegalStateException) {
                throw IllegalStateException(
                    "第 ${batchIndex + 1}/${batches.size} 批分镜失败：${error.message}", error)
            }
            if (title.isBlank()) title = result.title
            if (stylePreset.isBlank()) stylePreset = result.stylePreset
            result.shots.forEach { shot -> merged += shot.copy(id = merged.size + 1) }
        }
        StoryboardRules.validate(expectedScript, merged, granularity)
        return StoryboardResult(title, stylePreset, merged)
    }

    private fun storyboardBatch(config: ProviderConfig, prompt: String, input: String,
                                expectedScript: String, units: List<StoryboardRules.SourceUnit>,
                                granularity: String, batchIndex: Int, batchCount: Int, totalUnits: Int,
                                onRetry: () -> Unit, onUsage: (TextUsage) -> Unit): StoryboardResult {
        val header = if (batchCount <= 1) "" else buildString {
            append("\n\n【分批生成】全文共 $totalUnits 个 source unit，本批是第 ${batchIndex + 1}/$batchCount 批，")
            append("对应全文第 ${batchIndex * STORYBOARD_UNITS_PER_CALL + 1}–")
            append(batchIndex * STORYBOARD_UNITS_PER_CALL + units.size)
            append(" 段；本批编号已从 1 重新开始。只输出本批的镜头，不要补全其他段落。\n")
        }
        val taskPrompt = prompt + header +
            StoryboardRules.instruction(expectedScript, units, granularity, firstBatch = batchIndex == 0)
        val sourceInput = buildString {
            append(input.trim())
            append("\n\n原文source_units（必须按编号连续分组，禁止复述文本）：\n")
            append(JSONArray().apply {
                units.forEach { unit -> put(JSONObject().put("id", unit.id)
                    .put("text", unit.text).put("fixed_shot", unit.fixedShot)) }
            }.toString())
        }
        // 首批用本批原文校验开头边界和钩子长度；完整结果合并后再做一次全文校验。
        val validateBatch = batchIndex == 0
        val batchScript = if (validateBatch) units.joinToString("") { it.text } else expectedScript
        return checkedStoryboard(batchScript, units, onRetry, enforceOriginalCopy = true,
            validateResult = validateBatch, granularity = granularity) { retryInstruction ->
            text(config, taskPrompt + retryInstruction, sourceInput, onUsage = onUsage)
        }
    }

    internal fun checkedShots(expectedScript: String, onRetry: () -> Unit = {},
                              request: (String) -> String): MutableList<Shot> =
        checkedShots(expectedScript, StoryboardRules.sourceUnits(expectedScript), onRetry, request)

    internal fun checkedShots(expectedScript: String, units: List<StoryboardRules.SourceUnit>,
                              onRetry: () -> Unit = {}, request: (String) -> String): MutableList<Shot> =
        checkedStoryboard(expectedScript, units, onRetry, request = request).shots

    internal fun checkedStoryboard(expectedScript: String, units: List<StoryboardRules.SourceUnit>,
                                   onRetry: () -> Unit = {}, enforceOriginalCopy: Boolean = false,
                                   validateResult: Boolean = true,
                                   granularity: String = "balanced",
                                   request: (String) -> String): StoryboardResult {
        var lastFailure = ""
        for (attempt in 0..1) {
            val retryInstruction = if (attempt == 0) "" else
                "\n上次分镜不合格：$lastFailure。请只为每镜返回连续完整的source_unit_ids编号分组和PC格式画面字段，不要输出或重写voiceover；每个source unit必须按顺序恰好覆盖一次。"
            // Only invalid model output is retried. A transport error may already be billed.
            val content = request(retryInstruction)
            try {
                return parseStoryboard(content, expectedScript, units, enforceOriginalCopy,
                    validateResult, granularity)
            } catch (error: JSONException) {
                lastFailure = "分镜JSON不完整或字段格式无效"
            } catch (error: IllegalArgumentException) {
                lastFailure = error.message.orEmpty()
            }
            if (attempt == 0) onRetry()
        }
        error("分镜重试一次后仍不合格：$lastFailure。未保存错误分镜，正文和原分镜已保留")
    }

    private fun parseStoryboard(content: String, expectedScript: String,
                                units: List<StoryboardRules.SourceUnit>,
                                enforceOriginalCopy: Boolean,
                                validateResult: Boolean = true,
                                granularity: String = "balanced"): StoryboardResult {
        val raw = content.removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val objectStart = raw.indexOf('{')
        val arrayStart = raw.indexOf('[')
        val start = listOf(objectStart, arrayStart).filter { it >= 0 }.minOrNull()
        require(start != null) { "分镜不是 JSON 对象或数组" }
        val parsed = try {
            org.json.JSONTokener(raw.substring(start)).nextValue()
        } catch (error: JSONException) {
            throw IllegalArgumentException("分镜 JSON 格式错误：${error.message ?: "无法解析"}", error)
        }
        val root = parsed as? JSONObject
        val array = when (parsed) {
            is JSONArray -> parsed
            is JSONObject -> parsed.optJSONArray("shots")
                ?: parsed.optJSONObject("data")?.optJSONArray("shots")
                ?: parsed.optJSONObject("result")?.optJSONArray("shots")
                ?: throw IllegalArgumentException("分镜 JSON 缺少 shots 数组")
            else -> throw IllegalArgumentException("分镜 JSON 根节点应为对象或数组")
        }
        require(array.length() in 1..300) { "分镜数量无效" }
        val allowed = setOf("character_closeup", "character_medium", "environment", "prop_closeup",
            "building", "crowd", "food", "money", "document", "landscape")
        val shots = mutableListOf<Shot>()
        val objects = (0 until array.length()).map { index ->
            array.optJSONObject(index) ?: throw IllegalArgumentException("第 ${index + 1} 镜应为 JSON 对象")
        }
        val hasUnitGroups = objects.any { it.has("source_unit_ids") || it.has("source_segments") }
        val unitGroups = if (hasUnitGroups) {
            require(!root?.optString("title").isNullOrBlank()) { "PC格式分镜缺少title字段" }
            require(!root?.optString("style_preset").isNullOrBlank()) { "PC格式分镜缺少style_preset字段" }
            objects.mapIndexed { index, obj ->
                val ids = obj.optJSONArray("source_unit_ids") ?: obj.optJSONArray("source_segments")
                    ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少source_unit_ids")
                (0 until ids.length()).map { idIndex ->
                    when (val value = ids.opt(idIndex)) {
                        is Number -> value.toInt().takeIf { value.toDouble() == it.toDouble() }
                        is String -> value.toIntOrNull()
                        else -> null
                    } ?: throw IllegalArgumentException("第 ${index + 1} 镜包含无效source unit编号")
                }
            }
        } else null
        val reconstructed = when {
            unitGroups != null -> StoryboardRules.reconstruct(units, unitGroups)
            enforceOriginalCopy -> {
                val legacyVoiceovers = objects.mapIndexed { index, obj ->
                    listOf("voiceover", "text")
                        .firstNotNullOfOrNull { key -> (obj.opt(key) as? String)?.takeIf { it.isNotBlank() } }
                        ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少voiceover/source_unit_ids")
                }
                StoryboardRules.reconstructVoiceoverSegments(expectedScript, legacyVoiceovers)
            }
            else -> null
        }
        for (index in objects.indices) {
            val obj = objects[index]
            val voiceover = reconstructed?.get(index) ?: listOf("voiceover", "text")
                .firstNotNullOfOrNull { key -> (obj.opt(key) as? String)?.takeIf { it.isNotBlank() } }
                ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少 voiceover/text 字段")
            val visual = listOf("visual", "visual_description")
                .firstNotNullOfOrNull { key -> (obj.opt(key) as? String)?.takeIf { it.isNotBlank() } }
                ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少 visual 字段")
            val imagePrompt = listOf("image_prompt", "imagePrompt", "prompt")
                .firstNotNullOfOrNull { key -> (obj.opt(key) as? String)?.takeIf { it.isNotBlank() } }
                ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少 image_prompt 字段")
            val idValue = obj.opt("id").takeUnless { it == null || it == JSONObject.NULL }
                ?: obj.opt("shot_id").takeUnless { it == null || it == JSONObject.NULL }
            val id = when (idValue) {
                is Number -> idValue.toInt().takeIf { idValue.toDouble() == it.toDouble() }
                is String -> idValue.toIntOrNull()
                else -> null
            } ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少有效的 id/shot_id 字段")
            val subtitleChunks = if (unitGroups != null) {
                val subtitleUnitArray = obj.optJSONArray("subtitle_unit_groups")
                    ?: throw IllegalArgumentException("第 ${index + 1} 镜缺少subtitle_unit_groups字段")
                val subtitleGroups = (0 until subtitleUnitArray.length()).map { groupIndex ->
                    val group = subtitleUnitArray.optJSONArray(groupIndex)
                        ?: throw IllegalArgumentException("第 ${index + 1} 镜的字幕分组格式无效")
                    (0 until group.length()).map { unitIndex ->
                        when (val value = group.opt(unitIndex)) {
                            is Number -> value.toInt().takeIf { value.toDouble() == it.toDouble() }
                            is String -> value.toIntOrNull()
                            else -> null
                        } ?: throw IllegalArgumentException("第 ${index + 1} 镜字幕分组含有无效source unit编号")
                    }
                }
                StoryboardRules.reconstructSubtitleChunks(units, unitGroups[index], subtitleGroups)
            } else null
            val baseShot = Shot.from(obj)
            val shot = baseShot.copy(
                id = id,
                text = voiceover,
                visual = visual,
                punch = obj.optString("punch"),
                videoPrompt = obj.optString("video_prompt"),
                imagePrompt = imagePrompt,
                subtitleChunks = subtitleChunks?.toMutableList() ?: baseShot.subtitleChunks,
                imageStatus = "pending", audioStatus = "pending", error = "",
                cutoutEnabled = false, motionPlan = null, cutoutMirrorX = null,
                cutoutBackgroundPlan = null, cutoutForegroundPlan = null,
                imageRevision = "", audioRevision = ""
            )
            require(shot.id == index + 1 && shot.text.isNotBlank() && shot.imagePrompt.isNotBlank()) {
                "第 ${index + 1} 镜缺少必要字段"
            }
            require(shot.shotType in allowed) { "第 ${index + 1} 镜的画面类型无效" }
            require(shot.duration in 0.5..30.0) { "第 ${index + 1} 镜时长无效" }
            if (hasUnitGroups) {
                require(shot.punch.codePointCount(0, shot.punch.length) in 2..6) {
                    "第 ${index + 1} 镜punch应为2到6个字符"
                }
                require(shot.videoPrompt.isNotBlank()) { "第 ${index + 1} 镜缺少video_prompt字段" }
            }
            shots.add(shot)
        }
        if (validateResult) StoryboardRules.validate(expectedScript, shots, granularity)
        return StoryboardResult(root?.optString("title").orEmpty(), root?.optString("style_preset").orEmpty(), shots)
    }

    fun image(config: ProviderConfig, prompt: String, out: File, onGenerated: () -> Unit = {}) {
        require(config.model.isNotBlank()) { "请设置生图模型 ID" }
        val requestSize = cangyuanImageSize(config)
        val instruction = imageRatioInstruction(config.imageSize)
        val finalPrompt = if (instruction.isBlank()) prompt else "$prompt\n\n$instruction"
        val result = if (isCangyuan(config)) {
            cangyuanImage(config, finalPrompt, requestSize)
        } else {
            val body = JSONObject().put("model", config.model).put("prompt", finalPrompt)
                .put("size", requestSize).put("n", 1)
                .put("response_format", "b64_json")
                .put("stream", false)
                .put("watermark", false)
                .put("sequential_image_generation", "disabled")
            JSONObject(post(endpoint(config.baseUrl, "/v1/images/generations"), body, config.key))
        }
        val items = result.optJSONArray("data") ?: result.optJSONArray("images")
            ?: error("生图接口没有返回 data")
        require(items.length() > 0) { "生图接口返回了空图片列表" }
        val item = items.getJSONObject(0)
        require(item.optString("b64_json").isNotBlank() || item.optString("url").isNotBlank()) { "生图接口没有返回图片" }
        onGenerated()
        val bytes = when {
            item.optString("b64_json").isNotBlank() ->
                Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
            item.optString("url").isNotBlank() -> download(item.getString("url"))
            else -> error("生图接口没有返回图片")
        }
        require(bytes.size in 1_000..20_000_000) { "图片大小无效" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "下载内容不是有效图片" }
        val expectedAspect = imageAspect(config.imageSize)
        if (expectedAspect != null) {
            val actualAspect = bounds.outWidth.toDouble() / bounds.outHeight.toDouble()
            require(abs(actualAspect - expectedAspect) <= 0.04) {
                "图片接口返回 ${bounds.outWidth}×${bounds.outHeight}，与所选画幅不符"
            }
        }
        val pending = File(out.parentFile, out.name + ".tmp")
        pending.writeBytes(bytes)
        Files.move(pending.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun isCangyuan(config: ProviderConfig): Boolean {
        val host = runCatching { URI(config.baseUrl).host.orEmpty().lowercase() }.getOrDefault("")
        return host in setOf("ai.cangyuansuanli.cn", "www.ai.cangyuansuanli.cn", "direct-api.cangyuansuanli.cn")
    }

    /**
     * The Cangyuan relay can keep a synchronous image request open long enough
     * to be terminated by its gateway. Submit a task first, then poll the same
     * task so a gateway timeout does not create a second paid generation.
     */
    private fun cangyuanImage(config: ProviderConfig, prompt: String, requestSize: String): JSONObject {
        val taskEndpoint = endpoint(config.baseUrl, "/v1/images/generations")
        val body = JSONObject().put("model", config.model).put("prompt", prompt)
            .put("size", requestSize).put("n", 1)
            .put("response_format", "url")
            .put("async", true)
        var result = JSONObject(post(taskEndpoint, body, config.key))
        fun hasImage(value: JSONObject): Boolean {
            val data = value.optJSONArray("data") ?: value.optJSONArray("images") ?: return false
            if (data.length() == 0) return false
            val item = data.optJSONObject(0) ?: return false
            return item.optString("url").isNotBlank() || item.optString("b64_json").isNotBlank()
        }
        if (hasImage(result)) return result
        val firstItem = (result.optJSONArray("data") ?: result.optJSONArray("images"))
            ?.optJSONObject(0)
        val taskId = result.optString("id")
            .ifBlank { result.optString("task_id") }
            .ifBlank { firstItem?.optString("id").orEmpty() }
            .ifBlank { firstItem?.optString("task_id").orEmpty() }
            .trim()
        require(taskId.isNotBlank()) { "生图接口未返回任务 ID，未自动重试以避免重复扣费" }
        repeat(120) {
            val status = result.optString("status").lowercase()
            if (status in setOf("failed", "error", "cancelled", "canceled")) {
                val detail = result.opt("error") ?: result.opt("message") ?: status
                error("生图任务失败：$detail")
            }
            if (hasImage(result)) return result
            Thread.sleep(2_500L)
            result = JSONObject(get("$taskEndpoint/$taskId", config.key))
        }
        error("生图任务等待超时，任务 ID：$taskId；未重复提交")
    }

    private fun download(url: String): ByteArray {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null) { "图片链接必须使用 HTTPS" }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载图片 HTTP ${response.code}")
            val source = response.body?.byteStream() ?: error("图片响应为空")
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 20_000_000) { "图片超过 20 MB" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    fun doubao(config: ProviderConfig, voiceId: String, speed: Float, spoken: String, out: File,
               onGenerated: () -> Unit = {}) {
        val url = config.baseUrl.ifBlank { "https://openspeech.bytedance.com/api/v3/tts/unidirectional" }
        require(url.startsWith("https://")) { "TTS URL 必须使用 HTTPS" }
        val additions = JSONObject()
            .put("disable_markdown_filter", true)
            .put("enable_language_detector", true)
            .put("enable_latex_tn", true)
            .put("disable_default_bit_rate", true)
            .put("max_length_to_filter_parenthesis", 0)
            .put("cache_config", JSONObject().put("text_type", 1).put("use_cache", true))
        if (config.model == "seed-icl-2.0") additions.put("tone_fidelity", true)
        val requestParams = JSONObject()
            .put("text", spoken)
            .put("speaker", voiceId)
            .put("audio_params", JSONObject().put("format", "mp3")
                .put("sample_rate", 24000).put("bit_rate", 128000)
                .put("speech_rate", ((speed - 1f) * 100).toInt().coerceIn(-50, 100))
                .put("loudness_rate", 0))
            .put("additions", additions.toString())
        if (config.model == "seed-icl-2.0") {
            requestParams.put("model", "seed-tts-2.0-expressive")
        }
        val body = JSONObject().put("user", JSONObject().put("uid", "rensheng-fuben"))
            .put("req_params", requestParams)
        val response = post(url, body, config.key, true,
            config.model.ifBlank { "volc.service_type.10029" })
        val parts = parseDoubaoResponse(response)
        val errorCode = parts.codes.firstOrNull { it != 0 && it != 20000000 }
        if (errorCode != null) {
            val detail = parts.messages.firstOrNull().orEmpty()
            error("豆包 TTS 返回错误 $errorCode${detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
        }
        require(parts.audioChunks.isNotEmpty()) { "TTS 返回了空音频" }
        onGenerated()
        val pending = File(out.parentFile, out.name + ".tmp")
        pending.outputStream().use { stream ->
            for (data in parts.audioChunks) {
                val normalized = data.replace("\\/", "/")
                stream.write(Base64.decode(normalized, Base64.DEFAULT))
            }
        }
        require(pending.length() > 1000) { "TTS 返回了空音频" }
        Files.move(pending.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
