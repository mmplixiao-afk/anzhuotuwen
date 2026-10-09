package com.historyvideo.app

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.nio.FloatBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.min

/** Creates the two cached layers used by VideoRenderer for a selected shot. */
internal class CutoutProcessor(private val store: ProjectStore) {
    private val models = CutoutModels(store.context())

    fun requireReady() = models.requireReady()

    fun prepare(project: VideoProject, shot: Shot, size: Pair<Int, Int>): LayerFiles {
        requireReady()
        val image = store.image(project, shot)
        require(image.isFile && image.length() > 1000) { "第 ${shot.id} 镜缺少图片，无法抠图" }
        val directory = File(store.directory(project.id), "layers/shot_${shot.id.toString().padStart(2, '0')}")
        val background = File(directory, "background.png")
        val foreground = File(directory, "foreground.png")
        val pendingBackground = File(directory, "background.pending.png")
        val pendingForeground = File(directory, "foreground.pending.png")
        val stamp = File(directory, "source.txt")
        val signature = "${image.length()}:${image.lastModified()}:${size.first}x${size.second}:${modelsRevision()}"
        if (background.isFile && foreground.isFile && stamp.readTextOrNull() == signature) {
            return LayerFiles(background, foreground)
        }
        directory.mkdirs()
        val source = BitmapFactory.decodeFile(image.absolutePath)
            ?: error("无法读取第 ${shot.id} 镜图片")
        try {
            val bitmap = source
            val alpha = humanMask(bitmap)
            if (alpha == null) error("第 ${shot.id} 镜未识别到清晰人物，请更换图片或取消抠图")
            val canvasSize = size.first to size.second
            val fitted = centerCrop(bitmap, canvasSize.first, canvasSize.second)
            val fittedMask = centerCrop(alpha, canvasSize.first, canvasSize.second)
            val repaired = repairBackground(fitted, fittedMask)
            saveBitmap(repaired, pendingBackground, Bitmap.CompressFormat.PNG)
            val contained = contain(bitmap, canvasSize.first, canvasSize.second)
            val containedMask = contain(alpha, canvasSize.first, canvasSize.second)
            val foregroundBitmap = Bitmap.createBitmap(canvasSize.first, canvasSize.second, Bitmap.Config.ARGB_8888)
            Canvas(foregroundBitmap).drawBitmap(contained, (canvasSize.first - contained.width) / 2f, (canvasSize.second - contained.height) / 2f, null)
            val pixels = IntArray(foregroundBitmap.width * foregroundBitmap.height)
            foregroundBitmap.getPixels(pixels, 0, foregroundBitmap.width, 0, 0, foregroundBitmap.width, foregroundBitmap.height)
            val maskPixels = IntArray(containedMask.width * containedMask.height)
            containedMask.getPixels(maskPixels, 0, containedMask.width, 0, 0, containedMask.width, containedMask.height)
            for (y in 0 until containedMask.height) for (x in 0 until containedMask.width) {
                val target = (y + (canvasSize.second - contained.height) / 2) * foregroundBitmap.width + x + (canvasSize.first - contained.width) / 2
                if (target in pixels.indices) pixels[target] = (pixels[target] and 0x00ffffff) or ((maskPixels[y * containedMask.width + x] ushr 24) shl 24)
            }
            foregroundBitmap.setPixels(pixels, 0, foregroundBitmap.width, 0, 0, foregroundBitmap.width, foregroundBitmap.height)
            saveBitmap(foregroundBitmap, pendingForeground, Bitmap.CompressFormat.PNG)
            contained.recycle(); containedMask.recycle(); foregroundBitmap.recycle()
            fitted.recycle(); fittedMask.recycle(); repaired.recycle()
        } finally {
            source.recycle()
        }
        check(pendingBackground.isFile && pendingBackground.length() > 1000 &&
            pendingForeground.isFile && pendingForeground.length() > 1000) { "第 ${shot.id} 镜人物分层素材写入失败" }
        try {
            Files.move(pendingBackground.toPath(), background.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(pendingForeground.toPath(), foreground.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            pendingBackground.delete(); pendingForeground.delete()
            throw IllegalStateException("无法保存第 ${shot.id} 镜人物分层素材", error)
        }
        stamp.writeText(signature)
        return LayerFiles(background, foreground)
    }

    private fun modelsRevision() = CutoutModel.values().joinToString(":") { model ->
        val file = File(store.context().filesDir, "cutout_models/${model.filename}")
        "${file.length()}:${file.lastModified()}"
    }

    private fun humanMask(source: Bitmap): Bitmap? {
        val input = Bitmap.createScaledBitmap(source, 320, 320, true)
        val data = FloatArray(1 * 3 * 320 * 320)
        val pixels = IntArray(320 * 320)
        input.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        for (i in pixels.indices) {
            val color = pixels[i]
            data[i] = (android.graphics.Color.red(color) / 255f - 0.485f) / 0.229f
            data[320 * 320 + i] = (android.graphics.Color.green(color) / 255f - 0.456f) / 0.224f
            data[2 * 320 * 320 + i] = (android.graphics.Color.blue(color) / 255f - 0.406f) / 0.225f
        }
        val env = OrtEnvironment.getEnvironment()
        val output = CutoutModels(store.context()).withModels { paths ->
            withCachedCutoutSession(paths.human) { session ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, 320, 320)).use { tensor ->
                    session.run(mapOf(session.inputInfo.keys.first() to tensor)).use { result ->
                        val tensorOut = result[0] as OnnxTensor
                        tensorOut.floatBuffer?.let { buffer -> FloatArray(buffer.remaining()).also { buffer.get(it) } }
                    }
                }
            }
        } ?: run { input.recycle(); return null }
        if (input !== source) input.recycle()
        val minValue = output.minOrNull() ?: return null
        val maxValue = output.maxOrNull() ?: return null
        if (maxValue < 0.5f) return null
        val binary = BooleanArray(320 * 320) { ((output[it] - minValue) / max(1e-6f, maxValue - minValue)) > 0.5f }
        val components = connectedComponents(binary)
        val largest = components.maxOfOrNull { it.size } ?: return null
        val keep = components.filter { it.size >= max(320 * 320 * 0.008, largest * 0.18).toInt() }
        val selected = BooleanArray(320 * 320)
        keep.forEach { component -> component.forEach { selected[it] = true } }
        val area = selected.count { it }.toFloat() / selected.size
        if (area !in 0.012f..0.85f) return null
        val mask = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(320 * 320)
        for (i in maskPixels.indices) {
            val support = if (selected[i]) true else {
                val x = i % 320; val y = i / 320
                (-2..2).any { dy -> (-2..2).any { dx ->
                    val xx = x + dx; val yy = y + dy
                    xx in 0..319 && yy in 0..319 && selected[yy * 320 + xx]
                } }
            }
            val value = if (support) ((output[i] - minValue) / max(1e-6f, maxValue - minValue) * 255f).toInt().coerceIn(0, 255) else 0
            maskPixels[i] = (value shl 24) or 0x00ffffff
        }
        mask.setPixels(maskPixels, 0, 320, 0, 0, 320, 320)
        val scaled = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(scaled).drawBitmap(mask, null, Rect(0, 0, source.width, source.height), Paint(Paint.FILTER_BITMAP_FLAG))
        mask.recycle()
        return scaled
    }

