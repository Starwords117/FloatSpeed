package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `formats kmh as integer`() {
        assertEquals("61", Units.formatSpeed(61.4f, SpeedUnit.KMH))
        assertEquals("0", Units.formatSpeed(0f, SpeedUnit.KMH))
    }

    @Test
    fun `converts to mph`() {
        assertEquals("100", Units.formatSpeed(160.9344f, SpeedUnit.MPH))
        assertEquals("62", Units.formatSpeed(100f, SpeedUnit.MPH))
    }

    @Test
    fun `caps at 999 plus`() {
        assertEquals("999", Units.formatSpeed(999.4f, SpeedUnit.KMH))
        assertEquals("999+", Units.formatSpeed(1000.6f, SpeedUnit.KMH))
    }

    @Test
    fun `negative rounds to zero`() {
        assertEquals("0", Units.formatSpeed(-3f, SpeedUnit.KMH))
    }

    @Test
    fun `unit labels`() {
        assertEquals("km/h", Units.unitLabel(SpeedUnit.KMH))
        assertEquals("mph", Units.unitLabel(SpeedUnit.MPH))
    }

    @Test
    fun `spoken units for accessibility`() {
        assertEquals("公里每小时", Units.spokenUnit(SpeedUnit.KMH))
        assertEquals("英里每小时", Units.spokenUnit(SpeedUnit.MPH))
    }

    @Test
    fun `duration under one hour is mm colon ss`() {
        assertEquals("00:00", Units.formatDuration(0))
        assertEquals("05:32", Units.formatDuration(332_000))
        assertEquals("59:59", Units.formatDuration(3_599_000))
    }

    @Test
    fun `duration over one hour is h colon mm colon ss`() {
        assertEquals("1:02:05", Units.formatDuration(3_725_000))
        assertEquals("10:00:00", Units.formatDuration(36_000_000))
    }
}
