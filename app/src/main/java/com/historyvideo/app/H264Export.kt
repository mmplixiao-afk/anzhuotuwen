package com.historyvideo.app

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.CancellationException
import java.io.File
import java.nio.ByteBuffer

/**
 * Turns the existing MPEG-4 Part 2 final into H.264 + AAC.
 * The LGPL FFmpeg package has no libx264, so the video encoder is Android MediaCodec.
 * FFmpeg is used only to remux the verified streams with +faststart.
 */
internal object H264Export {
    fun probeOrNull(file: File): MediaProbe? = runCatching { probe(file) }.getOrNull()

    fun probe(file: File): MediaProbe {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            var durationUs = 0L
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val trackDuration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                if (trackDuration > durationUs) durationUs = trackDuration
                if (video == null && mime.startsWith("video/")) video = format
                if (audio == null && mime.startsWith("audio/")) audio = format
            }
            val videoFormat = video ?: error("成片没有视频轨")
            val audioFormat = audio ?: error("成片没有音频轨")
            val frameRate = runCatching {
                if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else null
            }.getOrNull()
            return MediaProbe(
                videoMime = videoFormat.getString(MediaFormat.KEY_MIME).orEmpty(),
                audioMime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty(),
                width = videoFormat.getInteger(MediaFormat.KEY_WIDTH),
                height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT),
                durationUs = durationUs,
                frameRate = frameRate
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun produce(
        source: File,
        destination: File,
        isActive: () -> Boolean,
        ffmpeg: (String, Array<String>) -> Unit
    ) {
        require(source.absolutePath != destination.absolutePath) { "转码源和目标不能是同一个文件" }
        require(source.isFile && source.length() > 1000) { "待转换的成片不存在或为空" }
        val parent = destination.parentFile ?: error("成片目录无效")
        parent.mkdirs()
        val encoded = File(parent, destination.name + ".encoded.mp4")
        val fast = File(parent, destination.name + ".fast.mp4")
        try {
            if (!isActive()) throw CancellationException("已暂停")
            transcode(source, encoded, isActive)
            if (!isActive()) throw CancellationException("已暂停")
            ffmpeg(
                "封装 faststart",
                arrayOf(
                    "-y", "-i", encoded.absolutePath,
                    "-map", "0:v:0", "-map", "0:a:0",
                    "-c", "copy", "-movflags", "+faststart",
                    fast.absolutePath
                )
            )
            val outputProbe = probe(fast)
            val sourceProbe = probe(source)
            val decision = decideExport(
                outputProbe,
                fastStartFromBoxOrder(mp4TopLevelBoxTypes(fast)),
                durationCloseEnough(sourceProbe.durationUs, outputProbe.durationUs),
                dimensionsMatch(sourceProbe.width, sourceProbe.height, outputProbe.width, outputProbe.height)
            )
            if (decision.commit != ExportCommit.REPLACE) error(decision.reason.ifBlank { "校验未通过" })
            replaceVerifiedFile(fast, destination)
        } finally {
            if (encoded.exists()) encoded.delete()
            if (fast.exists()) fast.delete()
        }
    }

