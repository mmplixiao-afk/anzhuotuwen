package com.historyvideo.app

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal const val H264_MIME = "video/avc"
internal const val AAC_MIME = "audio/mp4a-latm"
internal const val EXPORT_FRAME_RATE = 30

internal enum class FinalCacheAction { REUSE, TRANSCODE_EXISTING, RENDER }

internal enum class ExportCommit { REPLACE, KEEP_EXISTING }

internal data class ExportDecision(val commit: ExportCommit, val reason: String)

internal data class MediaProbe(
    val videoMime: String,
    val audioMime: String,
    val width: Int,
    val height: Int,
    val durationUs: Long,
    val frameRate: Int? = null
)

/** Settings consumed by the platform H.264 encoder. Tests lock this object, not a guessed FFmpeg name. */
internal data class H264EncoderSettings(
    val mime: String,
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val frameRate: Int,
    val iFrameIntervalSeconds: Int,
    val profileName: String,
    val levelName: String,
    val colorFormat: String
)

internal fun isH264(codec: String): Boolean {
    val value = codec.trim().lowercase()
    return value == "h264" || value == "avc" || value == "avc1" || value == H264_MIME
}

internal fun isAac(codec: String): Boolean {
    val value = codec.trim().lowercase()
    return value == "aac" || value == "mp4a" || value == AAC_MIME
}

internal fun h264Bitrate(width: Int, height: Int, frameRate: Int = EXPORT_FRAME_RATE): Int {
    val raw = width.toLong() * height.toLong() * frameRate.toLong() * 8L / 100L
    return raw.coerceIn(1_500_000L, 8_000_000L).toInt()
}

internal fun avcLevelName(width: Int, height: Int): String {
    val macroblocks = ((width + 15) / 16) * ((height + 15) / 16)
    return when {
        macroblocks <= 3600 -> "3.1"
        macroblocks <= 5120 -> "3.2"
        else -> "4"
    }
}

internal fun h264EncoderSettings(width: Int, height: Int): H264EncoderSettings {
    require(width >= 2 && height >= 2 && width % 2 == 0 && height % 2 == 0) {
        "画面尺寸必须是不小于 2 的偶数：${width}×${height}"
    }
    return H264EncoderSettings(
        mime = H264_MIME,
        width = width,
        height = height,
        bitRate = h264Bitrate(width, height),
        frameRate = EXPORT_FRAME_RATE,
        iFrameIntervalSeconds = 2,
        profileName = "baseline",
        levelName = avcLevelName(width, height),
        colorFormat = "surface"
    )
}

internal fun exportAccepted(probe: MediaProbe): Boolean =
    isH264(probe.videoMime) &&
        isAac(probe.audioMime) &&
        probe.width >= 2 && probe.height >= 2 &&
        probe.width % 2 == 0 && probe.height % 2 == 0 &&
        probe.durationUs > 0L &&
        (probe.frameRate == null || probe.frameRate == EXPORT_FRAME_RATE)

internal fun durationCloseEnough(sourceUs: Long, outputUs: Long): Boolean {
    if (sourceUs <= 0L || outputUs <= 0L) return false
    return kotlin.math.abs(sourceUs - outputUs) <= 200_000L
}

internal fun dimensionsMatch(sourceWidth: Int, sourceHeight: Int, outputWidth: Int, outputHeight: Int): Boolean =
    sourceWidth == outputWidth && sourceHeight == outputHeight && sourceWidth % 2 == 0 && sourceHeight % 2 == 0

internal fun finalCacheAction(
    signatureMatches: Boolean,
    bytes: Long,
    videoMime: String?,
    audioMime: String?
): FinalCacheAction {
    if (!signatureMatches || bytes <= 1000L || videoMime.isNullOrBlank() || audioMime.isNullOrBlank()) return FinalCacheAction.RENDER
    if (isH264(videoMime) && isAac(audioMime)) return FinalCacheAction.REUSE
    if (isAac(audioMime)) return FinalCacheAction.TRANSCODE_EXISTING
    return FinalCacheAction.RENDER
}

internal fun fastStartFromBoxOrder(types: List<String>): Boolean {
    val moov = types.indexOf("moov")
    val mdat = types.indexOf("mdat")
    return moov >= 0 && (mdat < 0 || moov < mdat)
}

