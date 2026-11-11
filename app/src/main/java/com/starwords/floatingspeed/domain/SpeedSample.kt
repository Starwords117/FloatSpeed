package com.starwords.floatingspeed.domain

data class SpeedSample(
    val speedMps: Float?,
    val accuracyM: Float,
    val elapsedRealtimeMs: Long,
    val utcTimeMs: Long,
    val latitude: Double,
    val longitude: Double,
)
