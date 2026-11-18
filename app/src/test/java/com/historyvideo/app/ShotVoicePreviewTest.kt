package com.historyvideo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShotVoicePreviewTest {
    @Test
    fun readyPreviewIsTheOriginalVoiceFile() {
        val voice = tempVoice("001.mp3", ShotVoicePreview.MIN_BYTES + 1)
        val decision = ShotVoicePreview.decide(4, "done", voice)
        assertTrue(decision is ShotVoicePreview.Decision.Ready)
        val ready = decision as ShotVoicePreview.Decision.Ready
        assertSame(voice, ready.file)
        assertEquals(voice.absolutePath, ready.file.absolutePath)
        assertEquals("正在试听第 4 段配音", ready.message)
        assertFalse(ready.message.contains("音效"))
        assertFalse(ready.message.contains("混合"))
    }

    @Test
    fun missingVoicePromptsAndDoesNotOfferAFile() {
        val pending = tempVoice("pending.mp3", 2000)
        val empty = tempVoice("empty.mp3", 0)
        val tooSmall = tempVoice("small.mp3", ShotVoicePreview.MIN_BYTES)
        val absent = File(pending.parentFile, "missing.mp3")
        val cases = listOf(
            ShotVoicePreview.decide(2, "pending", pending),
            ShotVoicePreview.decide(2, "failed", pending),
            ShotVoicePreview.decide(2, "done", empty),
            ShotVoicePreview.decide(2, "done", tooSmall),
            ShotVoicePreview.decide(2, "done", absent)
        )
        cases.forEach { decision ->
            assertTrue(decision is ShotVoicePreview.Decision.Missing)
            val message = (decision as ShotVoicePreview.Decision.Missing).message
            assertEquals("第 2 段配音尚未生成，请先完成配音", message)
            assertFalse(message.contains("混合"))
            assertFalse(message.contains("正在生成"))
        }
    }

    @Test
    fun repeatStopAndShotSwitchInvalidateTheOlderPreview() {
        val gate = VoicePreviewGate()
        var key: String? = null
        fun playShot(shotId: Int): Int {
            val token = gate.issue()
            key = ShotVoicePreview.shotKey("project", shotId)
            return token
        }
        fun playAll(): Int {
            val token = gate.issue()
            key = ShotVoicePreview.allKey("project")
            return token
        }
        fun stop() {
            gate.issue()
            key = null
        }

        val fullMix = playAll()
        val first = playShot(1)
        assertFalse(gate.isCurrent(fullMix))
        assertTrue(gate.isCurrent(first))
        assertTrue(ShotVoicePreview.keepPlayback("shots", "project", 1, key))
        assertTrue(ShotVoicePreview.keepPlayback("shot", "project", 1, key))

        val repeated = playShot(1)
        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(repeated))
        assertEquals(ShotVoicePreview.shotKey("project", 1), key)

        assertFalse(ShotVoicePreview.keepPlayback("shot", "project", 2, key))
        stop()
        assertFalse(gate.isCurrent(repeated))
        assertFalse(ShotVoicePreview.keepPlayback("shot", "project", 1, key))
        assertFalse(ShotVoicePreview.keepPlayback("home", "project", 1, ShotVoicePreview.shotKey("project", 1)))
        assertFalse(ShotVoicePreview.keepPlayback("shots", "other", 1, ShotVoicePreview.shotKey("project", 1)))
        assertFalse(gate.isCurrent(0))
    }

    @Test
    fun openingTheSameShotKeepsPlaybackAndOtherScreensDoNot() {
        val key = ShotVoicePreview.shotKey("project", 3)
        assertTrue(ShotVoicePreview.keepPlayback("shot", "project", 3, key))
        assertTrue(ShotVoicePreview.keepPlayback("shots", "project", 8, key))
        assertFalse(ShotVoicePreview.keepPlayback("shot", "project", 8, key))
        assertFalse(ShotVoicePreview.keepPlayback("effects", "project", 3, key))
        assertTrue(ShotVoicePreview.keepPlayback("shots", "project", 3, null))
        assertTrue(ShotVoicePreview.keepPlayback("shots", "project", 3, ShotVoicePreview.allKey("project")))
        assertFalse(ShotVoicePreview.keepPlayback("shot", "project", 3, ShotVoicePreview.allKey("project")))
    }

    @Test
    fun storyboardPreviewNoLongerWaitsForMixWhileFinalMixStays() {
        val main = source("app/src/main/java/com/historyvideo/app/MainActivity.kt")
        val renderer = source("app/src/main/java/com/historyvideo/app/VideoRenderer.kt")
        val play = functionBody(main, "private fun play(project: VideoProject, shot: Shot)")
        val playShot = functionBody(main, "fun playShot(project: VideoProject, shot: Shot)")
        val playVoice = functionBody(main, "fun playVoice(project: VideoProject)")
        val singleShotMix = functionBody(renderer, "fun previewShotAudio(")
        val finalMix = functionBody(renderer, "private fun muxFinalAudio(")
        val fullPreview = functionBody(renderer, "fun concatAudio(")

        assertTrue(play.contains("ShotVoicePreview.decide"))
        assertTrue(play.contains("store.audio"))
        assertFalse(play.contains("previewShotAudio"))
        assertFalse(play.contains("concatAudio"))
        assertTrue(playShot.contains("stopVoicePreview()"))
        assertTrue(playShot.contains("play(project, shot)"))
        assertFalse(playShot.contains("previewShotAudio"))
        assertFalse(playShot.contains("Dispatchers.IO"))
        assertFalse(main.contains("previewShotAudio"))
        assertFalse(main.contains("正在混合本镜配音和音效"))
        assertFalse(main.contains("试听本镜会同步播放已选音效"))
        assertTrue(main.contains("onPlay = ::playShot"))
        assertTrue(main.contains("试听只播放本镜已生成的原配音"))
        assertTrue(main.contains("本镜音效已保存，重新渲染后生效"))

        assertTrue(playVoice.contains("buildVoicePreview"))
        assertTrue(playVoice.contains("voicePreviewGate.isCurrent(token)"))
        assertTrue(playVoice.contains("isActive"))
        assertTrue(main.contains("import kotlinx.coroutines.isActive"))
        assertTrue(main.contains("试听全部 · 配音、BGM和镜头音效"))
        assertTrue(functionBody(main, "private fun buildVoicePreview").contains("concatAudio"))

        assertTrue(singleShotMix.contains("shotAudioEvents"))
        assertTrue(singleShotMix.contains("audioMixPlan"))
        assertTrue(singleShotMix.contains("duration("))
        assertTrue(singleShotMix.contains("混合本镜配音和音效"))
        assertTrue(fullPreview.contains("mixVoicePreview"))
        assertTrue(finalMix.contains("混合配音、BGM和镜头音效"))
    }

    private fun tempVoice(name: String, bytes: Long): File {
        val file = File(System.getProperty("java.io.tmpdir"), "shot-voice-preview-$name")
        file.writeBytes(ByteArray(bytes.toInt()))
        file.deleteOnExit()
        return file
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        val tried = mutableListOf<String>()
        while (dir != null) {
            val candidate = File(dir, relative)
            tried += candidate.path
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Missing $relative from ${System.getProperty("user.dir")}; tried $tried")
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "Missing $signature" }
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Unclosed $signature")
    }
}
