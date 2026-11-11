package com.starwords.floatingspeed.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.starwords.floatingspeed.domain.SatelliteInfo
import com.starwords.floatingspeed.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SatelliteProvider {
    val info: StateFlow<SatelliteInfo>
    fun start()
    fun stop()
}

class GnssStatusEngine(context: Context) : SatelliteProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    private val executor = ContextCompat.getMainExecutor(context)

    private val _info = MutableStateFlow(SatelliteInfo(null, 0L))
    override val info: StateFlow<SatelliteInfo> = _info.asStateFlow()

    private var registered = false

    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            _info.value = SatelliteInfo(
                visibleCount = status.satelliteCount,
                lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (registered) return
        registered = try {
            locationManager.registerGnssStatusCallback(executor, callback)
        } catch (e: SecurityException) {
            DebugLog.d("gnss register denied: fine permission missing")
            false
        }
        if (!registered) {
            DebugLog.d("gnss status callback unavailable")
        }
    }

    override fun stop() {
        if (!registered) return
        registered = false
        try {
            locationManager.unregisterGnssStatusCallback(callback)
        } catch (e: SecurityException) {
            DebugLog.d("gnss unregister denied")
        }
    }
}
