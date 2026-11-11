package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedFilterTest {

    @Test
    fun `stationary noise stays at zero`() {
        val filter = SpeedFilter()
        val noise = floatArrayOf(0.2f, 0.45f, 0.1f, 0.49f, 0.3f, 0.0f, 0.44f, 0.2f)
        noise.forEach { raw ->
            assertEquals(0f, filter.push(raw), 0.0001f)
        }
    }

    @Test
    fun `step input reaches 80 percent in 2 samples and 90 percent in 3`() {
        val filter = SpeedFilter()
        val target = 16.667f
        filter.push(target)
        val after2 = filter.push(target)
        val after3 = filter.push(target)
        assertTrue("after2=${after2 / target}", after2 >= 0.8f * target)
        assertTrue("after3=${after3 / target}", after3 >= 0.9f * target)
    }

    @Test
    fun `oscillating input is smoothed within band`() {
        val filter = SpeedFilter()
        repeat(10) { filter.push(16.667f) }
        val outputs = (0 until 6).map { i ->
            filter.push(if (i % 2 == 0) 19f else 14f)
        }
        assertTrue(outputs.all { it in 14f..19f })
        assertTrue("spread=${outputs.max() - outputs.min()}", outputs.max() - outputs.min() < 3f)
    }

    @Test
    fun `three consecutive below-gate samples snap to zero`() {
        val filter = SpeedFilter()
        repeat(10) { filter.push(16.667f) }
        val first = filter.push(0.1f)
        val second = filter.push(0.1f)
        val third = filter.push(0.1f)
        assertTrue(first > 10f)
        assertTrue(second > 5f)
        assertEquals(0f, third, 0.0001f)
    }

    @Test
    fun `single below-gate sample does not drop established speed`() {
        val filter = SpeedFilter()
        repeat(10) { filter.push(16.667f) }
        val afterOneDip = filter.push(0.1f)
        assertTrue(afterOneDip > 10f)
    }

    @Test
    fun `reset reinitializes filter`() {
        val filter = SpeedFilter()
        repeat(10) { filter.push(16.667f) }
        filter.reset()
        val first = filter.push(5f)
        assertEquals(5f, first, 0.0001f)
    }
}
