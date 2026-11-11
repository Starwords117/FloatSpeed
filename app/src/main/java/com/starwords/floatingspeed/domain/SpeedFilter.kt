package com.starwords.floatingspeed.domain

class SpeedFilter(
    private val fastAlpha: Float = ALPHA_FAST,
    private val slowAlpha: Float = ALPHA_SLOW,
    private val gateMps: Float = GATE_MPS,
    private val zeroLockSamples: Int = ZERO_LOCK_SAMPLES,
) {

    private var outputMps: Float? = null
    private var belowGateCount = 0

    fun push(rawMps: Float): Float {
        val current = outputMps
        val belowGate = rawMps < gateMps
        belowGateCount = if (belowGate) belowGateCount + 1 else 0

        val filtered = if (current == null) {
            rawMps
        } else {
            val alpha = if (rawMps > current) fastAlpha else slowAlpha
            alpha * rawMps + (1 - alpha) * current
        }

        val result = when {
            belowGateCount >= zeroLockSamples -> 0f
            filtered < gateMps -> 0f
            else -> filtered
        }
        outputMps = result
        return result
    }

    fun reset() {
        outputMps = null
        belowGateCount = 0
    }

    companion object {
        const val ALPHA_FAST = 0.6f
        const val ALPHA_SLOW = 0.25f
        const val GATE_MPS = 0.5f
        const val ZERO_LOCK_SAMPLES = 3
    }
}
