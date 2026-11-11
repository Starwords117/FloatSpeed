package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {

    @Test
    fun `session starts at first frame`() {
        val acc = SessionStatsAccumulator()
        val stats = acc.onFrame(filteredSpeedMps = 5f, stepMeters = 0.0, frameElapsedMs = 5_000)
        assertTrue(stats.hasFix)
        assertEquals(0L, stats.elapsedMs)
    }

    @Test
    fun `elapsed includes stopped time`() {
        val acc = SessionStatsAccumulator()
        acc.onFrame(5f, 100.0, 5_000)
        val stats = acc.onFrame(0f, 0.0, 65_000)
        assertEquals(60_000L, stats.elapsedMs)
    }

    @Test
    fun `average speed equals distance over elapsed`() {
        val acc = SessionStatsAccumulator()
        acc.onFrame(10f, 1_000.0, 0)
        val stats = acc.onFrame(10f, 0.0, 60_000)
        assertEquals(60f, stats.avgSpeedKmh, 0.01f)
    }

    @Test
    fun `average speed is zero before any elapsed time`() {
        val acc = SessionStatsAccumulator()
        val stats = acc.onFrame(10f, 500.0, 1_000)
        assertEquals(0f, stats.avgSpeedKmh, 0.0001f)
    }

    @Test
    fun `max speed keeps the highest filtered value`() {
        val acc = SessionStatsAccumulator()
        acc.onFrame(20f, 0.0, 0)
        val stats = acc.onFrame(10f, 0.0, 1_000)
        assertEquals(72f, stats.maxSpeedKmh, 0.1f)
    }

    @Test
    fun `reset clears everything and restarts session clock`() {
        val acc = SessionStatsAccumulator()
        acc.onFrame(20f, 1_000.0, 0)
        acc.onFrame(15f, 500.0, 30_000)
        val reset = acc.reset()
        assertFalse(reset.hasFix)
        assertEquals(0f, reset.maxSpeedKmh, 0.0001f)
        assertEquals(0.0, reset.distanceM, 0.0001)
        assertEquals(0L, reset.elapsedMs)
        val restarted = acc.onFrame(5f, 0.0, 999_000)
        assertEquals(0L, restarted.elapsedMs)
        assertTrue(restarted.hasFix)
    }

    @Test
    fun `distance accumulates across frames`() {
        val acc = SessionStatsAccumulator()
        acc.onFrame(10f, 120.0, 0)
        val stats = acc.onFrame(10f, 80.0, 1_000)
        assertEquals(200.0, stats.distanceM, 0.0001)
    }
}
