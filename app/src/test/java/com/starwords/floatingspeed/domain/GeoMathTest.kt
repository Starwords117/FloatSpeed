package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GeoMathTest {

    @Test
    fun `one degree of latitude is about 111 km`() {
        val distance = GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("distance=$distance", abs(distance - 111_194.9) < 60.0)
    }

    @Test
    fun `same point is zero`() {
        assertEquals(0.0, GeoMath.distanceMeters(31.23, 121.47, 31.23, 121.47), 1e-6)
    }

    @Test
    fun `distance is symmetric`() {
        val a = GeoMath.distanceMeters(31.23, 121.47, 31.5, 121.9)
        val b = GeoMath.distanceMeters(31.5, 121.9, 31.23, 121.47)
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun `small offset is about 11 meters`() {
        val distance = GeoMath.distanceMeters(31.0, 121.0, 31.0001, 121.0)
        assertTrue("distance=$distance", abs(distance - 11.119) < 0.1)
    }
}
