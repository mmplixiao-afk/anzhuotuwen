package com.historyvideo.app

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal enum class CutoutModel(val filename: String, val label: String) {
    HUMAN("u2net_human_seg.onnx", "人物分割模型"),
    BACKGROUND("lama_fp32.onnx", "背景修补模型")
}

internal data class LocalCutoutModels(val human: File, val background: File) {
    val revision: String
        get() = listOf(human, background).joinToString(":") { "${it.length()}:${it.lastModified()}" }
}

internal class CutoutModels(context: Context) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "cutout_models")

    fun file(model: CutoutModel) = File(directory, model.filename)

    fun isImported(model: CutoutModel) = file(model).isFile && file(model).length() > 1000

    fun requireReady() {
        val missing = CutoutModel.values().filterNot(::isImported)
        require(missing.isEmpty()) {
            "请先到‘画面与章法 → 成片抠图’导入${missing.joinToString("、") { it.label }}"
        }
    }

    suspend fun importModel(model: CutoutModel, uri: Uri, progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        modelLock.write {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            require(name == null || name.equals(model.filename, ignoreCase = true)) {
                "请选择 ${model.filename}，当前选择的是 $name"
            }
            check(directory.isDirectory || directory.mkdirs()) { "无法创建模型目录" }
            val pending = File(directory, "${model.filename}.importing")
            try {
                var bytes = 0L
                var lastProgress = 0L
                val task = currentCoroutineContext()
                progress("正在导入${model.label}")
                val input = context.contentResolver.openInputStream(uri) ?: error("无法打开所选文件")
                input.use { source ->
                    pending.outputStream().buffered().use { target ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            task.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                            bytes += count
                            val now = System.currentTimeMillis()
                            if (now - lastProgress >= 300) {
                                progress("正在导入${model.label}：${String.format(Locale.US, "%.1f", bytes / 1048576.0)} MB")
                                lastProgress = now
                            }
                        }
                    }
                }
                require(bytes > 1000) { "所选模型文件为空或不完整" }
                task.ensureActive()
                progress("正在加载${model.label}")
                withCutoutSession(pending) { session -> validateModel(model, session) }
                task.ensureActive()
                Files.move(pending.toPath(), file(model).toPath(), StandardCopyOption.REPLACE_EXISTING)
                progress("${model.label}已导入")
            } finally {
                pending.delete()
            }
        }
    }

    fun <T> withModels(action: (LocalCutoutModels) -> T): T {
        return modelLock.read {
            requireReady()
            action(LocalCutoutModels(file(CutoutModel.HUMAN), file(CutoutModel.BACKGROUND)))
        }
    }

    private fun validateModel(model: CutoutModel, session: OrtSession) {
        fun matches(info: TensorInfo?, expected: LongArray): Boolean {
            val shape = info?.shape ?: return false
            return shape.size == expected.size && shape.indices.all { shape[it] <= 0 || shape[it] == expected[it] }
        }
        val inputs = session.inputInfo
        val output = session.outputInfo.values.firstOrNull()?.info as? TensorInfo
        val valid = when (model) {
            CutoutModel.HUMAN -> inputs.size == 1 &&
                matches(inputs.values.first().info as? TensorInfo, longArrayOf(1, 3, 320, 320)) &&
                matches(output, longArrayOf(1, 1, 320, 320))
            CutoutModel.BACKGROUND ->
                matches(inputs["image"]?.info as? TensorInfo, longArrayOf(1, 3, 512, 512)) &&
                matches(inputs["mask"]?.info as? TensorInfo, longArrayOf(1, 1, 512, 512)) &&
                matches(output, longArrayOf(1, 3, 512, 512))
        }
        require(valid) { "所选文件不是兼容的${model.label}，请导入电脑端的 ${model.filename}" }
    }

    private companion object {
        val modelLock = ReentrantReadWriteLock()
    }
}

internal fun <T> withCutoutSession(model: File, action: (OrtSession) -> T): T {
    return createCutoutSession(model).use(action)
}

private data class CachedOrtSession(val revision: String, val session: OrtSession)

private val cachedCutoutSessions = mutableMapOf<String, CachedOrtSession>()

internal fun <T> withCachedCutoutSession(model: File, action: (OrtSession) -> T): T = synchronized(cachedCutoutSessions) {
    val path = model.absolutePath
    val revision = "${model.length()}:${model.lastModified()}"
    val cached = cachedCutoutSessions[path]
    val session = if (cached?.revision == revision) cached.session else {
        cached?.session?.close()
        val created = createCutoutSession(model)
        cachedCutoutSessions[path] = CachedOrtSession(revision, created)
        created
    }
    action(session)
}

private fun createCutoutSession(model: File): OrtSession {
    val options = OrtSession.SessionOptions()
    options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
    options.setInterOpNumThreads(1)
    options.setMemoryPatternOptimization(false)
    options.setCPUArenaAllocator(false)
    return OrtEnvironment.getEnvironment().createSession(model.absolutePath, options).also { options.close() }
}
