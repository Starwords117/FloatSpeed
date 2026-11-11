package com.starwords.floatingspeed.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.starwords.floatingspeed.console.PermissionState

interface LocationStatusProvider {
    fun permissionState(): PermissionState
    fun isLocationEnabled(): Boolean
}

class LocationServicesChecker(private val context: Context) : LocationStatusProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    override fun permissionState(): PermissionState {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return when {
            fine -> PermissionState.Granted
            coarse -> PermissionState.CoarseOnly
            else -> PermissionState.NotGranted
        }
    }

    override fun isLocationEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