internal fun decideExport(
    probe: MediaProbe,
    fastStart: Boolean,
    durationOk: Boolean,
    dimensionsOk: Boolean
): ExportDecision {
    if (!isH264(probe.videoMime)) return ExportDecision(ExportCommit.KEEP_EXISTING, "视频不是 H.264（${probe.videoMime}）")
    if (!isAac(probe.audioMime)) return ExportDecision(ExportCommit.KEEP_EXISTING, "音频不是 AAC（${probe.audioMime}）")
    if (!exportAccepted(probe)) return ExportDecision(ExportCommit.KEEP_EXISTING, "分辨率或帧率不符合兼容要求")
    if (!fastStart) return ExportDecision(ExportCommit.KEEP_EXISTING, "moov 不在文件头")
    if (!durationOk || !dimensionsOk) return ExportDecision(ExportCommit.KEEP_EXISTING, "时长或画面尺寸与原片不一致")
    return ExportDecision(ExportCommit.REPLACE, "")
}

internal fun h264FailureMessage(hadExisting: Boolean, cause: String): String =
    if (hadExisting) "H.264 转码失败，已保留原成片：${cause.trim()}"
    else "H.264 转码失败：${cause.trim()}"

internal fun mp4TopLevelBoxTypes(file: File): List<String> {
    RandomAccessFile(file, "r").use { input ->
        val types = mutableListOf<String>()
        while (input.filePointer + 8 <= input.length() && types.size < 32) {
            val start = input.filePointer
            val size32 = readU32(input)
            val type = readType(input)
            if (type.any { it.code !in 32..126 }) break
            val end = when (size32) {
                0L -> input.length()
                1L -> {
                    if (input.filePointer + 8 > input.length()) break
                    start + readU64(input)
                }
                else -> start + size32
            }
            if (end < start + 8 || end > input.length()) break
            types += type
            input.seek(end)
        }
        return types
    }
}

internal fun replaceVerifiedFile(verifiedTemp: File, destination: File) {
    require(verifiedTemp.isFile && verifiedTemp.length() > 1000) { "转码结果不存在或为空" }
    require(verifiedTemp.absolutePath != destination.absolutePath) { "不能把成片覆盖成同一个临时文件" }
    val parent = destination.parentFile ?: error("成片目录无效")
    parent.mkdirs()
    val backup = File(parent, destination.name + ".bak")
    if (backup.exists()) backup.delete()
    val hadExisting = destination.isFile
    if (hadExisting) Files.move(destination.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
    try {
        try {
            Files.move(
                verifiedTemp.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(verifiedTemp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (error: Exception) {
        if (hadExisting && backup.isFile && (!destination.isFile || destination.length() <= 1000L)) {
            runCatching {
                if (destination.exists()) destination.delete()
                Files.move(backup.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        throw error
    }
    if (backup.exists()) backup.delete()
}

internal fun applyExportDecision(decision: ExportDecision, verifiedTemp: File, destination: File) {
    if (decision.commit != ExportCommit.REPLACE) {
        error(h264FailureMessage(destination.isFile && destination.length() > 1000L, decision.reason.ifBlank { "校验未通过" }))
    }
    replaceVerifiedFile(verifiedTemp, destination)
}

private fun readU32(input: RandomAccessFile): Long {
    val bytes = ByteArray(4)
    if (input.read(bytes) != 4) error("MP4 箱头不完整")
    return ((bytes[0].toLong() and 0xff) shl 24) or
        ((bytes[1].toLong() and 0xff) shl 16) or
        ((bytes[2].toLong() and 0xff) shl 8) or
        (bytes[3].toLong() and 0xff)
}

private fun readU64(input: RandomAccessFile): Long {
    val bytes = ByteArray(8)
    if (input.read(bytes) != 8) error("MP4 超大箱头不完整")
    var value = 0L
    for (byte in bytes) value = (value shl 8) or (byte.toLong() and 0xff)
    return value
}

private fun readType(input: RandomAccessFile): String {
    val bytes = ByteArray(4)
    if (input.read(bytes) != 4) error("MP4 箱类型不完整")
    return String(bytes, Charsets.ISO_8859_1)
}
