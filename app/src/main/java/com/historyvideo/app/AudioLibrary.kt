package com.historyvideo.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AudioKind(val key: String, val label: String) {
    BGM("bgm", "背景音乐"), SFX("sfx", "音效")
}

data class AudioAsset(
    val id: String,
    val name: String,
    val kind: AudioKind,
    val fileName: String = "",
    val builtIn: Boolean = false
) {
    fun json() = JSONObject().apply {
        put("id", id); put("name", name); put("kind", kind.key); put("file_name", fileName)
    }

    companion object {
        fun from(value: JSONObject): AudioAsset? {
            val kind = AudioKind.values().firstOrNull { it.key == value.optString("kind") } ?: return null
            val id = value.optString("id")
            val fileName = value.optString("file_name")
            if (!id.startsWith("user:") || fileName.isBlank()) return null
            return AudioAsset(id, value.optString("name").ifBlank { fileName }, kind, fileName)
        }
    }
}

/** User imports are copied into app-private storage so document-provider URIs are never needed during rendering. */
class AudioLibrary(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("audio_library", Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, "audio_assets/imported").apply { mkdirs() }

    fun assets(kind: AudioKind): List<AudioAsset> = builtIns.filter { it.kind == kind } + imported().filter { it.kind == kind }

    fun asset(id: String): AudioAsset? = builtIns.firstOrNull { it.id == id } ?: imported().firstOrNull { it.id == id }

    fun resolve(id: String): File? {
        val imported = imported().firstOrNull { it.id == id }
        if (imported != null) {
            val file = File(root, imported.fileName).canonicalFile
            return file.takeIf { file.parentFile == root.canonicalFile && it.isFile && it.length() > 1000 }
        }
        return when (id) {
            "放屁", "哼", "他娘的" -> ProjectStore(appContext).keywordAudio(id.trim())
            "keyword:tense" -> ProjectStore(appContext).keywordAudio("哼")
            else -> ProjectStore(appContext).bundledAudio(id)
        }
    }

    fun importAudio(uri: Uri, kind: AudioKind): AudioAsset {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty().ifBlank { "导入音频" }
        val nameExtension = displayName.substringAfterLast('.', "").lowercase()
        val extension = nameExtension.ifBlank {
            when (mime.lowercase()) {
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/mp4", "audio/x-m4a" -> "m4a"
                "audio/aac" -> "aac"
                "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "wav"
                "audio/ogg" -> "ogg"
                "audio/flac", "audio/x-flac" -> "flac"
                else -> ""
            }
        }
        require(mime.startsWith("audio/") || extension in supportedExtensions) { "请选择音频文件" }
        require(extension in supportedExtensions) { "支持 MP3、M4A、AAC、WAV、OGG、OPUS、FLAC 格式" }
        val id = "user:${UUID.randomUUID()}"
        val fileName = "${id.substringAfter(':')}.$extension"
        val target = File(root, fileName)
        val input = resolver.openInputStream(uri) ?: error("无法读取所选音频")
        try {
            input.use { source -> target.outputStream().buffered().use { output -> source.copyTo(output) } }
            require(target.isFile && target.length() > 1000L) { "所选音频为空或文件不完整" }
            val asset = AudioAsset(id, displayName.substringBeforeLast('.', displayName), kind, fileName)
            val items = imported().toMutableList().apply { add(asset) }
            check(prefs.edit().putString(KEY_ITEMS, JSONArray().apply { items.forEach { put(it.json()) } }.toString()).commit()) {
                "保存音频资料失败"
            }
            return asset
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim().take(80)
        require(clean.isNotBlank()) { "音频名称不能为空" }
        val items = imported().toMutableList()
        val index = items.indexOfFirst { it.id == id }
        require(index >= 0) { "内置音频不能重命名" }
        items[index] = items[index].copy(name = clean)
        save(items)
    }

    fun delete(id: String, projects: List<VideoProject>): Boolean {
        require(builtIns.none { it.id == id }) { "内置音频不能删除" }
        require(!isUsed(id, projects)) { "有项目正在使用此音频，请先更换项目音轨" }
        val items = imported().toMutableList()
        val removed = items.firstOrNull { it.id == id } ?: return false
        save(items.filterNot { it.id == id })
        File(root, removed.fileName).delete()
        return true
    }

    fun isUsed(id: String, projects: List<VideoProject>): Boolean = projects.any { project ->
        project.bgmId == id || project.introSfxId == id || project.shots.any { shot ->
            shot.soundEffect == id || shot.soundEffects.any { it.assetId == id }
        }
    }

    private fun imported(): List<AudioAsset> {
        val array = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(AudioAsset::from) }
    }

    private fun save(items: List<AudioAsset>) {
        check(prefs.edit().putString(KEY_ITEMS, JSONArray().apply { items.forEach { put(it.json()) } }.toString()).commit()) {
            "保存音频资料失败"
        }
    }

    companion object {
        private const val KEY_ITEMS = "items_v1"
        private val supportedExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
        private val builtIns = listOf(
            AudioAsset("sad", "悲伤 BGM", AudioKind.BGM, builtIn = true),
            AudioAsset("爽文", "爽文 BGM", AudioKind.BGM, builtIn = true),
            AudioAsset("gong", "铜锣", AudioKind.SFX, builtIn = true),
            AudioAsset("放屁", "放屁音效", AudioKind.SFX, builtIn = true),
            AudioAsset("keyword:tense", "叮咚紧张", AudioKind.SFX, builtIn = true)
        )
    }
}
