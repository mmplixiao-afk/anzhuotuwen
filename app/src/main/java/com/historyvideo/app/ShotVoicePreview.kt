package com.historyvideo.app

import java.io.File

/**
 * Per-shot storyboard preview. The decision only looks at the voice file that
 * was already generated for this shot. It does not probe durations, resolve
 * sound effects, mix, or start TTS.
 */
object ShotVoicePreview {
    const val MIN_BYTES = 1000L

    sealed class Decision {
        data class Ready(val file: File, val message: String) : Decision()
        data class Missing(val message: String) : Decision()
    }

    fun decide(shotNumber: Int, audioStatus: String, voiceFile: File): Decision {
        val ready = audioStatus == "done" && voiceFile.isFile && voiceFile.length() > MIN_BYTES
        if (!ready) return Decision.Missing("第 $shotNumber 段配音尚未生成，请先完成配音")
        return Decision.Ready(voiceFile, "正在试听第 $shotNumber 段配音")
    }

    fun shotKey(projectId: String, shotId: Int) = "shot:$projectId:$shotId"

    fun allKey(projectId: String) = "all:$projectId"

    /**
     * Playback survives staying on the storyboard list and opening the same shot.
     * Switching shots, opening another screen, or a full-mix preview moving into
     * a single shot stops it.
     */
    fun keepPlayback(screen: String, projectId: String, shotId: Int, previewKey: String?): Boolean {
        return when (screen) {
            "shots" -> previewKey == null || previewKey == allKey(projectId) ||
                previewKey.startsWith("shot:$projectId:")
            "shot" -> previewKey == shotKey(projectId, shotId)
            else -> false
        }
    }
}

/** A newer click, stop, or shot switch invalidates preview work already in flight. */
class VoicePreviewGate {
    private var generation = 0

    fun issue(): Int = ++generation

    fun isCurrent(token: Int): Boolean = token != 0 && token == generation
}
