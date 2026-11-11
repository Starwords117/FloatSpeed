package com.starwords.floatingspeed.domain

data class SessionStats(
    val hasFix: Boolean = false,
    val maxSpeedKmh: Float = 0f,
    val distanceM: Double = 0.0,
    val elapsedMs: Long = 0,
) {
    val avgSpeedKmh: Float
        get() {
            if (elapsedMs <= 0) return 0f
            return ((distanceM / 1000.0) / (elapsedMs / 3_600_000.0)).toFloat()
        }
}

class SessionStatsAccumulator {

    private var startElapsedMs: Long? = null
    private var stats = SessionStats()

    fun onFrame(filteredSpeedMps: Float, stepMeters: Double, frameElapsedMs: Long): SessionStats {
        val start = startElapsedMs
        if (start == null) {
            startElapsedMs = frameElapsedMs
        }
        val elapsed = frameElapsedMs - (start ?: frameElapsedMs)
        val candidateMax = filteredSpeedMps * 3.6f
        stats = SessionStats(
            hasFix = true,
            maxSpeedKmh = maxOf(stats.maxSpeedKmh, candidateMax),
            distanceM = stats.distanceM + stepMeters,
            elapsedMs = elapsed,
        )
        return stats
    }

    fun current(): SessionStats = stats

    fun reset(): SessionStats {
        startElapsedMs = null
        stats = SessionStats()
        return stats
    }
}
