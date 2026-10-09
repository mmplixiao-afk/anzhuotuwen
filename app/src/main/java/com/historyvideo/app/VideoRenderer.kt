package com.historyvideo.app

import android.graphics.BitmapFactory
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
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

fun normalizeCoverText(value: String): String {
    val normalized = StringBuilder()
    var pendingSpace = false
    value.forEach { character ->
        if (character.isWhitespace()) {
            pendingSpace = normalized.isNotEmpty()
        } else {
            if (pendingSpace) normalized.append(' ')
            normalized.append(character)
            pendingSpace = false
        }
    }
    return normalized.toString()
}

class VideoRenderer(private val store: ProjectStore) {
    private data class SubtitleEvent(val start: Double, val end: Double, val text: String)
    private data class CoverLayout(val lines: List<String>, val fontSize: Int, val textHeight: Double)

    private companion object {
        const val SUBTITLE_FONT_SIZE = 120
        const val SUBTITLE_MIN_CHARS = 6
        const val SUBTITLE_MIN_SAFE_MARGIN = 48
        const val SUBTITLE_SAFE_MARGIN_RATIO = 0.065
        const val SUBTITLE_GLYPH_WIDTH_RATIO = 1.05
        const val COVER_TITLE_MAX_CHARS = 40
        const val COVER_TITLE_DELAY = 1.0
        const val RENDER_STYLE_VERSION = "subtitle-16-motion-3-transition-4-cutout-1"
        const val SUBTITLE_SPLIT_MARKS = "，。！？；：、,.!?;:…"
        const val SUBTITLE_CLOSING_QUOTES = "”’\"'」』）》】)]}〉〕〗〙〛"
        val spokenOnlyPhrases = listOf("他娘的", "奶奶的", "老子")
        val interjectionPattern = Regex(
            "(?<![\\p{L}\\p{N}_])(?:哎呀|哼哼|啧啧|嘿嘿|呵呵|哈哈|哼|切|啧|呸|嘿|唉|哎|嗯|哟)(?![\\p{L}\\p{N}_])"
        )
    }

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
        "1:1", "1024x1024", "1440x1440" -> 1440 to 1440
        else -> 1080 to 1920
    }

    private fun ffmpegPath(file: File): String = file.absolutePath.replace("\\", "/").replace(":", "\\:")

    private val motionModes = setOf("zoom_in", "zoom_out", "pan", "zoom_pan")

    private fun requestedMotionMode(project: VideoProject, shot: Shot): String {
        if (project.motionMode == "random") return "random"
        return project.motionMode.takeIf { it in motionModes }
            ?: shot.cameraMotion.trim().lowercase().takeIf { it in motionModes }
            ?: "random"
    }

    private fun newMotionPlan(requestedMode: String): MotionPlan {
        val mode = if (requestedMode == "random") motionModes.toList().random() else requestedMode
        val low = Random.nextDouble(1.02, 1.0600001)
        val high = Random.nextDouble(1.45, 1.6000001)
        val startZoom = if (mode == "zoom_out") high else low
        val endZoom = if (mode == "zoom_out") low else high
        val actualStartZoom = if (mode == "pan") high else startZoom
        val actualEndZoom = if (mode == "pan") high else endZoom
        val startMarginX = min(0.10, (actualStartZoom - 1.0) * 0.38)
        val startMarginY = min(0.10, (actualStartZoom - 1.0) * 0.38)
        val endMarginX = min(0.10, (actualEndZoom - 1.0) * 0.38)
        val endMarginY = min(0.10, (actualEndZoom - 1.0) * 0.38)
        var startX = Random.nextDouble(-startMarginX, startMarginX)
        var startY = Random.nextDouble(-startMarginY, startMarginY)
        var endX = Random.nextDouble(-endMarginX, endMarginX)
        var endY = Random.nextDouble(-endMarginY, endMarginY)
        if (mode == "zoom_in" || mode == "zoom_out") {
            startX = 0.0; startY = 0.0; endX = 0.0; endY = 0.0
        } else {
            val axis = listOf("x", "y").random()
            val startOffset = min(0.09, (actualStartZoom - 1.0) * 0.30)
            val endOffset = min(0.09, (actualEndZoom - 1.0) * 0.30)
            val direction = if (Random.nextBoolean()) 1.0 else -1.0
            if (axis == "x") {
                startX = -startOffset * direction; endX = endOffset * direction
            } else {
                startY = -startOffset * direction; endY = endOffset * direction
            }
        }
        return MotionPlan(
            mode,
            MotionPoint(actualStartZoom, startX, startY),
            MotionPoint(actualEndZoom, endX, endY)
        )
    }

    private fun motionFilter(plan: MotionPlan, frames: Int, width: Int, height: Int, preserveAlpha: Boolean = false): String {
        val progress = "min(1,on/${maxOf(1, frames - 1)})"
        fun interpolate(first: Double, last: Double) =
            "(${String.format(Locale.US, "%.9f", first)}+(${String.format(Locale.US, "%.9f", last - first)})*$progress)"
        val zoom = interpolate(plan.start.zoom, plan.end.zoom)
        val x = interpolate(plan.start.x, plan.end.x)
        val y = interpolate(plan.start.y, plan.end.y)
        val outputFormat = if (preserveAlpha) "gbrap" else "yuv420p"
        return "scale=${width * 2}:${height * 2}:force_original_aspect_ratio=increase,crop=${width * 2}:${height * 2},setsar=1," +
            "zoompan=z='$zoom':" +
            "x='max(0,min(iw-iw/zoom,(iw-iw/zoom)/2-$x*iw/zoom))':" +
            "y='max(0,min(ih-ih/zoom,(ih-ih/zoom)/2+$y*ih/zoom))':" +
            "d=1:s=${width}x${height}:fps=30,format=$outputFormat"
    }

    private fun motionPlanFor(project: VideoProject, shot: Shot): MotionPlan {
        val requested = requestedMotionMode(project, shot)
        val current = shot.motionPlan
        if (current != null && (requested == "random" || current.mode == requested)) return current
        return newMotionPlan(requested).also { shot.motionPlan = it }
    }

    private fun transitionPlansFor(project: VideoProject, durations: List<Double>): List<TransitionPlan> {
        val frameCounts = durations.map { ceil(it * 30.0).toInt().coerceAtLeast(1) }
        val signature = "$RENDER_STYLE_VERSION|${project.transitionMode}|${frameCounts.joinToString(",")}" 
        if (project.transitionPlanSignature == signature && project.transitionPlans.size == maxOf(0, durations.size - 1)) {
            return project.transitionPlans
        }
        val choices = listOf("fade", "dissolve", "smoothleft", "zoomin", "fadeblack")
        var previousType = ""
        val plans = durations.zipWithNext().mapIndexed { index, (leftDuration, rightDuration) ->
            if (index == 0 || project.transitionMode == "none") return@mapIndexed TransitionPlan("none", 0)
            val allowedFrames = (min(leftDuration, rightDuration) * 30.0 / 3.0).toInt()
            val requestedFrames = listOf(8, 10, 12).random()
            val frames = min(requestedFrames, allowedFrames) - (min(requestedFrames, allowedFrames) % 2)
            if (frames < 2) return@mapIndexed TransitionPlan("none", 0)
            val type = if (project.transitionMode == "random") {
                choices.filter { it != previousType }.random()
            } else when (project.transitionMode) {
                "slideleft" -> "smoothleft"
                else -> project.transitionMode.takeIf { it in choices } ?: "fade"
            }
            previousType = type
            TransitionPlan(type, frames)
        }.toMutableList()
        project.transitionPlans = plans
        project.transitionPlanSignature = signature
        return plans
    }

    private fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

    private fun isUnicodePunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt() -> true
        else -> false
    }

    private fun semanticText(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (Character.isLetterOrDigit(codePoint)) appendCodePoint(codePoint)
            index += Character.charCount(codePoint)
        }
    }

    private fun visibleLength(value: String): Int {
        var count = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (!Character.isWhitespace(codePoint) && !isUnicodePunctuation(codePoint)) count++
            index += Character.charCount(codePoint)
        }
        return count
    }

    private fun splitAtPunctuation(value: String): List<String> {
        val source = value.replace(Regex("[ \\t\\f\\u000B]+"), " ").trim()
        if (source.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            if (character == '\r' || character == '\n') {
                current.toString().trim().takeIf { it.isNotBlank() }?.let(chunks::add)
                current.setLength(0)
                index++
                continue
            }
            current.append(character)
            if (character in SUBTITLE_SPLIT_MARKS) {
                while (index + 1 < source.length &&
                    (source[index + 1] in SUBTITLE_SPLIT_MARKS || source[index + 1] in SUBTITLE_CLOSING_QUOTES)) {
                    current.append(source[++index])
                }
                current.toString().trim().takeIf { it.isNotBlank() }?.let(chunks::add)
                current.setLength(0)
            }
            index++
        }
        current.toString().trim().takeIf { it.isNotBlank() }?.let(chunks::add)
        return chunks
    }

    private fun validatedSubtitleChunks(voiceover: String, modelChunks: List<String>): List<String> {
        val refined = modelChunks.flatMap(::splitAtPunctuation)
        if (refined.isNotEmpty() && semanticText(refined.joinToString("")) == semanticText(voiceover)) return refined
        return splitAtPunctuation(voiceover)
    }

    private fun openingBody(voiceover: String): String? {
        val punctuationIndex = voiceover.indexOfFirst { it in "。！？!?" }
        if (punctuationIndex < 0) return null
        var end = punctuationIndex + 1
        while (end < voiceover.length && voiceover[end] in SUBTITLE_CLOSING_QUOTES) end++
        return voiceover.substring(end).trimStart()
    }

    private fun subtitleSource(project: VideoProject, shot: Shot): Pair<String, List<String>> {
        val hasCover = normalizeCoverText(project.coverText).isNotBlank()
        if (!hasCover) return shot.text to shot.subtitleChunks
        if (shot.id == 1) return "" to emptyList()
        if (shot.id == 2) {
            val firstShot = project.shots.firstOrNull { it.id == 1 }
            val remainder = firstShot?.let { openingBody(it.text) }
            if (!remainder.isNullOrBlank()) return remainder + shot.text to emptyList()
        }
        return shot.text to shot.subtitleChunks
    }

    private fun subtitleCharWidth(codePoint: Int): Double {
        if (Character.isWhitespace(codePoint)) return 0.35
        return if (codePoint >= 0x2E80 || Character.UnicodeBlock.of(codePoint) in setOf(
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO,
                Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO,
                Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
                Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            )) 1.0 else 0.55
    }

    private fun textWidthUnits(value: String): Double {
        var width = 0.0
        var offset = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            width += subtitleCharWidth(codePoint)
            offset += Character.charCount(codePoint)
        }
        return width
    }

    private fun rebalanceShortTail(pieces: List<String>, maxUnits: Double): List<String> {
        val balanced = pieces.filter(String::isNotEmpty).toMutableList()
        if (balanced.size < 2) return balanced
        val minUnits = min(SUBTITLE_MIN_CHARS.toDouble(), maxUnits * 0.55)
        var index = balanced.lastIndex
        while (index > 0) {
            val current = balanced[index]
            if (textWidthUnits(current) >= minUnits) {
                index--
                continue
            }
            val combined = balanced[index - 1] + current
            if (textWidthUnits(combined) <= maxUnits) {
                balanced[index - 1] = combined
                balanced.removeAt(index)
                index = min(index - 1, balanced.lastIndex)
                continue
            }
            var bestScore = Double.POSITIVE_INFINITY
            var bestSplit: Pair<String, String>? = null
            for (cut in 1 until combined.length) {
                if (Character.isLowSurrogate(combined[cut])) continue
                val left = combined.substring(0, cut).trim()
                val right = combined.substring(cut).trim()
                if (left.isEmpty() || right.isEmpty()) continue
                val leftWidth = textWidthUnits(left)
                val rightWidth = textWidthUnits(right)
                if (leftWidth > maxUnits || rightWidth > maxUnits) continue
                val shortfall = maxOf(0.0, minUnits - min(leftWidth, rightWidth))
                val punctuationBonus = if (left.last() in SUBTITLE_SPLIT_MARKS) 0.25 else 0.0
                val score = shortfall * 100 + kotlin.math.abs(leftWidth - rightWidth) - punctuationBonus
                if (score < bestScore) {
                    bestScore = score
                    bestSplit = left to right
                }
            }
            if (bestSplit != null) {
                balanced[index - 1] = bestSplit.first
                balanced[index] = bestSplit.second
            }
            index--
        }
        return balanced
    }

    private fun splitForSafeWidth(value: String, maxUnits: Double): List<String> {
        val clean = value.trim()
        if (clean.isEmpty() || textWidthUnits(clean) <= maxUnits) return listOf(clean)
        val pieces = mutableListOf<String>()
        var remaining = clean
        while (remaining.isNotEmpty()) {
            if (textWidthUnits(remaining) <= maxUnits) {
                pieces += remaining.trim()
                break
            }
            var width = 0.0
            var cut = 0
            var punctuationCut = 0
            var index = 0
            while (index < remaining.length) {
                val codePoint = remaining.codePointAt(index)
                val charCount = Character.charCount(codePoint)
                val nextWidth = width + subtitleCharWidth(codePoint)
                if (nextWidth > maxUnits) break
                width = nextWidth
                cut = index + charCount
                if (charCount == 1 && remaining[index] in SUBTITLE_SPLIT_MARKS) punctuationCut = cut
                index += charCount
            }
            if (punctuationCut >= maxOf(1, (cut * 0.55).toInt())) cut = punctuationCut
            cut = maxOf(1, cut)
            val piece = remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trim()
            if (piece.isNotEmpty()) pieces += piece
        }
        return rebalanceShortTail(pieces, maxUnits).ifEmpty { listOf(clean) }
    }

    private fun subtitleDisplayChunks(chunks: List<String>): List<String> {
        val source = chunks.joinToString("")
        val hidden = BooleanArray(source.length)
        fun hideMatches(pattern: Regex) {
            pattern.findAll(source).forEach { match ->
                for (index in match.range) hidden[index] = true
            }
        }
        spokenOnlyPhrases.forEach { phrase ->
            var start = 0
            while (start < source.length) {
                val match = source.indexOf(phrase, start)
                if (match < 0) break
                for (index in match until match + phrase.length) hidden[index] = true
                start = match + phrase.length
            }
        }
        hideMatches(interjectionPattern)
        var punctuationIndex = 0
        while (punctuationIndex < source.length) {
            val codePoint = source.codePointAt(punctuationIndex)
            if (isUnicodePunctuation(codePoint)) {
                repeat(Character.charCount(codePoint)) { hidden[punctuationIndex + it] = true }
            }
            punctuationIndex += Character.charCount(codePoint)
        }
        var offset = 0
        return chunks.map { chunk ->
            val display = buildString {
                chunk.forEachIndexed { index, character ->
                    if (!hidden[offset + index]) append(character)
                }
            }.replace(Regex("(?U)\\s+"), " ").trim()
            offset += chunk.length
            display
        }
    }

    private fun mergeShortVisibleChunks(chunks: List<String>): List<String> {
        if (chunks.size < 2) return chunks
        val display = subtitleDisplayChunks(chunks)
        val result = mutableListOf<String>()
        val hasVisibleText = mutableListOf<Boolean>()
        val pendingSource = StringBuilder()
        val pendingDisplay = StringBuilder()
        fun flushPending() {
            if (pendingSource.isNotEmpty()) {
                result += pendingSource.toString()
                hasVisibleText += visibleLength(pendingDisplay.toString()) > 0
                pendingSource.setLength(0)
                pendingDisplay.setLength(0)
            }
        }
        chunks.forEachIndexed { index, source ->
            val shown = display[index]
            if (shown.isBlank()) {
                flushPending()
                result += source
                hasVisibleText += false
            } else {
                pendingSource.append(source)
                pendingDisplay.append(shown)
                if (visibleLength(pendingDisplay.toString()) >= SUBTITLE_MIN_CHARS) flushPending()
            }
        }
        if (pendingSource.isNotEmpty()) {
            val previousVisible = hasVisibleText.indexOfLast { it }
            if (previousVisible == result.lastIndex && previousVisible >= 0) {
                result[previousVisible] += pendingSource.toString()
            } else flushPending()
        }
        return result.ifEmpty { chunks }
    }

    private fun subtitleEvents(project: VideoProject, shot: Shot, width: Int): List<SubtitleEvent> {
        val (voiceover, modelChunks) = subtitleSource(project, shot)
        if (voiceover.isBlank()) return emptyList()
        val chunks = mergeShortVisibleChunks(validatedSubtitleChunks(voiceover, modelChunks))
        if (chunks.isEmpty()) return emptyList()
        val displays = subtitleDisplayChunks(chunks)
        val margin = maxOf(SUBTITLE_MIN_SAFE_MARGIN, (width * SUBTITLE_SAFE_MARGIN_RATIO).roundToInt())
        val usableWidth = maxOf(SUBTITLE_FONT_SIZE * 4, width - margin * 2)
        val maxUnits = usableWidth / (SUBTITLE_FONT_SIZE * SUBTITLE_GLYPH_WIDTH_RATIO)
        val weights = chunks.map { maxOf(1, codePointLength(it)) }
        val totalWeight = weights.sum().coerceAtLeast(1)
        val duration = shot.duration.coerceAtLeast(0.1)
        var cursor = 0.0
        val events = mutableListOf<SubtitleEvent>()
        chunks.forEachIndexed { index, chunk ->
            val chunkEnd = if (index == chunks.lastIndex) duration
            else min(duration, cursor + duration * weights[index] / totalWeight)
            val display = displays[index]
            if (display.isNotBlank()) {
                val pieces = splitForSafeWidth(display, maxUnits)
                val pieceWeights = pieces.map { maxOf(1.0, textWidthUnits(it)) }
                val pieceTotal = pieceWeights.sum().coerceAtLeast(1.0)
                var pieceCursor = cursor
                pieces.forEachIndexed { pieceIndex, piece ->
                    val pieceEnd = if (pieceIndex == pieces.lastIndex) chunkEnd else
                        min(chunkEnd, pieceCursor + (chunkEnd - cursor) * pieceWeights[pieceIndex] / pieceTotal)
                    events += SubtitleEvent(pieceCursor, maxOf(pieceCursor + 0.08, pieceEnd), piece)
                    pieceCursor = pieceEnd
                }
            }
            cursor = chunkEnd
        }
        return events
    }

    private fun subtitleFilter(project: VideoProject, shot: Shot, dir: File, width: Int, height: Int): String {
        if (!project.subtitlesEnabled) return ""
        val events = subtitleEvents(project, shot, width)
        if (events.isEmpty()) return ""
        val fontPath = ffmpegPath(store.bundledFont("subtitle"))
        return events.mapIndexed { index, event ->
            val file = File(dir, "subtitle_%03d_%03d.txt".format(shot.id, index)).apply { writeText(event.text) }
            val enable = "between(t,${String.format(Locale.US, "%.3f", event.start)},${String.format(Locale.US, "%.3f", event.end)})"
            ",drawtext=fontfile='$fontPath':textfile='${ffmpegPath(file)}':" +
                "fontcolor=white:fontsize=$SUBTITLE_FONT_SIZE:box=0:" +
                "borderw=4:bordercolor=0x111111:shadowcolor=black@0.44:shadowx=0:shadowy=1:" +
                "x=(w-text_w)/2:y=h-text_h-45:enable='$enable'"
        }.joinToString("")
    }

    private fun coverCharWidth(codePoint: Int): Double =
        if (codePoint >= 0x2E80 || Character.UnicodeBlock.of(codePoint) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            )) 1.0 else 0.55

    private fun coverLayout(text: String, width: Int, height: Int): CoverLayout {
        var fontSize = maxOf(24, minOf(width * 0.13, height * 0.20).roundToInt())
        while (true) {
            val maxUnits = width * 0.84 / (fontSize * 1.08)
            val lines = mutableListOf<String>()
            val line = StringBuilder()
            var lineUnits = 0.0
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val charCount = Character.charCount(codePoint)
                val weight = coverCharWidth(codePoint)
                if (line.isNotEmpty() && lineUnits + weight > maxUnits + 1e-6) {
                    lines += line.toString()
                    line.setLength(0)
                    lineUnits = 0.0
                }
                line.appendCodePoint(codePoint)
                lineUnits += weight
                offset += charCount
            }
            if (line.isNotEmpty()) lines += line.toString()
            val textHeight = lines.size * fontSize * 1.15
            if (textHeight <= height * 0.55 || fontSize <= 24) return CoverLayout(lines, fontSize, textHeight)
            fontSize = maxOf(24, fontSize - 2)
        }
    }

    private fun coverFilter(project: VideoProject, shot: Shot, dir: File, width: Int, height: Int, duration: Double): String {
        val text = normalizeCoverText(project.coverText)
        if (shot.id != 1 || text.isBlank()) return ""
        val layout = coverLayout(text, width, height)
        val fontPath = ffmpegPath(store.bundledFont("cover"))
        val outline = maxOf(3, (layout.fontSize * 0.065).roundToInt())
        val shadow = maxOf(2, (layout.fontSize * 0.025).roundToInt())
        val entry = min(0.42, duration.coerceAtLeast(0.1) * 0.55)
        val firstPhase = entry * 0.65
        val secondPhase = entry * 0.82
        val finalTop = (height - layout.textHeight) / 2.0
        val overshootTop = finalTop - height * 0.055
        val reboundTop = finalTop + height * 0.018
        val initialTop = height + layout.textHeight * 0.6
        val y = "if(lt(t,$COVER_TITLE_DELAY),$initialTop," +
            "if(lt(t,${String.format(Locale.US, "%.4f", COVER_TITLE_DELAY + firstPhase)})," +
            "$initialTop+($overshootTop-$initialTop)*(t-$COVER_TITLE_DELAY)/${String.format(Locale.US, "%.4f", firstPhase)}," +
            "if(lt(t,${String.format(Locale.US, "%.4f", COVER_TITLE_DELAY + secondPhase)})," +
            "$overshootTop+($reboundTop-$overshootTop)*(t-${String.format(Locale.US, "%.4f", COVER_TITLE_DELAY + firstPhase)})/${String.format(Locale.US, "%.4f", entry * 0.17)}," +
            "if(lt(t,${String.format(Locale.US, "%.4f", COVER_TITLE_DELAY + entry)})," +
            "$reboundTop+($finalTop-$reboundTop)*(t-${String.format(Locale.US, "%.4f", COVER_TITLE_DELAY + secondPhase)})/${String.format(Locale.US, "%.4f", entry * 0.18)},$finalTop))))"
        return layout.lines.mapIndexed { index, line ->
            val file = File(dir, "cover_title_%02d.txt".format(index)).apply { writeText(line) }
            ",drawtext=fontfile='$fontPath':textfile='${ffmpegPath(file)}':" +
                "fontcolor=white:fontsize=${layout.fontSize}:box=0:borderw=$outline:bordercolor=black:" +
                "shadowcolor=black:shadowx=0:shadowy=$shadow:x=(w-text_w)/2:" +
                "y='($y)+${String.format(Locale.US, "%.3f", index * layout.fontSize * 1.15)}'"
        }.joinToString("")
    }

    private data class LayerFiles(val background: File, val foreground: File)

    private data class NormalizedBounds(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private fun layerFiles(project: VideoProject, shot: Shot): LayerFiles {
        val directory = File(store.directory(project.id), "layers/shot_${shot.id.toString().padStart(2, '0')}")
        val background = File(directory, "background.png")
        val foreground = File(directory, "foreground.png")
        require(background.isFile && background.length() > 1000 && foreground.isFile && foreground.length() > 1000) {
            "第 ${shot.id} 镜人物分层素材尚未生成；请确认两份模型已导入后重试。"
        }
        return LayerFiles(background, foreground)
    }

    private fun foregroundBounds(file: File): NormalizedBounds {
        val options = BitmapFactory.Options().apply { inSampleSize = 8; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("无法读取人物透明图：${file.name}")
        try {
            var left = bitmap.width
            var top = bitmap.height
            var right = -1
            var bottom = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) >= 12) {
                    left = min(left, x); top = min(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            require(right >= left && bottom >= top) { "人物透明图中没有可见人物：${file.name}" }
            return NormalizedBounds(
                left.toDouble() / bitmap.width, top.toDouble() / bitmap.height,
                (right + 1.0) / bitmap.width, (bottom + 1.0) / bitmap.height
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun fitForegroundMotion(plan: MotionPlan, bounds: NormalizedBounds): MotionPlan {
        val safeLeft = maxOf(0.0, bounds.left - 0.015)
        val safeTop = maxOf(0.0, bounds.top - 0.015)
        val safeRight = min(1.0, bounds.right + 0.015)
        val safeBottom = min(1.0, bounds.bottom + 0.015)
        val maxZoom = maxOf(1.0, min(1.0 / maxOf(safeRight - safeLeft, 1e-6), 1.0 / maxOf(safeBottom - safeTop, 1e-6)))
        fun fit(point: MotionPoint): MotionPoint {
            val zoom = point.zoom.coerceIn(1.0, maxZoom)
            val viewport = 1.0 / zoom
            val desiredLeft = (1.0 - viewport) / 2.0 - point.x / zoom
            val desiredTop = (1.0 - viewport) / 2.0 + point.y / zoom
            val minLeft = maxOf(0.0, safeRight - viewport)
            val maxLeft = minOf(safeLeft, 1.0 - viewport)
            val minTop = maxOf(0.0, safeBottom - viewport)
            val maxTop = minOf(safeTop, 1.0 - viewport)
            val cropLeft = maxOf(minLeft, min(desiredLeft, maxOf(minLeft, maxLeft)))
            val cropTop = maxOf(minTop, min(desiredTop, maxOf(minTop, maxTop)))
            return MotionPoint(
                zoom,
                ((1.0 - viewport) / 2.0 - cropLeft) * zoom,
                (cropTop - (1.0 - viewport) / 2.0) * zoom
            )
        }
        return MotionPlan(plan.mode, fit(plan.start), fit(plan.end))
    }

    private fun ensureCutoutPlans(shot: Shot) {
        val direction = shot.cutoutMirrorX ?: Random.nextBoolean().also { shot.cutoutMirrorX = it }
        val sign = if (direction) -1.0 else 1.0
        if (shot.cutoutBackgroundPlan == null || shot.cutoutForegroundPlan == null) {
            shot.cutoutBackgroundPlan = MotionPlan(
                "zoom_out",
                MotionPoint(Random.nextDouble(1.45, 1.6000001), 0.020 * sign, 0.0),
                MotionPoint(1.045, -0.013 * sign, 0.0)
            )
            shot.cutoutForegroundPlan = MotionPlan(
                "zoom_in",
                MotionPoint(1.055, -0.016 * sign, 0.0),
                MotionPoint(Random.nextDouble(1.50, 1.6000001), 0.028 * sign, 0.0)
            )
        }
    }

    private fun cutoutFilter(
        backgroundPlan: MotionPlan,
        foregroundPlan: MotionPlan,
        frames: Int,
        width: Int,
        height: Int,
        duration: Double
    ): String {
        val background = motionFilter(backgroundPlan, frames, width, height)
        val foreground = motionFilter(foregroundPlan, frames, width, height, preserveAlpha = true)
        val entry = min(0.28, maxOf(1.0 / 30.0, duration / 3.0))
        val entrySeconds = String.format(Locale.US, "%.6f", entry)
        val focusSeconds = String.format(Locale.US, "%.6f", min(0.08, entry))
        return "[0:v]$background[bg];" +
            "[1:v]$foreground,split[sharp][blur];" +
            "[blur]boxblur=0:0:12:1[blurred];" +
            "[sharp][blurred]blend=all_expr='A*min(1,T/$entrySeconds)+B*(1-min(1,T/$entrySeconds))'," +
            "format=rgba,fade=t=in:st=0:d=$focusSeconds:alpha=1[fg];" +
            "[bg][fg]overlay=x=0:y='${height * 0.18}*pow(max(0,1-t/$entrySeconds),3)':" +
            "shortest=1:format=auto,format=yuv420p[v]"
    }

    private fun renderOpeningTemplate(
        template: String,
        imagePaths: List<File>,
        output: File,
        frameCount: Int,
        imageSeconds: Float,
        width: Int,
        height: Int
    ) {
        val templateTransitions = mapOf(
            "life_copy_fast_cut" to "dissolve",
            "life_copy_expand_cut" to "vertopen",
            "life_copy_flash_horizontal" to "smoothleft",
            "life_copy_flash_vertical" to "smoothup",
            "life_copy_staggered_mask" to "coverdown",
            "life_copy_mosaic_collage" to "pixelize",
            "life_copy_cinematic_reveal" to "circleopen",
            "life_copy_tile_build_impact" to "zoomin"
        )
        val transition = templateTransitions[template]
            ?: error("不支持的开头模板：$template")
        val selectedImages = imagePaths.take(5).reversed()
        require(selectedImages.isNotEmpty()) { "开头模板缺少镜头图片" }
        val imageFrameCount = (imageSeconds.coerceIn(0.08f, 3f) * 30f).roundToInt().coerceAtLeast(3)
        val segmentCounts = mutableListOf<Int>()
        var remainingFrames = frameCount
        selectedImages.forEach { _ ->
            if (remainingFrames > 0) {
                val count = min(imageFrameCount, remainingFrames)
                segmentCounts += count
                remainingFrames -= count
            }
        }
        if (segmentCounts.size < 2 || frameCount <= 12) {
            val basicPlan = MotionPlan("zoom_in", MotionPoint(1.02, 0.0, 0.0), MotionPoint(1.12, 0.0, 0.0))
            val filter = motionFilter(basicPlan, frameCount, width, height)
            run("生成开头模板", "-y", "-loop", "1", "-framerate", "30", "-i", selectedImages.first().absolutePath,
                "-vf", filter, "-frames:v", frameCount.toString(), "-an", "-c:v", "mpeg4", "-q:v", "4",
                "-pix_fmt", "yuv420p", output.absolutePath)
            return
        }
        val args = mutableListOf("-y")
        selectedImages.take(segmentCounts.size).forEach { image ->
            args += listOf("-loop", "1", "-framerate", "30", "-i", image.absolutePath)
        }
        val filters = StringBuilder()
        segmentCounts.forEachIndexed { index, count ->
            val seconds = String.format(Locale.US, "%.6f", count / 30.0)
            filters.append("[$index:v]scale=$width:$height:force_original_aspect_ratio=increase,")
                .append("crop=$width:$height,setsar=1,fps=30,trim=duration=$seconds,")
                .append("settb=1/30,setpts=PTS-STARTPTS,format=yuv444p[v$index];")
        }
        var current = "v0"
        var currentFrames = segmentCounts.first()
        for (index in 1 until segmentCounts.size) {
            val transitionFrames = min(8, min(currentFrames, segmentCounts[index]) / 2)
            if (transitionFrames < 2) {
                val next = "cut$index"
                filters.append("[$current][v$index]concat=n=2:v=1:a=0[$next];")
                current = next
                currentFrames += segmentCounts[index]
            } else {
                val seconds = String.format(Locale.US, "%.6f", transitionFrames / 30.0)
                val offset = String.format(Locale.US, "%.6f", (currentFrames - transitionFrames) / 30.0)
                val next = "intro$index"
                filters.append("[$current][v$index]xfade=transition=$transition:duration=$seconds:offset=$offset[$next];")
                current = next
                currentFrames += segmentCounts[index] - transitionFrames
            }
        }
        val result = "[opening]"
        val holdFrames = (frameCount - currentFrames).coerceAtLeast(0)
        val hold = String.format(Locale.US, "%.6f", holdFrames / 30.0)
        filters.append("[$current]tpad=stop_mode=clone:stop_duration=$hold,fps=30,")
            .append("trim=end_frame=$frameCount,format=yuv420p$result")
        args += listOf(
            "-filter_complex", filters.toString(), "-map", result,
            "-frames:v", frameCount.toString(), "-an", "-c:v", "mpeg4", "-q:v", "4",
            "-pix_fmt", "yuv420p", output.absolutePath
        )
        run("生成开头模板", *args.toTypedArray())
    }

    private fun concatLine(file: File): String = "file '${file.absolutePath.replace("\\", "/").replace("'", "'\\''")}'\n"

    private fun concatVideos(clips: List<File>, output: File, temp: File) {
        val list = File(temp, "video_list.txt")
        list.writeText(clips.joinToString("") { concatLine(it) })
        run("合并画面", "-y", "-f", "concat", "-safe", "0", "-i", list.absolutePath, "-c", "copy", output.absolutePath)
    }

    private fun mergeVideos(
        clips: List<File>,
        durations: List<Double>,
        transitionPlans: List<TransitionPlan>,
        output: File,
        temp: File
    ) {
        if (clips.size < 2 || transitionPlans.none { it.frames >= 2 && it.type != "none" }) {
            concatVideos(clips, output, temp)
            return
        }
        val frameCounts = durations.map { ceil(it * 30.0).toInt().coerceAtLeast(1) }
        val filters = StringBuilder()
        val incoming = MutableList<String?>(clips.size) { null }
        val body = MutableList(clips.size) { "" }
        val outgoing = MutableList<String?>(clips.size) { null }
        clips.indices.forEach { i ->
            val hasIncoming = i > 0 && transitionPlans.getOrNull(i - 1)?.let { it.frames >= 2 && it.type != "none" } == true
            val hasOutgoing = i < clips.lastIndex && transitionPlans.getOrNull(i)?.let { it.frames >= 2 && it.type != "none" } == true
            val labels = mutableListOf<String>()
            if (hasIncoming) { incoming[i] = "head$i"; labels += "head$i" }
            body[i] = "body$i"; labels += body[i]
            if (hasOutgoing) { outgoing[i] = "tail$i"; labels += "tail$i" }
            filters.append("[$i:v]fps=30,format=yuv444p,setsar=1,settb=1/30,setpts=PTS-STARTPTS[norm$i];")
            if (labels.size == 1) {
                filters.append("[norm$i]null[${labels.single()}];")
            } else {
                filters.append("[norm$i]split=${labels.size}${labels.joinToString("") { "[$it]" }};")
            }
        }
        clips.indices.forEach { i ->
            val incomingFrames = if (incoming[i] == null) 0 else transitionPlans[i - 1].frames / 2
            val outgoingFrames = if (outgoing[i] == null) 0 else transitionPlans[i].frames / 2
            val startFrame = incomingFrames
            val endFrame = (frameCounts[i] - outgoingFrames).coerceAtLeast(startFrame + 1)
            filters.append("[${body[i]}]trim=start_frame=$startFrame:end_frame=$endFrame,settb=1/30,setpts=PTS-STARTPTS[clip$i];")
        }
        val segments = mutableListOf<String>()
        clips.indices.forEach { i ->
            segments += "clip$i"
            if (i == clips.lastIndex || outgoing[i] == null) return@forEach
            val plan = transitionPlans[i]
            val transitionFrames = plan.frames - (plan.frames % 2)
            val halfFrames = transitionFrames / 2
            val transitionSeconds = String.format(Locale.US, "%.6f", transitionFrames / 30.0)
            val halfSeconds = String.format(Locale.US, "%.6f", halfFrames / 30.0)
            val oldTail = "oldtail$i"
            val newHead = "newhead$i"
            val transition = "transition$i"
            filters.append("[${outgoing[i]}]trim=start_frame=${frameCounts[i] - halfFrames}:end_frame=${frameCounts[i]},")
                .append("settb=1/30,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=$halfSeconds[$oldTail];")
            filters.append("[${incoming[i + 1]}]trim=start_frame=0:end_frame=$halfFrames,")
                .append("settb=1/30,setpts=PTS-STARTPTS,tpad=start_mode=clone:start_duration=$halfSeconds[$newHead];")
            filters.append("[$oldTail][$newHead]xfade=transition=${plan.type}:duration=$transitionSeconds:offset=0[$transition];")
            segments += transition
        }
        val totalFrames = frameCounts.sum()
        val finalLabel = "[final]"
        filters.append("${segments.joinToString("") { "[$it]" }}concat=n=${segments.size}:v=1:a=0,")
            .append("fps=30,trim=end_frame=$totalFrames,format=yuv420p$finalLabel")
        val inputs = clips.flatMap { listOf("-i", it.absolutePath) }.toTypedArray()
        run("生成转场", "-y", *inputs, "-filter_complex_threads", "8", "-filter_complex", filters.toString(),
            "-map", finalLabel, "-an", "-c:v", "mpeg4", "-q:v", "4", "-threads", "8",
            "-r", "30", "-frames:v", totalFrames.toString(), "-pix_fmt", "yuv420p", output.absolutePath)
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
        val normalizedCover = normalizeCoverText(project.coverText)
        require(normalizedCover.isNotBlank()) { "封面文案必填，请先填写封面文案" }
        require(codePointLength(normalizedCover) <= COVER_TITLE_MAX_CHARS) { "封面文案最多 40 个字符，请缩短后重试" }
        project.coverText = normalizedCover
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
        val openingInputs = if (project.openingTemplate == "none") "" else images.take(5).joinToString("|") {
            "${it.length()}:${it.lastModified()}"
        }
        val openingInputsStamp = File(temp, "opening_template_inputs.txt")
        if (openingInputsStamp.readTextOrNull() != openingInputs) {
            File(temp, "clip_001.mp4").delete()
        }
        val cutoutProcessor = CutoutProcessor(store)
        val cutoutShots = project.shots.filter { it.cutoutEnabled }
        val cutoutIndexes = cutoutShots.mapIndexed { index, shot -> shot.id to index + 1 }.toMap()
        project.shots.forEach { shot ->
            motionPlanFor(project, shot)
            if (shot.cutoutEnabled) {
                val index = cutoutIndexes[shot.id] ?: 1
                stage("正在处理抠图镜头 $index/${cutoutShots.size}：识别第 ${shot.id} 镜人物并修补背景")
                cutoutProcessor.prepare(project, shot, canvasSize(project))
                layerFiles(project, shot)
                ensureCutoutPlans(shot)
            }
        }
        val transitionPlans = transitionPlansFor(project, durations)
        store.save(project)
        val clipSignature = listOf(
            RENDER_STYLE_VERSION, project.imageSize, project.subtitlesEnabled, project.motionMode,
            project.transitionMode, transitionPlans.joinToString(",") { "${it.type}:${it.frames}" },
            project.coverShotId, project.coverText, project.openingTemplate, project.introImageSeconds,
            project.bgmId, project.introSfxId, project.bgmVolume, project.introSfxVolume, project.ttsVolume,
            project.shots.joinToString(",") { shot ->
                val layers = if (shot.cutoutEnabled) layerFiles(project, shot).let {
                    val sourceStamp = File(it.background.parentFile, "source.txt").readTextOrNull().orEmpty()
                    "$sourceStamp:${it.background.length()}:${it.background.lastModified()}:${it.foreground.length()}:${it.foreground.lastModified()}"
                } else ""
                "${shot.id}:${shot.cutoutEnabled}:${shot.text.hashCode()}:${shot.subtitleChunks.joinToString("\u0000").hashCode()}:" +
                    "${shot.motionPlan}:${shot.cutoutBackgroundPlan}:${shot.cutoutForegroundPlan}:$layers"
            }
        ).joinToString("|")
        val savedSignature = renderStamp.readTextOrNull()
        val rebuildClips = savedSignature != clipSignature
        if (rebuildClips) temp.listFiles { file -> file.name.matches(Regex("clip_\\d{3}\\.mp4")) }
            ?.forEach { it.delete() }
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
                            val cover = coverFilter(project, shot, temp, width, height, durations[index])
                            if (shot.cutoutEnabled) {
                                val layers = layerFiles(project, shot)
                                val foregroundPlan = fitForegroundMotion(
                                    shot.cutoutForegroundPlan ?: error("第 ${shot.id} 镜人物运动方案丢失"),
                                    foregroundBounds(layers.foreground)
                                )
                                val cutout = cutoutFilter(
                                    shot.cutoutBackgroundPlan ?: error("第 ${shot.id} 镜背景运动方案丢失"),
                                    foregroundPlan, frameCount, width, height, durations[index]
                                )
                                val overlays = subtitle + cover
                                val graph = if (overlays.isBlank()) cutout else
                                    "$cutout;[v]${overlays.removePrefix(",")}[out]"
                                val outputLabel = if (overlays.isBlank()) "[v]" else "[out]"
                                run("第 ${shot.id} 镜生成抠图视频片段", "-y",
                                    "-loop", "1", "-framerate", "30", "-i", layers.background.absolutePath,
                                    "-loop", "1", "-framerate", "30", "-i", layers.foreground.absolutePath,
                                    "-filter_complex", graph, "-map", outputLabel, "-frames:v", frameCount.toString(), "-an",
                                    "-c:v", "mpeg4", "-q:v", "4", "-pix_fmt", "yuv420p", pending.absolutePath)
                            } else {
                                if (shot.id == 1 && project.openingTemplate != "none") {
                                    val openingBase = File(temp, "opening_template_base.mp4")
                                    openingBase.delete()
                                    renderOpeningTemplate(
                                        project.openingTemplate, images, openingBase, frameCount,
                                        project.introImageSeconds, width, height
                                    )
                                    val overlayFilter = motionFilter(motionPlanFor(project, shot), frameCount, width, height) + subtitle + cover
                                    run("叠加封面文案", "-y", "-i", openingBase.absolutePath, "-vf", overlayFilter,
                                        "-frames:v", frameCount.toString(), "-an", "-c:v", "mpeg4", "-q:v", "4",
                                        "-pix_fmt", "yuv420p", pending.absolutePath)
                                    openingBase.delete()
                                } else {
                                    val filter = motionFilter(motionPlanFor(project, shot), frameCount, width, height) + subtitle + cover
                                    run("生成视频片段", "-y", "-loop", "1", "-framerate", "30", "-i", images[index].absolutePath,
                                        "-vf", filter, "-frames:v", frameCount.toString(), "-an", "-c:v", "mpeg4",
                                        "-q:v", "4", "-pix_fmt", "yuv420p", pending.absolutePath)
                                }
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
        mergeVideos(clips, durations, transitionPlans, video, temp)
        stage("正在合并配音")
        run("合并配音", "-y", "-f", "concat", "-safe", "0", "-i", audioList.absolutePath, "-vn", "-c:a", "aac", "-b:a", "192k", voice.absolutePath)
        val pending = File(dir, "output/final.pending.mp4")
        stage("正在封装最终 MP4")
        muxFinalAudio(project, video, voice, pending)
        require(pending.length() > 1000) { "输出视频为空" }
        Files.move(pending.toPath(), store.output(project).toPath(), StandardCopyOption.REPLACE_EXISTING)
        project.duration = duration(store.output(project))
        renderStamp.writeText(clipSignature)
        openingInputsStamp.writeText(openingInputs)
    }
}

private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()