    private fun connectedComponents(mask: BooleanArray): List<IntArray> {
        val visited = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val found = mutableListOf<IntArray>()
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; visited[start] = true
            val component = IntArray(mask.size)
            var count = 0
            while (head < tail) {
                val at = queue[head++]
                component[count++] = at
                val x = at % 320; val y = at / 320
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0..319 || ny !in 0..319) continue
                    val next = ny * 320 + nx
                    if (mask[next] && !visited[next]) { visited[next] = true; queue[tail++] = next }
                }
            }
            found += component.copyOf(count)
        }
        return found
    }

    private fun repairBackground(image: Bitmap, mask: Bitmap): Bitmap {
        val width = 512; val height = 512
        val source = Bitmap.createScaledBitmap(image, width, height, true)
        val alpha = Bitmap.createScaledBitmap(mask, width, height, true)
        val pixels = IntArray(width * height); val maskPixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        alpha.getPixels(maskPixels, 0, width, 0, 0, width, height)
        val data = FloatArray(3 * width * height)
        val rawMask = BooleanArray(width * height)
        for (i in pixels.indices) {
            data[i] = android.graphics.Color.red(pixels[i]) / 255f
            data[width * height + i] = android.graphics.Color.green(pixels[i]) / 255f
            data[2 * width * height + i] = android.graphics.Color.blue(pixels[i]) / 255f
            rawMask[i] = (maskPixels[i] ushr 24) > 12
        }
        val repairMask = dilate(rawMask, width, height, 6)
        val maskData = FloatArray(width * height) { if (repairMask[it]) 1f else 0f }
        val env = OrtEnvironment.getEnvironment()
        val repaired = CutoutModels(store.context()).withModels { paths ->
            withCachedCutoutSession(paths.background) { session ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, 512, 512)).use { imageTensor ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(maskData), longArrayOf(1, 1, 512, 512)).use { maskTensor ->
                        session.run(mapOf("image" to imageTensor, "mask" to maskTensor)).use { result ->
                            (result[0] as OnnxTensor).floatBuffer?.let { buffer -> FloatArray(buffer.remaining()).also { buffer.get(it) } }
                        }
                    }
                }
            }
        } ?: error("背景修补模型没有输出")
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        val modelScale = if ((repaired.maxOrNull() ?: 0f) <= 1.5f) 255f else 1f
        for (i in outPixels.indices) {
            val shouldRepair = repairMask[i]
            val r = ((repaired[i] * modelScale).coerceIn(0f, 255f)).toInt()
            val g = ((repaired[width * height + i] * modelScale).coerceIn(0f, 255f)).toInt()
            val b = ((repaired[2 * width * height + i] * modelScale).coerceIn(0f, 255f)).toInt()
            val original = pixels[i]
            val rr = if (shouldRepair) r else android.graphics.Color.red(original)
            val gg = if (shouldRepair) g else android.graphics.Color.green(original)
            val bb = if (shouldRepair) b else android.graphics.Color.blue(original)
            outPixels[i] = -0x1000000 or (rr shl 16) or (gg shl 8) or bb
        }
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(output, null, Rect(0, 0, image.width, image.height), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle(); alpha.recycle(); output.recycle()
        return result
    }

    private fun dilate(source: BooleanArray, width: Int, height: Int, radius: Int): BooleanArray {
        val horizontal = BooleanArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var xx = max(0, x - radius)
            val end = min(width - 1, x + radius)
            while (xx <= end && !source[y * width + xx]) xx++
            horizontal[y * width + x] = xx <= end
        }
        val result = BooleanArray(source.size)
        for (y in 0 until height) for (x in 0 until width) {
            var yy = max(0, y - radius)
            val end = min(height - 1, y + radius)
            while (yy <= end && !horizontal[yy * width + x]) yy++
            result[y * width + x] = yy <= end
        }
        return result
    }

    private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val left = (width - scaledWidth) / 2f
        val top = (height - scaledHeight) / 2f
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(source, null, RectF(left, top, left + scaledWidth, top + scaledHeight),
            Paint(Paint.FILTER_BITMAP_FLAG))
        return result
    }

    private fun contain(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val outWidth = (source.width * scale).toInt().coerceIn(1, width)
        val outHeight = (source.height * scale).toInt().coerceIn(1, height)
        return Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(source, null, Rect(0, 0, outWidth, outHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat) {
        file.outputStream().use { bitmap.compress(format, 100, it) }
    }

    internal data class LayerFiles(val background: File, val foreground: File)
}

private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()
