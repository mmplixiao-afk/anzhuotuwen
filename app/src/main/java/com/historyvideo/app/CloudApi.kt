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

    fun text(config: ProviderConfig, system: String, input: String, outputInstruction: String = ""): String {
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
        val result = JSONObject(post(endpoint(config.baseUrl, "/v1/chat/completions"), body, config.key,
            requestClient = textClient))
        val content = result.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
        require(content.isNotBlank()) { "模型返回了空内容" }
        return content
    }

    fun topics(config: ProviderConfig, prompt: String, direction: String): MutableList<String> {
        val raw = text(config, prompt, direction).removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "选题不是 JSON" }
        val json = JSONObject(raw.substring(start, end + 1))
        val array = json.optJSONArray("topics")
        val ideas = json.optJSONArray("ideas")
        val topics = mutableListOf<String>()
        if (array != null) for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotBlank()) topics += value
        }
        if (ideas != null) for (i in 0 until ideas.length()) {
            val item = ideas.opt(i)
            val value = if (item is JSONObject) item.optString("title") else item.toString()
            if (value.isNotBlank()) topics += value.trim()
        }
        require(topics.isNotEmpty()) { "选题 JSON 缺少 topics 或 ideas" }
        require(topics.size in 1..12) { "选题数量无效" }
        return topics
    }

    fun shots(config: ProviderConfig, prompt: String, input: String, expectedScript: String = input,
              onRetry: () -> Unit = {}): MutableList<Shot> {
        val taskPrompt = prompt + StoryboardRules.instruction(expectedScript)
        return checkedShots(expectedScript, onRetry) { retryInstruction ->
            text(config, taskPrompt + retryInstruction, input)
        }
    }

    internal fun checkedShots(expectedScript: String, onRetry: () -> Unit = {},
                              request: (String) -> String): MutableList<Shot> {
        var lastFailure = ""
        for (attempt in 0..1) {
            val retryInstruction = if (attempt == 0) "" else
                "\n上次分镜不合格：$lastFailure。严格按上述原文边界输出，每镜生成独立画面，原文不得增删。"
            // Only invalid model output is retried. A transport error may already be billed.
            val content = request(retryInstruction)
            try {
                return parseShots(content, expectedScript)
            } catch (error: JSONException) {
                lastFailure = "分镜JSON不完整或字段格式无效"
            } catch (error: IllegalArgumentException) {
                lastFailure = error.message.orEmpty()
            }
            if (attempt == 0) onRetry()
        }
        error("分镜重试一次后仍不合格：$lastFailure。未保存错误分镜，正文和原分镜已保留")
    }

    private fun parseShots(content: String, expectedScript: String): MutableList<Shot> {
        val raw = content.removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "分镜不是 JSON" }
        val array = JSONObject(raw.substring(start, end + 1)).getJSONArray("shots")
        require(array.length() in 1..100) { "分镜数量无效" }
        val allowed = setOf("character_closeup", "character_medium", "environment", "prop_closeup",
            "building", "crowd", "food", "money", "document", "landscape")
        val shots = mutableListOf<Shot>()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            val shot = Shot.from(obj).copy(
                text = obj.getString("voiceover"),
                visual = obj.getString("visual"),
                imagePrompt = obj.getString("image_prompt"),
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
            shots.add(shot)
        }
        StoryboardRules.validate(expectedScript, shots)
        return shots
    }

    fun image(config: ProviderConfig, prompt: String, out: File) {
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

    fun doubao(config: ProviderConfig, voiceId: String, speed: Float, spoken: String, out: File) {
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
        val pending = File(out.parentFile, out.name + ".tmp")
        pending.outputStream().use { stream ->
            val parts = parseDoubaoResponse(response)
            val errorCode = parts.codes.firstOrNull { it != 0 && it != 20000000 }
            if (errorCode != null) {
                val detail = parts.messages.firstOrNull().orEmpty()
                error("豆包 TTS 返回错误 $errorCode${detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}")
            }
            for (data in parts.audioChunks) {
                val normalized = data.replace("\\/", "/")
                stream.write(Base64.decode(normalized, Base64.DEFAULT))
            }
        }
        require(pending.length() > 1000) { "TTS 返回了空音频" }
        Files.move(pending.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
