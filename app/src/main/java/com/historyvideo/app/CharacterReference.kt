package com.historyvideo.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class StoryCharacter(
    val name: String,
    val appearance: String,
    val role: String = "",
    val sex: String = "",
    val id: String = "",
    val aliases: List<String> = emptyList()
) {
    fun json() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("appearance", appearance)
        .put("role", role)
        .put("sex", sex)
        .put("aliases", JSONArray(aliases))

    companion object {
        fun from(value: JSONObject) = StoryCharacter(
            name = value.optString("name").trim().ifBlank { "主角" },
            appearance = value.optString("appearance", value.optString("description")).trim(),
            role = value.optString("role").trim(),
            sex = value.optString("sex").trim(),
            id = value.optString("id").trim(),
            aliases = readAliases(value)
        )

        private fun readAliases(value: JSONObject): List<String> {
            val array = value.optJSONArray("aliases") ?: value.optJSONArray("alias")
            if (array != null) {
                return (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotBlank() }
            }
            return value.optString("aliases").split('、', ',', '，', '/', '|')
                .map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}

data class CharacterReference(
    var name: String = "主角",
    var remoteUrl: String = "",
    var status: String = "pending",
    var appearance: String = "",
    var prompt: String = "",
    var role: String = "",
    var sex: String = "",
    var fileKey: String = "",
    var promptCustom: Boolean = false,
    var characterId: String = "",
    var aliases: MutableList<String> = mutableListOf()
) {
    fun json() = JSONObject().apply {
        put("name", name)
        put("remote_url", remoteUrl)
        put("status", status)
        put("appearance", appearance)
        put("prompt", prompt)
        put("role", role)
        put("sex", sex)
        put("file_key", fileKey)
        put("prompt_custom", promptCustom)
        put("character_id", characterId)
        put("aliases", JSONArray(aliases))
    }

    companion object {
        fun from(value: JSONObject) = CharacterReference(
            name = value.optString("name").trim().ifBlank { "主角" },
            remoteUrl = persistedImageUrl(value.optString("remote_url")),
            status = value.optString("status").trim().ifBlank { "pending" },
            appearance = value.optString("appearance"),
            prompt = value.optString("prompt"),
            role = value.optString("role"),
            sex = value.optString("sex"),
            fileKey = value.optString("file_key").trim(),
            promptCustom = if (value.has("prompt_custom")) value.optBoolean("prompt_custom")
            else value.optString("prompt").trim().isNotEmpty(),
            characterId = value.optString("character_id").trim(),
            aliases = mutableListOf<String>().apply {
                val array = value.optJSONArray("aliases")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val alias = array.optString(index).trim()
                        if (alias.isNotBlank()) add(alias)
                    }
                }
            }
        )
    }
}

internal enum class ShotImageRoute { TEXT, EDIT, BLOCKED }

internal data class PlannedShot(val shotId: Int, val route: ShotImageRoute)

internal data class ImageBatchPlan(val generateSheet: Boolean, val shots: List<PlannedShot>)

internal data class MultipartEditSpec(val fields: Map<String, String>, val fileField: String)

private val CANGYUAN_HOSTS = setOf(
    "ai.cangyuansuanli.cn",
    "www.ai.cangyuansuanli.cn",
    "direct-api.cangyuansuanli.cn"
)

internal fun isCangyuanImageHost(baseUrl: String): Boolean {
    val host = runCatching { URI(baseUrl.trim()).host.orEmpty().lowercase() }.getOrDefault("")
    return host in CANGYUAN_HOSTS
}

internal fun isPublicHttpsImageUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank() || trimmed.startsWith("data:", true) || trimmed.startsWith("file:", true) || trimmed.startsWith("/")) {
        return false
    }
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    if (!uri.isAbsolute || !uri.scheme.equals("https", true)) return false
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank() || host == "localhost" || host == "127.0.0.1" || host.endsWith(".local")) return false
    return true
}

internal fun persistedImageUrl(raw: String?): String =
    raw?.trim().orEmpty().takeIf { isPublicHttpsImageUrl(it) }.orEmpty()

internal fun referenceAssetUsable(bytes: Long, remoteUrl: String, requiresPublicUrl: Boolean): Boolean {
    if (bytes <= 1_000L) return false
    if (requiresPublicUrl && !isPublicHttpsImageUrl(remoteUrl)) return false
    return true
}

/**
 * Missing character fields stay false. Shot type or has_person alone never means “this is the protagonist”.
 */
internal fun usesProtagonistReference(value: JSONObject): Boolean {
    if (value.has("uses_protagonist_reference")) return value.optBoolean("uses_protagonist_reference")
    val label = listOf("character_ref", "subject").firstNotNullOfOrNull { key ->
        value.optString(key).trim().takeIf { it.isNotBlank() }
    } ?: return false
    return label.equals("protagonist", true) || label == "主角"
}

