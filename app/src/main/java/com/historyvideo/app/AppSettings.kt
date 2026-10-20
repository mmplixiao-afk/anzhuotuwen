package com.historyvideo.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val model: String,
    val key: String,
    val groupId: String = "",
    val imageSize: String = "1024x1792"
)

class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "video_provider_keys"

    private fun masterKey() = (keyStore.getKey(alias, null) ?: run {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generator.generateKey()
    }) as javax.crypto.SecretKey

    private fun secret(name: String): String {
        val value = prefs.getString("key_$name", null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun saveSecret(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("key_$name", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun hasApiKey(name: String): Boolean = secret(name).isNotBlank()

    fun provider(name: String): ProviderConfig {
        val storedName = prefs.getString("${name}_name", null).orEmpty()
        return ProviderConfig(
        if (storedName.isBlank() || storedName == name) defaultProviderName(name) else storedName,
        prefs.getString("${name}_url", null)?.takeIf { it.isNotBlank() } ?: defaultBaseUrl(name),
        prefs.getString("${name}_model", null)?.takeIf { it.isNotBlank() } ?: defaultModel(name), secret(name),
        prefs.getString("${name}_group_id", "").orEmpty(),
        prefs.getString("${name}_image_size", null)?.takeIf { it.isNotBlank() } ?: defaultImageSize(name)
        )
    }

    fun saveProvider(name: String, config: ProviderConfig) {
        prefs.edit().putString("${name}_name", config.name)
            .putString("${name}_url", config.baseUrl.trim())
            .putString("${name}_model", config.model.trim())
            .putString("${name}_group_id", config.groupId.trim())
            .putString("${name}_image_size", config.imageSize.trim()).apply()
        if (config.key.isNotBlank()) saveSecret(name, config.key.trim())
    }

    fun prompt(name: String): String = prefs.getString("prompt_$name", null) ?: defaultPrompt(name)
    fun savePrompt(name: String, value: String) {
        check(prefs.edit().putString("prompt_$name", value).commit()) { "提示词保存失败" }
    }
    fun resetPrompt(name: String) {
        check(prefs.edit().remove("prompt_$name").commit()) { "提示词恢复失败" }
    }
    fun defaultPrompt(name: String): String = runCatching {
        appContext.assets.open("prompts/$name.md").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse { legacyDefaultPrompt(name) }
    var voiceId: String
        get() {
            val value = prefs.getString("voice_id", null).orEmpty()
            return if (value.isBlank() || value == "zh_male_beijingxiaoye_emo_v2_mars_bigtts") {
                "S_BqpkLjoe2"
            } else value
        }
        set(value) { prefs.edit().putString("voice_id", value).apply() }
    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) { prefs.edit().putFloat("speed", value).apply() }
    var concurrency: Int
        get() = prefs.getInt("concurrency", 5)
        set(value) { prefs.edit().putInt("concurrency", value).apply() }
    var imageUnitPrice: String
        get() = prefs.getString("image_unit_price", "0.017").orEmpty()
        set(value) {
            val price = value.toBigDecimalOrNull()
            require(price != null && price >= java.math.BigDecimal.ZERO && price.scale() <= 6) {
                "图片单价需为非负数字，最多 6 位小数"
            }
            check(prefs.edit().putString("image_unit_price", price.toPlainString()).commit()) { "图片单价保存失败" }
        }

    companion object {
        val types = listOf("script", "shots", "image")
        fun defaultProviderName(name: String) = when (name) {
            "text" -> "OpenAI Compatible"
            "image" -> "OpenAI Images API"
            "tts" -> "豆包语音 / 火山 TTS"
            else -> name
        }

        // Match the working desktop configuration for a fresh phone install.
        fun defaultBaseUrl(name: String) = when (name) {
            "text" -> "https://ai.cangyuansuanli.cn"
            "image" -> "https://direct-api.cangyuansuanli.cn/v1"
            "tts" -> "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
            else -> ""
        }

        fun defaultModel(name: String) = when (name) {
            "text" -> "gemini-3.8-flash-high"
            "image" -> "gpt-image-2"
            "tts" -> "seed-icl-2.0"
            else -> ""
        }

        fun defaultImageSize(name: String) = if (name == "image") "1792x1024" else "1024x1792"

        private fun legacyDefaultPrompt(name: String) = when (name) {
            "topic" -> "你是历史短视频选题策划。根据用户给出的方向，一次给出6个适合手机短视频的中文题材。题材要具体、有冲突、有画面感，避免重复。只输出严格JSON对象：{\"topics\":[\"题材1\",\"题材2\",\"题材3\",\"题材4\",\"题材5\",\"题材6\"]}。"
            "script" -> "你是古代历史短视频口播作者。根据题材写约500字中文文案。使用第二人称，前3至5秒有冲突，口语化、强钩子、信息密集，每20至30秒有新信息或反转，不要长时间铺垫。只输出可直接配音的文案。"
            "shots" -> "你是写实古装短视频分镜导演。口播原文以source_units提供，只将相邻连续编号分组成镜，不输出voiceover，不得复述、增删或改写。字幕用subtitle_unit_groups按自然停顿分组本镜编号。默认平衡粒度每镜约20–35字、原则不超过40字；按动作、情绪、场景变化拆分。第一镜只放主题句；开头钩子按PC规则优先匹配语义分镜；固定source_unit不得拆并。每镜按PC格式输出visual、2–6字punch、100–180字image_prompt及video_prompt；前两镜有人物、人物外观统一、画面无文字并留字幕区。输出title、style_preset和shots严格JSON。"
            else -> "中国古代真人影视剧照质感，写实比例，自然光，服装和建筑器物符合时代；单一清晰主体，画幅9:16，画面底部留字幕空间，不生成文字、水印。"
        }
    }
}
