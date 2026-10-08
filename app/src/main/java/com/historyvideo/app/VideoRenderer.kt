package com.historyvideo.app

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.min

class VideoRenderer(private val store: ProjectStore) {
    fun duration(path: File): Double {
        val session = FFprobeKit.getMediaInformation(path.absolutePath)
        return session.mediaInformation?.duration?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: error("无法读取音频时长: ${path.name}")
    }

    private fun run(label: String, vararg args: String) {
        val session = FFmpegKit.executeWithArguments(arrayOf(*args))
        if (!ReturnCode.isSuccess(session.returnCode)) {
            val lines = session.allLogsAsString.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
            val diagnostic = lines.lastOrNull { it.contains("No such filter", ignoreCase = true) }
                ?: lines.lastOrNull { line ->
                    listOf("error", "failed", "invalid", "not found", "out of memory")
                        .any { word -> line.contains(word, ignoreCase = true) }
                } ?: lines.lastOrNull()
            error("${label}失败，退出码 ${session.returnCode?.value}：${diagnostic?.take(160) ?: "无 FFmpeg 日志"}")
        }
    }

    private fun canvasSize(project: VideoProject): Pair<Int, Int> = when (project.imageSize.lowercase()) {
        "16:9", "1792x1024", "1920x1080" -> 1920 to 1080
        "1:1", "1024x1024" -> 1080 to 1080
        else -> 1080 to 1920
    }

    private fun ffmpegPath(file: File): String = file.absolutePath.replace("\\", "/").replace(":", "\\:")

    private fun motionFilter(project: VideoProject, shot: Shot, frames: Int, width: Int, height: Int): String {
        val mode = shot.cameraMotion.takeIf { it.isNotBlank() && it != "static" }
            ?: when (project.motionMode) {
                "zoom_in" -> "zoom_in"
                "zoom_out" -> "zoom_out"
                "pan" -> "pan"
                else -> listOf("zoom_in", "zoom_out", "pan")[shot.id % 3]
            }
        val progress = "min(1,on/${maxOf(1, frames - 1)})"
        val zoom = when (mode) {
            "zoom_out" -> "1.14-0.10*$progress"
            "pan" -> "1.12"
            else -> "1.02+0.10*$progress"
        }
        // Move steadily in one direction. A sine wave makes the picture swing back and forth.
        val pan = if (mode == "pan") {
            "(iw-iw/zoom)/2+(iw-iw/zoom)*0.20*(2*$progress-1)"
        } else "(iw-iw/zoom)/2"
        return "scale=${width * 2}:${height * 2}:force_original_aspect_ratio=increase,crop=${width * 2}:${height * 2},setsar=1," +
            "zoompan=z='$zoom':x='$pan':y='(ih-ih/zoom)/2':d=1:s=${width}x${height}:fps=30,format=yuv420p"
    }

    private fun subtitleFilter(project: VideoProject, shot: Shot, dir: File, width: Int, height: Int): String {
        if (!project.subtitlesEnabled || shot.text.isBlank()) return ""
        if (shot.id == project.coverShotId && project.coverText.isNotBlank()) return ""
        // Keep the larger subtitle inside the canvas while allowing a balanced two-line caption.
        val lineChars = if (height > width) 8 else 14
        val chunks = subtitleChunks(shot.text, lineChars * 2)
        if (chunks.isEmpty()) return ""
        val font = 108
        val fontPath = ffmpegPath(store.bundledFont("subtitle"))
        val duration = shot.duration.coerceAtLeast(0.1)
        val weights = chunks.map { maxOf(1, it.count { ch -> !ch.isWhitespace() }) }
        val total = weights.sum().coerceAtLeast(1)
        var cursor = 0.0
        return chunks.mapIndexed { index, text ->
            val lines = subtitleLines(text, lineChars)
            val end = if (index == chunks.lastIndex) duration else cursor + duration * weights[index] / total
            val enable = "between(t,${"%.3f".format(cursor)},${"%.3f".format(end)})"
            cursor = end
            lines.mapIndexed { lineIndex, line ->
                val file = File(dir, "subtitle_%03d_%02d_%02d.txt".format(shot.id, index, lineIndex)).apply {
                    writeText(line)
                }
                val offset = (lines.lastIndex - lineIndex) * (font + 8)
                ",drawtext=fontfile='$fontPath':textfile='${ffmpegPath(file)}':" +
                    "fontcolor=white:fontsize=$font:line_spacing=8:box=0:" +
                    "borderw=6:bordercolor=black:shadowx=1:shadowy=1:" +
                    "x=(w-text_w)/2:y=h-text_h-150-$offset:enable='$enable'"
            }.joinToString("")
        }.joinToString("")
    }