internal fun protagonistAppearance(characters: List<StoryCharacter>): String {
    val usable = characters.filter { it.appearance.isNotBlank() }
    usable.firstOrNull { it.role.equals("protagonist", true) || it.name == "主角" }?.let { return it.appearance }
    if (usable.size == 1 && usable[0].role.isBlank()) return usable[0].appearance
    return ""
}

internal enum class ProtagonistSex { FEMALE, MALE, UNSPECIFIED }

private val femaleMarkers = listOf(
    "女性", "女人", "女子", "女士", "女主", "美女", "姑娘", "少女", "少妇", "妇人", "闺女",
    "娘娘", "皇后", "太后", "公主", "王妃", "贵妃", "妃子", "夫人", "小姐", "丫鬟", "宫女", "尼姑"
)
private val explicitMaleMarkers = listOf("男性", "男人", "男子", "男士", "男主")
private val maleRoleMarkers = listOf("汉子", "书生", "公子", "皇帝", "皇上", "王爷", "和尚", "太监")
private val beardTerms = listOf("络腮胡", "络腮", "美髯", "长须", "短须", "胡须", "胡子", "八字须")

private fun containsUnprefixed(text: String, token: String, prefix: Char): Boolean {
    var index = text.indexOf(token)
    while (index >= 0) {
        if (index == 0 || text[index - 1] != prefix) return true
        index = text.indexOf(token, index + token.length)
    }
    return false
}

/** Gender comes only from the appearance text. Beard words do not override an explicit woman. */
internal fun protagonistSex(appearance: String): ProtagonistSex {
    val text = appearance.trim()
    if (text.isEmpty()) return ProtagonistSex.UNSPECIFIED
    val female = femaleMarkers.any { text.contains(it) } || containsUnprefixed(text, "她", '其')
    val explicitMale = explicitMaleMarkers.any { text.contains(it) } || containsUnprefixed(text, "他", '其')
    val maleRole = maleRoleMarkers.any { text.contains(it) }
    if (female && (explicitMale || maleRole)) return ProtagonistSex.UNSPECIFIED
    if (female) return ProtagonistSex.FEMALE
    val male = explicitMale || maleRole || beardTerms.any { text.contains(it) }
    return if (male) ProtagonistSex.MALE else ProtagonistSex.UNSPECIFIED
}

internal fun appearanceForSheet(appearance: String, sex: ProtagonistSex): String {
    var text = appearance.trim()
    if (sex == ProtagonistSex.FEMALE) {
        beardTerms.sortedByDescending { it.length }.forEach { term -> text = text.replace(term, "") }
        text = text.replace(Regex("[，,、；;\\s]+"), "，").trim('，', ' ', '。', '、', '；', ';')
    }
    return text
}

internal fun sheetAppearance(characters: List<StoryCharacter>, flaggedPrompts: List<String>): String {
    val explicit = protagonistAppearance(characters)
    if (explicit.isNotBlank()) return explicit
    return flaggedPrompts.map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString("\n")
        .ifBlank { "写实中国古代主角。只保留文中已经写明的年龄、脸型、发式和服装。" }
}

internal fun planImageBatch(shots: List<Pair<Int, Boolean>>, referenceReady: Boolean): ImageBatchPlan {
    val generateSheet = !referenceReady && shots.any { it.second }
    val planned = shots.map { (id, usesReference) ->
        PlannedShot(id, shotImageRoute(usesReference, referenceReady || generateSheet))
    }
    return ImageBatchPlan(generateSheet, planned)
}

internal fun shotImageRoute(usesProtagonist: Boolean, referenceReady: Boolean): ShotImageRoute = when {
    !usesProtagonist -> ShotImageRoute.TEXT
    referenceReady -> ShotImageRoute.EDIT
    else -> ShotImageRoute.BLOCKED
}

internal fun imageErrorCanResubmit(message: String?): Boolean {
    val detail = message.orEmpty().lowercase()
    if (detail.contains("未重复提交") || detail.contains("任务 id") || detail.contains("https 地址")) return false
    return detail.contains("timeout") || detail.contains("timed out") ||
        detail.contains("连接") || detail.contains("connection") ||
        detail.contains("http 408") || detail.contains("http 429")
}

internal fun imagePollTimeout(taskId: String) = "生图任务等待超时，任务 ID：$taskId；未重复提交"

internal fun cangyuanPollUrl(createEndpoint: String, taskId: String): String {
    val id = taskId.trim()
    require(id.isNotBlank()) { "生图接口未返回任务 ID，未自动重试以避免重复扣费" }
    return createEndpoint.trimEnd('/') + "/" + id
}

internal fun cangyuanGenerationRequestBody(model: String, prompt: String, size: String): JSONObject =
    JSONObject()
        .put("model", model)
        .put("prompt", prompt)
        .put("n", 1)
        .put("size", size)
        .put("response_format", "url")
        .put("async", true)

