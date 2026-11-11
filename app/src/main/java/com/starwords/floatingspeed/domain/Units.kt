package com.starwords.floatingspeed.domain

import java.util.Locale
import kotlin.math.roundToInt

enum class SpeedUnit {
    KMH,
    MPH,
}

object Units {

    private const val KMH_PER_MPH = 1.609344f
    private const val MAX_DISPLAY_VALUE = 999

    fun formatSpeed(kmh: Float, unit: SpeedUnit): String {
        val converted = when (unit) {
            SpeedUnit.KMH -> kmh
            SpeedUnit.MPH -> kmh / KMH_PER_MPH
        }
        val rounded = converted.roundToInt()
        return if (rounded > MAX_DISPLAY_VALUE) {
            "999+"
        } else {
            rounded.coerceAtLeast(0).toString()
        }
    }

    fun unitLabel(unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KMH -> "km/h"
        SpeedUnit.MPH -> "mph"
    }

    fun spokenUnit(unit: SpeedUnit): String = when (unit) {
        SpeedUnit.KMH -> "公里每小时"
        SpeedUnit.MPH -> "英里每小时"
    }

    fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }
}