    private fun transcode(source: File, encoded: File, isActive: () -> Boolean) {
        if (encoded.exists()) encoded.delete()
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(source.absolutePath)
            val videoIndex = trackIndex(extractor, "video/")
            val audioIndex = trackIndex(extractor, "audio/")
            val videoFormat = extractor.getTrackFormat(videoIndex)
            val audioFormat = extractor.getTrackFormat(audioIndex)
            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val settings = h264EncoderSettings(width, height)
            val started = startEncoder(settings)
            val videoEncoder = started.first
            val inputSurface = started.second
            encoder = videoEncoder
            surface = inputSurface
            val decoderMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: error("无法读取原视频编码")
            val videoDecoder = MediaCodec.createDecoderByType(decoderMime)
            decoder = videoDecoder
            videoDecoder.configure(videoFormat, inputSurface, null, 0)
            videoDecoder.start()
            val videoMuxer = MediaMuxer(encoded.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = videoMuxer
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                videoMuxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            extractor.selectTrack(videoIndex)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            var signaled = false
            var videoTrack = -1
            var audioTrack = -1
            var rendered = 0
            var lastProgress = SystemClock.elapsedRealtime()
            fun note() { lastProgress = SystemClock.elapsedRealtime() }
            while (!encoderDone) {
                if (!isActive()) throw CancellationException("已暂停")
                if (SystemClock.elapsedRealtime() - lastProgress > 30_000L) error("H.264 转码超时")
                if (!inputDone) {
                    val inIndex = videoDecoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = videoDecoder.getInputBuffer(inIndex) ?: error("解码器输入缓冲区不可用")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            videoDecoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            videoDecoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                        note()
                    }
                }
                if (!decoderDone) {
                    val outIndex = videoDecoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        val end = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bufferInfo.size > 0) {
                            val timestampNs = maxOf(1L, bufferInfo.presentationTimeUs * 1000L)
                            videoDecoder.releaseOutputBuffer(outIndex, timestampNs)
                            rendered++
                        } else {
                            videoDecoder.releaseOutputBuffer(outIndex, false)
                        }
                        if (end && !signaled) {
                            decoderDone = true
                            videoEncoder.signalEndOfInputStream()
                            signaled = true
                        }
                        note()
                    }
                } else if (!signaled) {
                    videoEncoder.signalEndOfInputStream()
                    signaled = true
                }
                val encodedIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    encodedIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) error("H.264 编码器重复改变输出格式")
                        videoTrack = videoMuxer.addTrack(videoEncoder.outputFormat)
                        audioTrack = videoMuxer.addTrack(cleanAudioFormat(audioFormat))
                        videoMuxer.start()
                        muxerStarted = true
                        note()
                    }
                    encodedIndex >= 0 -> {
                        val codecConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!muxerStarted && bufferInfo.size > 0 && !codecConfig) {
                            error("H.264 输出格式尚未就绪")
                        }
                        if (muxerStarted && bufferInfo.size > 0 && !codecConfig) {
                            val encodedBuffer = videoEncoder.getOutputBuffer(encodedIndex) ?: error("编码器输出缓冲区不可用")
                            videoMuxer.writeSampleData(videoTrack, encodedBuffer, bufferInfo)
                        }
                        val end = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        videoEncoder.releaseOutputBuffer(encodedIndex, false)
                        if (end) encoderDone = true
                        note()
                    }
                }
            }
            if (!muxerStarted || rendered == 0) error("H.264 编码器没有产出画面")
            extractor.unselectTrack(videoIndex)
            extractor.selectTrack(audioIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            copyAudio(extractor, videoMuxer, audioTrack, isActive)
            videoMuxer.stop()
            muxerStarted = false
        } catch (error: CancellationException) {
            throw error
        } finally {
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { surface?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun copyAudio(extractor: MediaExtractor, muxer: MediaMuxer, track: Int, isActive: () -> Boolean) {
        val info = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocateDirect(1024 * 1024)
        var first = true
        while (true) {
            if (!isActive()) throw CancellationException("已暂停")
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            require(size <= buffer.capacity()) { "音频样本超过 1 MB" }
            info.set(
                0,
                size,
                extractor.sampleTime.coerceAtLeast(0L),
                if (first || extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
            )
            buffer.position(0)
            buffer.limit(size)
            first = false
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }

    private fun cleanAudioFormat(source: MediaFormat): MediaFormat {
        val mime = source.getString(MediaFormat.KEY_MIME) ?: error("无法读取原音频编码")
        val sampleRate = source.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = source.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)
        val raw = source.getByteBuffer("csd-0")
        if (raw == null) {
            if (isAac(mime)) error("AAC 缺少解码配置")
        } else {
            val sourceBuffer = raw.duplicate()
            val copy = ByteBuffer.allocate(sourceBuffer.remaining())
            copy.put(sourceBuffer)
            copy.flip()
            format.setByteBuffer("csd-0", copy)
        }
        return format
    }

    private fun trackIndex(extractor: MediaExtractor, prefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(prefix)) return index
        }
        error(if (prefix.startsWith("video")) "成片没有视频轨" else "成片没有音频轨")
    }

    private fun startEncoder(settings: H264EncoderSettings): Pair<MediaCodec, Surface> =
        try {
            configureEncoder(settings, withProfile = true)
        } catch (error: CancellationException) {
            throw error
        } catch (first: Exception) {
            try {
                configureEncoder(settings, withProfile = false)
            } catch (second: Exception) {
                throw IllegalStateException(
                    "设备无法创建 H.264 编码器：${second.message ?: first.message ?: "未知错误"}",
                    second
                )
            }
        }

    private fun configureEncoder(settings: H264EncoderSettings, withProfile: Boolean): Pair<MediaCodec, Surface> {
        require(settings.mime == H264_MIME && settings.colorFormat == "surface")
        val encoder = MediaCodec.createEncoderByType(settings.mime)
        try {
            val format = MediaFormat.createVideoFormat(settings.mime, settings.width, settings.height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, settings.bitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, settings.iFrameIntervalSeconds)
            if (withProfile) {
                format.setInteger(MediaFormat.KEY_PROFILE, profileConstant(settings.profileName))
                format.setInteger(MediaFormat.KEY_LEVEL, levelConstant(settings.levelName))
                format.setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                )
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            encoder.start()
            return encoder to surface
        } catch (error: Exception) {
            runCatching { encoder.release() }
            throw error
        }
    }

    private fun profileConstant(name: String): Int = when (name) {
        "baseline" -> MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        else -> error("未知的 H.264 profile：$name")
    }

    private fun levelConstant(name: String): Int = when (name) {
        "3.1" -> MediaCodecInfo.CodecProfileLevel.AVCLevel31
        "3.2" -> MediaCodecInfo.CodecProfileLevel.AVCLevel32
        "4" -> MediaCodecInfo.CodecProfileLevel.AVCLevel4
        else -> error("未知的 H.264 level：$name")
    }
}