internal fun referenceImageUrls(urls: List<String>): List<String> {
    val clean = urls.map { it.trim() }.filter { isPublicHttpsImageUrl(it) }.distinct().take(9)
    require(clean.isNotEmpty()) { "人物参考图缺少可公开访问的 HTTPS 地址" }
    return clean
}

internal fun cangyuanEditRequestBody(model: String, prompt: String, size: String, images: List<String>): JSONObject =
    cangyuanGenerationRequestBody(model, prompt, size).put("images", JSONArray(referenceImageUrls(images)))

internal fun referenceFormField(count: Int): String {
    require(count in 1..9) { "人物参考图最多 9 张" }
    return if (count == 1) "image" else "image[]"
}

internal fun multipartEditSpec(model: String, prompt: String, size: String, fileCount: Int) = MultipartEditSpec(
    fields = linkedMapOf(
        "model" to model,
        "prompt" to prompt,
        "n" to "1",
        "size" to size,
        "response_format" to "b64_json"
    ),
    fileField = referenceFormField(fileCount)
)

internal fun parseStoryCharacters(root: JSONObject?): List<StoryCharacter> {
    val array = root?.optJSONArray("characters") ?: return emptyList()
    val result = mutableListOf<StoryCharacter>()
    for (index in 0 until array.length()) {
        if (result.size == 12) break
        val item = array.optJSONObject(index) ?: continue
        val character = StoryCharacter.from(item)
        val named = character.name.isNotBlank() && !genericCharacterName(character.name)
        if (character.appearance.isNotBlank() || named) result += character
    }
    return result
}

internal const val CHARACTER_REFERENCE_LOCK =
    "输入图片只作为人物的脸型、发式、服装和年龄参考。参考图里没有的特征不要自行添加，没有胡须就不要加胡须。不要复制它的构图或背景。请根据镜头描述生成一张全新的写实古装场景。"

internal fun characterSheetRequestPrompt(savedPrompt: String, computedPrompt: String): String {
    val saved = savedPrompt.trim()
    return if (saved.isNotEmpty()) saved else computedPrompt.trim()
}

/** Saves the edited sheet prompt and leaves the existing image, URL, and status untouched. */
internal fun storeReferencePrompt(reference: CharacterReference, prompt: String) {
    reference.prompt = prompt.trim()
    reference.promptCustom = true
}

internal fun buildCharacterSheetPrompt(globalImagePrompt: String, stylePreset: String, appearance: String): String {
    val sex = protagonistSex(appearance)
    val described = appearanceForSheet(appearance, sex).ifBlank {
        "只使用文中已经写明的年龄、脸型、发式和服装"
    }
    val gender = when (sex) {
        ProtagonistSex.FEMALE -> "主角是女性，按女性的脸、发式、体态和服装来画。"
        ProtagonistSex.MALE -> "主角是男性，按男性的脸、发式、体态和服装来画。"
        ProtagonistSex.UNSPECIFIED -> "这段描述没有写明主角性别。只画已经写明的特征，不要默认画成男性，不要自行添加胡须。"
    }
    val sheet = "为主角生成一张人物参考图。$gender 光线清楚，正面或三分之四侧面，中景或全身，服装统一。外观：$described。" +
        "写实中国古代影视剧照，不要Q版、漫画或图集拼贴。画面不要文字、字幕、Logo或水印。背景简洁，不要复杂剧情场景。"
    return listOf(globalImagePrompt, stylePreset, sheet).filter { it.isNotBlank() }.joinToString("\n")
}

internal fun buildShotImagePrompt(
    globalImagePrompt: String,
    stylePreset: String,
    shotPrompt: String,
    useReference: Boolean,
    referenceNames: List<String> = emptyList()
): String {
    val lock = if (useReference) CHARACTER_REFERENCE_LOCK else ""
    val identity = if (useReference) referenceIdentityLine(referenceNames) else ""
    return listOf(globalImagePrompt, stylePreset, lock, identity, shotPrompt).filter { it.isNotBlank() }.joinToString("\n")
}

internal fun referenceBlockedMessage(requiresPublicUrl: Boolean): String =
    if (requiresPublicUrl) "主角参考图没有可公开访问的 HTTPS 地址，本镜已停止，不会改用无参考图生图"
    else "主角参考图文件无效，本镜已停止，不会改用无参考图生图"

/**
 * One paid reference generation per task. Concurrent callers wait and reuse the result or the same failure.
 */
internal class ReferenceGenerationGate {
    private val mutex = Mutex()
    private var failure: Throwable? = null

    fun reset() {
        failure = null
    }

    suspend fun ensure(
        needed: Boolean,
        ready: () -> Boolean,
        cancelled: () -> Boolean,
        generate: suspend () -> Unit
    ) {
        if (!needed) return
        if (cancelled()) throw CancellationException("已暂停")
        mutex.withLock {
            if (cancelled()) throw CancellationException("已暂停")
            if (ready()) return
            failure?.let { throw it }
            try {
                generate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                failure = error
                throw error
            }
        }
    }
}