    private fun subtitleLines(value: String, maxChars: Int): List<String> {
        val source = value.replace("\r", " ").replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (source.length <= maxChars) return listOf(source)
        val midpoint = source.length / 2
        val lower = maxOf(1, source.length - maxChars)
        val upper = minOf(maxChars, source.length - 1)
        val punctuation = "，。！？；：、,.!?;:…"
        val cut = (midpoint downTo lower).firstOrNull { source[it - 1] in punctuation }
            ?: (midpoint..upper).firstOrNull { source[it - 1] in punctuation }
            ?: midpoint.coerceIn(lower, upper)
        return listOf(source.substring(0, cut).trim(), source.substring(cut).trim()).filter { it.isNotBlank() }
    }

    private fun subtitleChunks(value: String, maxChars: Int): List<String> {
        val source = value.replace("\r", " ").replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (source.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var current = StringBuilder()
        source.forEach { ch ->
            current.append(ch)
            if (current.length >= 6 && ch in "，。！？；：、,.!?;:…") {
                result += current.toString().trim()
                current = StringBuilder()
            } else if (current.length >= maxChars) {
                result += current.toString().trim()
                current = StringBuilder()
            }
        }
        if (current.isNotBlank()) result += current.toString().trim()
        if (result.size > 1 && result.last().length < 4 &&
            result[result.lastIndex - 1].length + result.last().length <= maxChars) {
            result[result.lastIndex - 1] = result[result.lastIndex - 1] + result.removeAt(result.lastIndex)
        }
        return result
    }

    private fun coverFilter(project: VideoProject, shot: Shot, dir: File, width: Int, height: Int): String {
        if (shot.id != project.coverShotId || project.coverText.isBlank()) return ""
        val maxChars = if (height > width) 8 else 14
        val title = project.coverText.trim().chunked(maxChars).joinToString("\n")
        val file = File(dir, "cover_title.txt").apply { writeText(title) }
        val font = maxOf(28, minOf((width * 0.13f).toInt(), (height * 0.20f).toInt()))
        val fontPath = ffmpegPath(store.bundledFont("cover"))
        // Match the desktop title: one second delay, slide up from below, then a small settle.
        val y = "if(lt(t,1.0),h+text_h,if(lt(t,1.28),h-(h*0.50)*(t-1.0)/0.28,if(lt(t,1.38),h*0.47,h*0.50)))"
        return ",drawtext=fontfile='$fontPath':textfile='${ffmpegPath(file)}':" +
            "fontcolor=white:fontsize=$font:line_spacing=10:box=1:boxcolor=black@0.18:boxborderw=12:" +
            "borderw=5:bordercolor=black:x=(w-text_w)/2:y='$y'"
    }

    /**
     * Android does not ship the desktop ONNX matting models. Keep the selected
     * cutout option visible in the final render with a local subject-emphasis
     * pass: a softened/dimmed plate plus a feathered central foreground layer.
     * This is deliberately self-contained and does not change the source image.
     */
    private fun cutoutFilter(project: VideoProject, shot: Shot, frames: Int, width: Int, height: Int): String {
        val base = motionFilter(project, shot, frames, width, height)
            .replace(",format=yuv420p", ",format=rgba")
        val distance = "(pow((X-W/2)/(W*0.38),2)+pow((Y-H/2)/(H*0.48),2))"
        return "[0:v]split=2[bgsrc][fgsrc];" +
            // The mobile FFmpeg build has gblur but does not include eq. Keep the
            // background separation stable with the supported blur filter only.
            "[bgsrc]$base,gblur=sigma=10[bg];" +
            "[fgsrc]$base,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':" +
            "a='if(lte($distance,0.82),255,if(gte($distance,1.18),0,255*(1.18-$distance)/0.36))'[fg];" +
            "[bg][fg]overlay=x=0:y=0:format=auto,format=yuv420p[v]"
    }

    private fun concatLine(file: File): String = "file '${file.absolutePath.replace("\\", "/").replace("'", "'\\''")}'\n"

    private fun concatVideos(clips: List<File>, output: File, temp: File) {
        val list = File(temp, "video_list.txt")
        list.writeText(clips.joinToString("") { concatLine(it) })
        run("合并画面", "-y", "-f", "concat", "-safe", "0", "-i", list.absolutePath, "-c", "copy", output.absolutePath)
    }

    private fun mergeVideos(project: VideoProject, clips: List<File>, durations: List<Double>, output: File, temp: File) {
        if (clips.size < 2 || project.transitionMode == "none") {
            concatVideos(clips, output, temp)
            return
        }
        val transition = when (project.transitionMode) {
            "dissolve" -> "dissolve"
            "slideleft" -> "slideleft"
            else -> "fade"
        }
        val randomTransitions = listOf("fade", "dissolve", "slideleft", "wipeleft", "circleopen")
        val filters = StringBuilder()
        clips.indices.forEach { i ->
            // xfade requires matching frame rate, pixel format and time base.
            filters.append("[$i:v]fps=30,format=yuv444p,setsar=1,settb=1/30,setpts=PTS-STARTPTS[v$i];")
        }
        var current = "v0"
        var elapsed = durations.first().coerceAtLeast(0.2)
        clips.drop(1).forEachIndexed { index, _ ->
            val i = index + 1
            val d = min(0.35, min(durations[i - 1], durations[i]).coerceAtLeast(0.2) / 3.0)
            val next = "x$i"
            val selectedTransition = if (project.transitionMode == "random") randomTransitions[index % randomTransitions.size] else transition
            filters.append("[$current][v$i]xfade=transition=$selectedTransition:duration=${"%.3f".format(d)}:offset=${"%.3f".format(elapsed - d)}[$next];")
            elapsed += durations[i].coerceAtLeast(0.2) - d
            current = next
        }
        val inputs = clips.flatMap { listOf("-i", it.absolutePath) }.toTypedArray()
        run("生成转场", "-y", *inputs, "-filter_complex_threads", "8", "-filter_complex", filters.toString(),
            "-map", "[$current]", "-an", "-c:v", "mpeg4", "-q:v", "4", "-threads", "8",
            "-r", "30", "-pix_fmt", "yuv420p", output.absolutePath)
    }

    private fun muxFinalAudio(project: VideoProject, video: File, voice: File, output: File) {
        val bgm = store.bundledAudio(project.bgmId)
        val sfx = store.bundledAudio(project.introSfxId)
        val useMix = bgm != null || sfx != null || project.ttsVolume != 1f
        if (!useMix) {
            run("封装最终视频", "-y", "-i", video.absolutePath, "-i", voice.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", "-movflags", "+faststart", output.absolutePath)
            return
        }
        val inputs = mutableListOf("-y", "-i", video.absolutePath, "-i", voice.absolutePath)
        val filters = mutableListOf("[1:a]volume=${"%.3f".format(project.ttsVolume)}[voice]")
        val labels = mutableListOf("[voice]")
        var index = 2
        val total = duration(voice)
        if (bgm != null) {
            inputs += listOf("-stream_loop", "-1", "-i", bgm.absolutePath)
            val fade = min(0.5, total.coerceAtLeast(0.1))
            filters += "[$index:a]volume=${"%.3f".format(project.bgmVolume)},atrim=0:${"%.3f".format(total)},asetpts=PTS-STARTPTS,afade=t=in:st=0:d=${"%.3f".format(fade)},adelay=800:all=1[bgm]"
            labels += "[bgm]"
            index++
        }
        if (sfx != null) {
            inputs += listOf("-i", sfx.absolutePath)
            filters += "[$index:a]volume=${"%.3f".format(project.introSfxVolume)},adelay=0:all=1[sfx]"
            labels += "[sfx]"
        }
        filters += "${labels.joinToString("")}amix=inputs=${labels.size}:duration=first:dropout_transition=2:normalize=0[aout]"
        run("混合背景音乐和音效", *inputs.toTypedArray(), "-filter_complex", filters.joinToString(";"),
            "-map", "0:v:0", "-map", "[aout]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-shortest", "-movflags", "+faststart", output.absolutePath)
    }

    private fun mixVoicePreview(project: VideoProject, voice: File, output: File): File {
        val bgm = store.bundledAudio(project.bgmId)
        val sfx = store.bundledAudio(project.introSfxId)
        if (bgm == null && sfx == null && project.ttsVolume == 1f) {
            Files.move(voice.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return output
        }
        val inputs = mutableListOf("-y", "-i", voice.absolutePath)
        val filters = mutableListOf("[0:a]volume=${"%.3f".format(project.ttsVolume)}[voice]")
        val labels = mutableListOf("[voice]")
        var index = 1
        val total = duration(voice)
        if (bgm != null) {
            inputs += listOf("-stream_loop", "-1", "-i", bgm.absolutePath)
            val fade = min(0.5, total.coerceAtLeast(0.1))
            filters += "[$index:a]volume=${"%.3f".format(project.bgmVolume)},atrim=0:${"%.3f".format(total)},asetpts=PTS-STARTPTS,afade=t=in:st=0:d=${"%.3f".format(fade)},adelay=800:all=1[bgm]"
            labels += "[bgm]"
            index++
        }
        if (sfx != null) {
            inputs += listOf("-i", sfx.absolutePath)
            filters += "[$index:a]volume=${"%.3f".format(project.introSfxVolume)},adelay=0:all=1[sfx]"
            labels += "[sfx]"
        }
        filters += "${labels.joinToString("")}amix=inputs=${labels.size}:duration=first:dropout_transition=2:normalize=0[aout]"
        run("生成配音预览", *inputs.toTypedArray(), "-filter_complex", filters.joinToString(";"), "-map", "[aout]",
            "-c:a", "aac", "-b:a", "192k", "-shortest", output.absolutePath)
        voice.delete()
        return output
    }

    fun concatAudio(project: VideoProject, output: File): File {
        require(project.shots.isNotEmpty()) { "暂无分镜配音" }
        val parts = project.shots.map { store.audio(project, it) }
        require(parts.all { it.isFile && it.length() > 1000 }) { "配音尚未全部完成" }
        val temp = File(store.directory(project.id), "temp").apply { mkdirs() }
        val list = File(temp, "voice_preview_list.txt")
        list.writeText(parts.joinToString("") { concatLine(it) })
        val pending = File(output.parentFile, "${output.nameWithoutExtension}.pending.m4a")
        run("配音试听合并", "-y", "-f", "concat", "-safe", "0", "-i", list.absolutePath, "-vn", "-c:a", "aac", "-b:a", "192k", pending.absolutePath)
        require(pending.length() > 1000) { "配音预览为空" }
        return mixVoicePreview(project, pending, output)
    }

    fun render(project: VideoProject, progress: (Int, Int) -> Unit, stage: (String) -> Unit = {}) {
        require(project.shots.isNotEmpty())
        val dir = store.directory(project.id)
        val temp = File(dir, "temp").apply { mkdirs() }
        val renderStamp = File(temp, "render_options.txt")
        val clipSignature = listOf("subtitle-2", project.imageSize, project.subtitlesEnabled, project.motionMode, project.transitionMode,
            project.coverShotId, project.coverText,
            project.bgmId, project.introSfxId, project.bgmVolume,
            project.introSfxVolume, project.ttsVolume,
            project.shots.joinToString(",") { "${it.id}:${it.cutoutEnabled}:${it.text.hashCode()}" }).joinToString("|")
        val legacySignatures = setOf("fade", "dissolve", "slideleft", "random", "none").map { transition ->
            listOf("subtitle-2", project.imageSize, project.subtitlesEnabled, project.motionMode, transition,
                project.coverShotId, project.coverText,
                project.bgmId, project.introSfxId, project.bgmVolume,
                project.introSfxVolume, project.ttsVolume,
                project.shots.joinToString(",") { "${it.id}:${it.cutoutEnabled}:${it.text.hashCode()}" }).joinToString("|")
        }.toSet()
        val savedSignature = renderStamp.readTextOrNull()
        val rebuildClips = savedSignature != clipSignature && savedSignature !in legacySignatures
        if (rebuildClips) temp.listFiles { file -> file.name.matches(Regex("clip_\\d{3}\\.mp4")) }
            ?.forEach { it.delete() }
        stage("正在生成视频片段")
        val images = project.shots.map { shot ->
            store.image(project, shot).also { image ->
                require(image.length() > 1000) { "第 ${shot.id} 镜缺少图片" }
            }
        }
        val parts = project.shots.map { shot ->
            store.audio(project, shot).also { audio ->
                require(audio.length() > 1000) { "第 ${shot.id} 镜缺少配音" }
            }
        }
        val durations = parts.mapIndexed { index, audio ->
            duration(audio).also { project.shots[index].duration = it }
        }
        val clips = project.shots.map { shot -> File(temp, "clip_%03d.mp4".format(shot.id)) }
        val completed = AtomicInteger(0)
        val progressLock = Any()
        // Ten FFmpeg jobs use the available phone memory while keeping the
        // render queue bounded instead of launching all encoders at once.
        runBlocking {
            val gate = Semaphore(10)
            clips.indices.map { index ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val shot = project.shots[index]
                        val clip = clips[index]
                        if (clip.length() < 1000) {
                            val pending = File(temp, clip.name + ".tmp.mp4")
                            val frameCount = ceil(durations[index] * 30).toInt().coerceAtLeast(1)
                            val (width, height) = canvasSize(project)
                            val subtitle = subtitleFilter(project, shot, temp, width, height)
                            val cover = coverFilter(project, shot, temp, width, height)
                            if (shot.cutoutEnabled) {
                                val cutout = cutoutFilter(project, shot, frameCount, width, height)
                                val overlays = subtitle + cover
                                val graph = if (overlays.isBlank()) cutout else
                                    "$cutout;[v]${overlays.removePrefix(",")}[out]"
                                val outputLabel = if (overlays.isBlank()) "[v]" else "[out]"
                                run("第 ${shot.id} 镜生成抠图视频片段", "-y", "-loop", "1", "-framerate", "30", "-i", images[index].absolutePath,
                                    "-filter_complex", graph, "-map", outputLabel, "-frames:v", frameCount.toString(), "-an",
                                    "-c:v", "mpeg4", "-q:v", "4", "-pix_fmt", "yuv420p", pending.absolutePath)
                            } else {
                                val filter = motionFilter(project, shot, frameCount, width, height) + subtitle + cover
                                run("生成视频片段", "-y", "-loop", "1", "-framerate", "30", "-i", images[index].absolutePath,
                                    "-vf", filter, "-frames:v", frameCount.toString(), "-an", "-c:v", "mpeg4",
                                    "-q:v", "4", "-pix_fmt", "yuv420p", pending.absolutePath)
                            }
                            Files.move(pending.toPath(), clip.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }
                        synchronized(progressLock) {
                            progress(completed.incrementAndGet(), project.shots.size)
                        }
                    }
                }
            }.awaitAll()
        }
        val audioList = File(temp, "audio_list.txt")
        audioList.writeText(parts.joinToString("") { concatLine(it) })
        val video = File(temp, "video.mp4")
        val voice = File(temp, "voice.m4a")
        stage("正在合并画面")
        mergeVideos(project, clips, durations, video, temp)
        stage("正在合并配音")
        run("合并配音", "-y", "-f", "concat", "-safe", "0", "-i", audioList.absolutePath, "-vn", "-c:a", "aac", "-b:a", "192k", voice.absolutePath)
        val pending = File(dir, "output/final.pending.mp4")
        stage("正在封装最终 MP4")
        muxFinalAudio(project, video, voice, pending)
        require(pending.length() > 1000) { "输出视频为空" }
        Files.move(pending.toPath(), store.output(project).toPath(), StandardCopyOption.REPLACE_EXISTING)
        project.duration = duration(store.output(project))
        renderStamp.writeText(clipSignature)
    }
}

private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()
