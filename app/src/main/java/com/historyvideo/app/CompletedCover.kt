package com.historyvideo.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

internal fun completedFileName(topic: String): String {
    var name = topic.replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "").trim().trimEnd('.', ' ')
    if (name.codePointCount(0, name.length) > 100) name = name.substring(0, name.offsetByCodePoints(0, 100))
    name = name.trimEnd('.', ' ')
    if (name.isBlank()) name = "未命名视频"
    if (Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$").matches(name.substringBefore('.'))) name = "_$name"
    return name
}

internal fun completedCoverTitle(topic: String, coverText: String): String {
    val supplied = normalizeCoverText(coverText)
    if (supplied.isNotBlank()) return supplied
    if (topic.contains("宁古塔")) {
        listOf("披甲人", "奴仆", "举人").firstOrNull { topic.contains(it) }?.let { return "宁古塔$it" }
    }
    val identity = topic.replace(Regex("^在古代(?:做|当)?"), "")
        .split(Regex("(?:到底|究竟)?有多"), limit = 2).first()
        .replace(Regex("[\\s\\p{P}]"), "").trim()
    return identity.ifBlank { topic.trim().ifBlank { "未命名视频" } }
}

internal class CompletedCover(private val store: ProjectStore) {
    private companion object {
        const val VERSION = 1
        const val WIDTH = 1080
        const val HEIGHT = 1440
        val fonts = listOf("STXINGKA.TTF", "STKAITI.TTF", "simkai.ttf", "msyh.ttc")
        val lock = Any()
    }

    private data class Inputs(val source: File, val foreground: File?, val font: File, val text: String, val signature: String)

