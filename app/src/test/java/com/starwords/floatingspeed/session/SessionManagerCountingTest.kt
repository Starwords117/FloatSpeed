package com.starwords.floatingspeed.session

import com.starwords.floatingspeed.console.PermissionState
import com.starwords.floatingspeed.data.SettingsSource
import com.starwords.floatingspeed.domain.SatelliteInfo
import com.starwords.floatingspeed.domain.SpeedSample
import com.starwords.floatingspeed.domain.SpeedUnit
import com.starwords.floatingspeed.location.LocationEngine
import com.starwords.floatingspeed.location.LocationStatusProvider
import com.starwords.floatingspeed.location.SatelliteProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerCountingTest {

    private class FakeLocationEngine : LocationEngine {
        var startCount = 0
        var stopCount = 0
        override fun start(onSample: (SpeedSample) -> Unit, onError: (String) -> Unit) {
            startCount++
        }
        override fun stop() {
            stopCount++
        }
    }

    private class FakeSatelliteEngine : SatelliteProvider {
        private val _info = MutableStateFlow(SatelliteInfo(null, 0L))
        override val info: StateFlow<SatelliteInfo> = _info
        var startCount = 0
        var stopCount = 0
        override fun start() {
            startCount++
        }
        override fun stop() {
            stopCount++
        }
    }

    private class FakeStatusProvider : LocationStatusProvider {
        var permission: PermissionState = PermissionState.Granted
        var locationEnabled = true
        override fun permissionState(): PermissionState = permission
        override fun isLocationEnabled(): Boolean = locationEnabled
    }

    private class FakeSettings : SettingsSource {
        override val unit = MutableStateFlow(SpeedUnit.KMH)
    }

    private class Harness {
        val location = FakeLocationEngine()
        val satellite = FakeSatelliteEngine()
        val status = FakeStatusProvider()
        val settings = FakeSettings()
        val session = SessionManager(
            locationEngine = location,
            satelliteEngine = satellite,
            statusProvider = status,
            unitSource = settings,
        )
    }

    @Test
    fun retainTwice_startsEnginesOnlyOnce() {
        val h = Harness()
        assertTrue(h.session.retain())
        assertFalse(h.session.retain())
        assertEquals(2, h.session.holderCount())
        assertEquals(1, h.location.startCount)
        assertEquals(1, h.satellite.startCount)
        assertTrue(h.session.enginesActive())
        h.session.release()
        h.session.release()
    }

    @Test
    fun releaseToZero_stopsEnginesOnce() {
        val h = Harness()
        h.session.retain()
        h.session.retain()
        assertFalse(h.session.release())
        assertEquals(1, h.session.holderCount())
        assertEquals(0, h.location.stopCount)
        assertTrue(h.session.enginesActive())
        assertTrue(h.session.release())
        assertEquals(0, h.session.holderCount())
        assertEquals(1, h.location.stopCount)
        assertEquals(1, h.satellite.stopCount)
        assertFalse(h.session.enginesActive())
    }

    @Test
    fun releaseAtZero_isIdempotent() {
        val h = Harness()
        assertFalse(h.session.release())
        assertEquals(0, h.session.holderCount())
        h.session.retain()
        h.session.release()
        assertFalse(h.session.release())
        assertEquals(0, h.session.holderCount())
        assertEquals(1, h.location.stopCount)
    }

    @Test
    fun missingPermission_doesNotStartEngines() {
        val h = Harness()
        h.status.permission = PermissionState.NotGranted
        assertTrue(h.session.retain())
        assertEquals(0, h.location.startCount)
        assertEquals(0, h.satellite.startCount)
        assertFalse(h.session.enginesActive())
        assertEquals(PermissionState.NotGranted, h.session.state.value.permission)
        assertTrue(h.session.release())
        assertEquals(0, h.location.stopCount)
    }

    @Test
    fun reacquireAfterRelease_restartsEngines() {
        val h = Harness()
        h.session.retain()
        h.session.release()
        assertTrue(h.session.retain())
        assertEquals(2, h.location.startCount)
        assertEquals(2, h.satellite.startCount)
        assertTrue(h.session.enginesActive())
        h.session.release()
    }

    @Test
    fun coarseOnlyPermission_startsLocationWithoutSatellite() {
        val h = Harness()
        h.status.permission = PermissionState.CoarseOnly
        h.session.retain()
        assertEquals(1, h.location.startCount)
        assertEquals(0, h.satellite.startCount)
        h.session.release()
    }
}
