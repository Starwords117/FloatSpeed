package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedProcessorTest {

    private fun sample(
        speedMps: Float? = null,
        accuracyM: Float = 10f,
        elapsedMs: Long,
        utcMs: Long,
        lat: Double = 31.2304,
        lon: Double = 121.4737,
    ) = SpeedSample(speedMps, accuracyM, elapsedMs, utcMs, lat, lon)

    @Test
    fun `first sample without speed field is rejected but updates signal`() {
        val processor = SpeedProcessor()
        val frame = processor.onSample(sample(speedMps = null, elapsedMs = 1_000, utcMs = 0))
        assertNull(frame)
        assertEquals(SignalState.Good(10f), processor.signalState)
        assertEquals(false, processor.stats.hasFix)
    }

    @Test
    fun `inaccurate sample is rejected but still updates signal state`() {
        val processor = SpeedProcessor()
        val first = processor.onSample(sample(speedMps = 10f, elapsedMs = 1_000, utcMs = 0))
        assertNotNull(first)
        assertEquals(SignalState.Good(10f), processor.signalState)

        val second = processor.onSample(
            sample(speedMps = 12f, accuracyM = 35f, elapsedMs = 2_000, utcMs = 1_000),
        )
        assertNull(second)
        assertEquals(SignalState.Fair(35f), processor.signalState)
    }

    @Test
    fun `falls back to distance over time when speed field missing`() {
        val processor = SpeedProcessor()
        processor.onSample(
            sample(speedMps = 5f, elapsedMs = 1_000, utcMs = 0, lat = 31.0, lon = 121.0),
        )
        val frame = processor.onSample(
            sample(speedMps = null, elapsedMs = 3_000, utcMs = 2_000, lat = 31.0001, lon = 121.0),
        )
        assertNotNull(frame)
        assertTrue("raw=${frame!!.rawSpeedKmh}", abs(frame.rawSpeedKmh - 20.0f) < 1.0f)
        assertEquals(11.119, frame.stats.distanceM, 0.15)
    }

    @Test
    fun `large time gap skips distance accumulation`() {
        val processor = SpeedProcessor()
        processor.onSample(
            sample(speedMps = 0f, elapsedMs = 1_000, utcMs = 0, lat = 31.0, lon = 121.0),
        )
        val frame = processor.onSample(
            sample(speedMps = null, elapsedMs = 12_000, utcMs = 11_000, lat = 31.001, lon = 121.0),
        )
        assertNull(frame)
        assertEquals(0.0, processor.stats.distanceM, 0.0001)
        assertEquals(0L, processor.stats.elapsedMs)
    }

    @Test
    fun `stationary samples keep displaying zero`() {
        val processor = SpeedProcessor()
        repeat(10) { i ->
            val frame = processor.onSample(
                sample(speedMps = 0.1f, elapsedMs = 1_000L + i * 1_000L, utcMs = i * 1_000L),
            )
            assertNotNull(frame)
            assertEquals(0f, frame!!.speedKmh, 0.0001f)
        }
        assertEquals(0f, processor.stats.maxSpeedKmh, 0.0001f)
    }

    @Test
    fun `max speed tracks highest filtered speed`() {
        val processor = SpeedProcessor()
        processor.onSample(sample(speedMps = 20f, elapsedMs = 1_000, utcMs = 0))
        processor.onSample(sample(speedMps = 10f, elapsedMs = 2_000, utcMs = 1_000))
        assertEquals(72f, processor.stats.maxSpeedKmh, 0.1f)
    }

    @Test
    fun `tick drives signal into lost after three seconds`() {
        val processor = SpeedProcessor()
        processor.onSample(sample(speedMps = 10f, elapsedMs = 1_000, utcMs = 0))
        processor.onTick(4_000)
        assertEquals(SignalState.Lost, processor.signalState)
    }

    @Test
    fun `reset clears stats and keeps signal state`() {
        val processor = SpeedProcessor()
        processor.onSample(sample(speedMps = 20f, elapsedMs = 1_000, utcMs = 0))
        val reset = processor.resetStats()
        assertEquals(false, reset.hasFix)
        assertEquals(0L, reset.elapsedMs)
        assertEquals(SignalState.Good(10f), processor.signalState)
    }
}