    private fun inputs(project: VideoProject): Inputs {
        val video = store.output(project)
        require(video.isFile && video.length() > 1000) { "成片尚未生成，无法制作封面" }
        val first = project.shots.firstOrNull { it.id == 1 } ?: error("缺少第一镜封面素材")
        val source = store.image(project, first)
        require(source.isFile && source.length() > 1000) { "缺少第一镜封面素材，请先生成第一镜图片" }
        val directory = File(store.directory(project.id), "layers/shot_01")
        val stamp = File(directory, "source.txt")
        val sourcePrefix = "${source.length()}:${source.lastModified()}:"
        val foreground = File(directory, "foreground.png").takeIf {
            it.isFile && stamp.isFile && stamp.readText().startsWith(sourcePrefix)
        }
        val availableFonts = store.context().assets.list("fonts").orEmpty().toSet()
        val fontName = fonts.firstOrNull { it in availableFonts } ?: error("缺少封面字体文件")
        val font = store.bundledFontFile(fontName)
        val text = completedCoverTitle(project.title, project.coverText)
        val parameters = JSONObject().apply {
            put("version", VERSION); put("topic", project.title); put("text", text)
            put("source", fileStamp(source)); put("video", fileStamp(video))
            put("foreground", foreground?.let(::fileStamp) ?: ""); put("font", fileStamp(font))
        }.toString()
        val signature = MessageDigest.getInstance("SHA-256").digest(parameters.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return Inputs(source, foreground, font, text, signature)
    }

    private fun fileStamp(file: File) = "${file.name}:${file.length()}:${file.lastModified()}"

    fun isCurrent(project: VideoProject): Boolean = runCatching {
        val input = inputs(project)
        val info = JSONObject(store.completedInfo(project).readText())
        store.cover(project).length() > 1000 && info.optString("signature") == input.signature
    }.getOrDefault(false)

    fun generate(project: VideoProject): File = synchronized(lock) {
        val input = inputs(project)
        val destination = store.cover(project)
        val metadata = store.completedInfo(project)
        val previous = runCatching { JSONObject(metadata.readText()) }.getOrNull()
        if (destination.length() > 1000 && previous?.optString("signature") == input.signature) return@synchronized destination
        check(destination.parentFile?.isDirectory == true || destination.parentFile?.mkdirs() == true) { "无法创建封面目录" }
        val pending = File(destination.parentFile, "completed_cover.pending.png")
        val pendingInfo = File(destination.parentFile, "completed_cover.pending.json")
        try {
            draw(input, pending)
            val appVersion = store.context().packageManager.getPackageInfo(store.context().packageName, 0).versionName.orEmpty()
            pendingInfo.writeText(JSONObject().apply {
                put("project_id", project.id); put("project_source", "android")
                put("topic", project.title); put("cover_text", input.text)
                put("app_version", appVersion); put("cover_version", VERSION); put("signature", input.signature)
                put("image_signature", fileStamp(input.source)); put("video_signature", fileStamp(store.output(project)))
                put("completed_video", store.output(project).name); put("completed_cover", destination.name)
                put("file_name", completedFileName(project.title)); put("created_at", System.currentTimeMillis())
            }.toString(2))
            Files.move(pending.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(pendingInfo.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            pending.delete()
            pendingInfo.delete()
        }
        destination
    }

    private fun orientedImage(file: File): Bitmap {
        try {
            // ImageDecoder applies EXIF rotation and mirroring before reporting the image size.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > 2880) decoder.setTargetSize(
                    max(1, info.size.width * 2880 / longest), max(1, info.size.height * 2880 / longest)
                )
            }
        } catch (error: Exception) {
            throw IllegalStateException("无法读取第一镜封面图片或校正方向：${error.message}", error)
        }
    }

    private fun subjectCenter(foreground: File?): Float {
        if (foreground == null) return 0.5f
        val bitmap = BitmapFactory.decodeFile(foreground.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })
            ?: return 0.5f
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var left = bitmap.width
            var right = -1
            pixels.forEachIndexed { index, color ->
                if (Color.alpha(color) > 128) {
                    val x = index % bitmap.width
                    left = min(left, x); right = max(right, x)
                }
            }
            return if (right >= left) (left + right + 1f) / (2f * bitmap.width) else 0.5f
        } finally { bitmap.recycle() }
    }

    private fun draw(input: Inputs, destination: File) {
        val original = orientedImage(input.source)
        val image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        image.setHasAlpha(false)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(Color.BLACK)
            val scale = max(WIDTH.toFloat() / original.width, HEIGHT.toFloat() / original.height)
            val cropWidth = WIDTH / scale
            val left = (original.width * subjectCenter(input.foreground) - cropWidth / 2f)
                .coerceIn(0f, max(0f, original.width - cropWidth))
            val top = max(0f, original.height - HEIGHT / scale) * 0.35f
            canvas.drawBitmap(original, null, RectF(-left * scale, -top * scale,
                (original.width - left) * scale, (original.height - top) * scale), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
            }
            canvas.drawRoundRect(RectF(15f, 15f, WIDTH - 16f, HEIGHT - 16f), 28f, 28f, paint)
            val lines = input.text.codePoints().toArray().asList().chunked(8).map { points ->
                buildString { points.forEach { appendCodePoint(it) } }
            }
            paint.typeface = Typeface.createFromFile(input.font)
            paint.textAlign = Paint.Align.CENTER
            paint.strokeJoin = Paint.Join.ROUND
            var size = 180f
            while (true) {
                paint.textSize = size
                if ((lines.maxOf { line ->
                    val bounds = android.graphics.Rect()
                    paint.getTextBounds(line, 0, line.length, bounds)
                    max(paint.measureText(line), bounds.width().toFloat()) + 16f
                } <= WIDTH * 0.86f &&
                    lines.size * size * 1.2f <= HEIGHT * 0.4f) || size <= 24f) break
                size -= 2f
            }
            val lineHeight = size * 1.2f
            val firstCenter = HEIGHT * 0.49f - lineHeight * (lines.size - 1) / 2f
            lines.forEachIndexed { index, line ->
                val box = android.graphics.Rect()
                paint.getTextBounds(line, 0, line.length, box)
                val baseline = firstCenter + index * lineHeight - (box.top + box.bottom) / 2f
                paint.color = Color.BLACK; paint.style = Paint.Style.FILL_AND_STROKE; paint.strokeWidth = 20f
                canvas.drawText(line, WIDTH / 2f + 3f, baseline + 5f, paint)
                paint.strokeWidth = 16f
                canvas.drawText(line, WIDTH / 2f, baseline, paint)
                paint.color = Color.WHITE; paint.strokeWidth = 4f
                canvas.drawText(line, WIDTH / 2f, baseline, paint)
            }
            destination.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) { "封面 PNG 写入失败" } }
        } finally {
            original.recycle()
            image.recycle()
        }
    }
}
