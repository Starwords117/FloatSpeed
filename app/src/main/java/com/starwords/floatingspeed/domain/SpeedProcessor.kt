package com.starwords.floatingspeed.domain

data class ProcessedFrame(
    val speedKmh: Float,
    val rawSpeedKmh: Float,
    val accuracyM: Float,
    val signal: SignalState,
    val stats: SessionStats,
)

class SpeedProcessor(
    val filter: SpeedFilter = SpeedFilter(),
    val statsAccumulator: SessionStatsAccumulator = SessionStatsAccumulator(),
    val signalMachine: SignalStateMachine = SignalStateMachine(),
) {

    private var last: SpeedSample? = null

    val signalState: SignalState
        get() = signalMachine.state

    val stats: SessionStats
        get() = statsAccumulator.current()

    fun onSample(sample: SpeedSample): ProcessedFrame? {
        signalMachine.onFix(sample.accuracyM, sample.elapsedRealtimeMs)

        if (sample.accuracyM > MAX_SPEED_ACCURACY_M) return null

        val previous = last
        var stepMeters = 0.0
        var deltaSeconds = 0.0
        if (previous != null) {
            deltaSeconds = (sample.utcTimeMs - previous.utcTimeMs) / 1000.0
            if (deltaSeconds in MIN_DELTA_S..MAX_DELTA_S) {
                stepMeters = GeoMath.distanceMeters(
                    previous.latitude, previous.longitude,
                    sample.latitude, sample.longitude,
                )
            }
        }

        val rawMps: Float = when {
            sample.speedMps != null -> sample.speedMps
            previous != null && deltaSeconds in MIN_DELTA_S..MAX_DELTA_S ->
                (stepMeters / deltaSeconds).toFloat()
            else -> return null
        }

        val filteredMps = filter.push(rawMps)
        val frameStats = statsAccumulator.onFrame(filteredMps, stepMeters, sample.elapsedRealtimeMs)
        last = sample

        return ProcessedFrame(
            speedKmh = filteredMps * 3.6f,
            rawSpeedKmh = rawMps * 3.6f,
            accuracyM = sample.accuracyM,
            signal = signalMachine.state,
            stats = frameStats,
        )
    }

    fun onTick(nowElapsedMs: Long) {
        signalMachine.onTick(nowElapsedMs)
    }

    fun resetStats(): SessionStats = statsAccumulator.reset()

    fun onPermissionGranted() = signalMachine.onPermissionGranted()

    fun onPermissionLost() = signalMachine.onPermissionLost()

    companion object {
        const val MAX_SPEED_ACCURACY_M = 30f
        const val MIN_DELTA_S = 0.5
        const val MAX_DELTA_S = 5.0
    }
}
