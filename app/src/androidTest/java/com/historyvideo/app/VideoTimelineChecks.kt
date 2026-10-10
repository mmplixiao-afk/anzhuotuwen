package com.historyvideo.app

import kotlin.math.abs
import kotlin.math.ceil

internal object VideoTimelineChecks {
    fun run() {
        val durations = List(75) { 4.01 }
        val counts = VideoTimeline.frameCounts(durations)
        val oldFrames = durations.sumOf { ceil(it * VideoTimeline.FPS).toInt() }
        val audioSeconds = durations.sum()
        check(oldFrames / VideoTimeline.FPS.toDouble() - audioSeconds > 1.7)
        check(abs(counts.sum() / VideoTimeline.FPS.toDouble() - audioSeconds) <= 0.5 / VideoTimeline.FPS + 1e-9)
        check(counts == VideoTimeline.frameCounts(durations))
        check(VideoTimeline.frameCounts(emptyList()).isEmpty())
        check(VideoTimeline.frameCounts(listOf(1.0, 2.0, 3.0)) == listOf(30, 60, 90))

        // Check every boundary, including a long project with variable short shots.
        val varied = List(240) { 0.4 + ((it * 17) % 131) / 100.0 }
        var audioElapsed = 0.0
        var videoFrames = 0
        VideoTimeline.frameCounts(varied).forEachIndexed { index, frames ->
            check(frames > 0)
            audioElapsed += varied[index]
            videoFrames += frames
            check(abs(videoFrames / VideoTimeline.FPS.toDouble() - audioElapsed) <= 0.5 / VideoTimeline.FPS + 1e-9)
        }
        // A valid sub-frame shot still needs one video frame; invalid media must fail clearly.
        check(VideoTimeline.frameCounts(listOf(0.01)) == listOf(1))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid ->
            check(runCatching { VideoTimeline.frameCounts(listOf(invalid)) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
