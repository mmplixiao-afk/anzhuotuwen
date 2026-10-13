package com.historyvideo.app

import kotlin.math.min

/** A shared start time for the opening title and the first cutout character. */
internal object OpeningTiming {
    const val DEFAULT_DELAY = 0.5f
    const val MAX_DELAY = 5f

    fun normalizeDelay(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, MAX_DELAY) else DEFAULT_DELAY

    fun start(delaySeconds: Float, shotDuration: Double): Double {
        val duration = if (shotDuration.isFinite()) shotDuration.coerceAtLeast(0.0) else 0.0
        // Reserve time for the title to finish its entry even on a very short first shot.
        val entry = min(0.42, duration * 0.55)
        return min(normalizeDelay(delaySeconds).toDouble(), (duration - entry).coerceAtLeast(0.0))
    }
}
