package com.starwords.floatingspeed.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.starwords.floatingspeed.domain.SpeedSample
import com.starwords.floatingspeed.util.DebugLog

class GpsLocationEngine(context: Context) : LocationEngine {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(onSample: (SpeedSample) -> Unit, onError: (String) -> Unit) {
        stop()
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val sample = location.toSpeedSample()
                DebugLog.d(
                    "fix acc=${sample.accuracyM} speed=${sample.speedMps} " +
                        "dt=${sample.utcTimeMs}",
                )
                try {
                    onSample(sample)
                } catch (t: Throwable) {
                    DebugLog.d("sample handler error: ${t.javaClass.simpleName}")
                }
            }
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper(),
            )
            listener = locationListener
        } catch (e: IllegalArgumentException) {
            DebugLog.d("gps provider unavailable")
            onError("定位不可用")
        } catch (e: SecurityException) {
            DebugLog.d("location permission missing")
            onError("定位权限被拒绝")
        }
    }

    override fun stop() {
        val current = listener ?: return
        listener = null
        try {
            locationManager.removeUpdates(current)
        } catch (e: SecurityException) {
            DebugLog.d("remove updates denied")
        }
    }

    private fun Location.toSpeedSample() = SpeedSample(
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        utcTimeMs = time,
        latitude = latitude,
        longitude = longitude,
    )

    companion object {
        private const val MIN_TIME_MS = 1000L
        private const val MIN_DISTANCE_M = 0f
    }
}
