package com.historyvideo.app

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class AlbumExporter(private val store: ProjectStore) {
    private companion object { val lock = Any() }

    fun save(project: VideoProject, cover: Boolean): String = synchronized(lock) {
        val file = if (cover) store.cover(project) else store.output(project)
        val label = if (cover) "封面" else "视频"
        require(file.isFile && file.length() > 1000) { "$label 尚未生成" }
        if (cover) require(CompletedCover(store).isCurrent(project)) {
            "封面与当前视频不一致，请在成片结果页重新生成封面后保存"
        }
        val resolver = store.context().contentResolver
        val record = store.exportInfo(project)
        val exports = runCatching { JSONObject(record.readText()) }.getOrElse { JSONObject() }
        val key = if (cover) "cover" else "video"
        val previous = exports.optJSONObject(key)
        val signature = "${file.name}:${file.length()}:${file.lastModified()}"
        val oldUri = previous?.optString("uri")?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (previous?.optString("signature") == signature && oldUri != null && runCatching {
                resolver.openAssetFileDescriptor(oldUri, "r")?.use { it.length != 0L } == true
            }.getOrDefault(false)) return@synchronized "$label 已保存到相册"

        val folder = exports.optString("folder").ifBlank { "${completedFileName(project.title)}_${project.id}" }
        val relativePath = "${if (cover) "Pictures" else "Movies"}/HistoryVideo/$folder"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${completedFileName(project.title)}.${if (cover) "png" else "mp4"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (cover) "image/png" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (cover) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error("无法在相册创建$label 文件")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: error("无法打开相册$label 写入流")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0) {
                "相册$label 文件已写入，但发布失败"
            }
            exports.put("folder", folder)
            exports.put(key, JSONObject().apply { put("uri", uri.toString()); put("signature", signature) })
            val pending = File(record.parentFile, "album_exports.pending.json")
            try {
                pending.writeText(exports.toString(2))
                Files.move(pending.toPath(), record.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { pending.delete() }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw IllegalStateException("保存$label 失败：${error.message ?: error.javaClass.simpleName}", error)
        }
        if (oldUri != null && oldUri != uri) runCatching { resolver.delete(oldUri, null, null) }
        "$label 已保存到相册 $relativePath"
    }
}
