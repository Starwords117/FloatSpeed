package com.starwords.floatingspeed.domain

data class SatelliteInfo(
    val visibleCount: Int?,
    val lastUpdateElapsedMs: Long,
) {

    fun display(nowMs: Long): String =
        if (visibleCount == null || nowMs - lastUpdateElapsedMs > STALE_MS) {
            DASH
        } else {
            visibleCount.toString()
        }

    companion object {
        const val STALE_MS = 10_000L
        const val DASH = "--"
    }
}
