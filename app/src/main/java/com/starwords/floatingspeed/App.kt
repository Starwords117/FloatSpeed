package com.starwords.floatingspeed

import android.app.Application
import com.starwords.floatingspeed.data.SettingsRepository
import com.starwords.floatingspeed.location.GnssStatusEngine
import com.starwords.floatingspeed.location.GpsLocationEngine
import com.starwords.floatingspeed.location.LocationServicesChecker
import com.starwords.floatingspeed.session.SessionManager

class App : Application() {

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val session: SessionManager by lazy {
        SessionManager(
            locationEngine = GpsLocationEngine(this),
            satelliteEngine = GnssStatusEngine(this),
            statusProvider = LocationServicesChecker(this),
            unitSource = settings,
        )
    }
}
