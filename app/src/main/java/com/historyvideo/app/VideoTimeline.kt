package com.historyvideo.app

import kotlin.math.roundToInt

/** Quantize the narration's cumulative boundaries so rounding cannot drift from shot to shot. */
internal object VideoTimeline {
    const val FPS = 30

    fun frameCounts(durations: List<Double>): List<Int> {
        var elapsed = 0.0
        var previousBoundary = 0
        return durations.map { duration ->
            require(duration.isFinite() && duration > 0.0) { "配音时长无效" }
            elapsed += duration
            val boundary = (elapsed * FPS).roundToInt().coerceAtLeast(previousBoundary + 1)
            val count = boundary - previousBoundary
            previousBoundary = boundary
            count
        }
    }
}
