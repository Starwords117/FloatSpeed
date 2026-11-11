package com.starwords.floatingspeed.console

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.starwords.floatingspeed.App
import com.starwords.floatingspeed.data.SettingsRepository
import com.starwords.floatingspeed.domain.SpeedUnit
import com.starwords.floatingspeed.overlay.OverlayPermission
import com.starwords.floatingspeed.overlay.OverlayService
import com.starwords.floatingspeed.session.SessionManager
import com.starwords.floatingspeed.session.SessionSnapshot
import com.starwords.floatingspeed.util.DebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConsoleViewModel(
    private val app: App,
    private val session: SessionManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ConsoleUiState())
    val ui: StateFlow<ConsoleUiState> = _ui.asStateFlow()

    private var snap = SessionSnapshot.EMPTY
    private var keepScreenOn = true
    private var floatingEnabled = false
    private var alphaLevel = SettingsRepository.ALPHA_MEDIUM
    private var overlayGranted = OverlayPermission.canDrawOverlays(app)
    private var overlayAttempted = false
    private var overlayRevoked = false
    private var pendingOverlayEnable = false

    init {
        viewModelScope.launch {
            session.state.collect {
                snap = it
                publish()
            }
        }
        viewModelScope.launch {
            settings.keepScreenOn.collect {
                keepScreenOn = it
                publish()
            }
        }
        viewModelScope.launch {
            settings.floatingEnabled.collect {
                val previous = floatingEnabled
                floatingEnabled = it
                if (it && !previous && OverlayPermission.canDrawOverlays(app)) {
                    sendServiceAction(OverlayService.ACTION_SHOW)
                }
                publish()
            }
        }
        viewModelScope.launch {
            settings.overlayAlpha.collect {
                alphaLevel = it
                publish()
            }
        }
    }

    fun onEvent(event: ConsoleEvent) {
        when (event) {
            ConsoleEvent.ToggleUnit -> viewModelScope.launch {
                settings.setUnit(if (snap.unit == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH)
            }
            ConsoleEvent.ToggleKeepScreenOn -> viewModelScope.launch {
                settings.setKeepScreenOn(!keepScreenOn)
            }
            ConsoleEvent.ToggleFloating -> toggleFloating()
            is ConsoleEvent.SetAlpha -> viewModelScope.launch {
                settings.setOverlayAlpha(event.level)
            }
            ConsoleEvent.PermissionRecheck -> session.refresh()
            ConsoleEvent.Resume -> onResume()
        }
    }

    private fun toggleFloating() {
        if (floatingEnabled) {
            viewModelScope.launch {
                settings.setFloatingEnabled(false)
                sendServiceAction(OverlayService.ACTION_STOP)
            }
            return
        }
        overlayAttempted = true
        if (!OverlayPermission.canDrawOverlays(app)) {
            overlayGranted = false
            pendingOverlayEnable = true
            publish()
            runCatching {
                app.startActivity(OverlayPermission.intent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        overlayGranted = true
        viewModelScope.launch {
            settings.setFloatingEnabled(true)
            sendServiceAction(OverlayService.ACTION_SHOW)
        }
    }

    private fun onResume() {
        val granted = OverlayPermission.canDrawOverlays(app)
        overlayGranted = granted
        if (granted) {
            overlayRevoked = false
            overlayAttempted = false
            if (pendingOverlayEnable) {
                viewModelScope.launch {
                    delay(OVERLAY_RECHECK_DELAY_MS)
                    val recheck = OverlayPermission.canDrawOverlays(app)
                    pendingOverlayEnable = false
                    if (recheck) {
                        settings.setFloatingEnabled(true)
                        sendServiceAction(OverlayService.ACTION_SHOW)
                    } else {
                        overlayAttempted = true
                    }
                    publish()
                }
            }
        } else if (floatingEnabled) {
            overlayRevoked = true
            overlayAttempted = true
            viewModelScope.launch {
                settings.setFloatingEnabled(false)
                sendServiceAction(OverlayService.ACTION_STOP)
            }
        }
        session.refresh()
        publish()
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(app, OverlayService::class.java).setAction(action)
        try {
            if (action == OverlayService.ACTION_SHOW) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (t: Throwable) {
            DebugLog.d("service action $action failed: ${t.javaClass.simpleName}")
        }
    }

    private fun publish() {
        val backgroundGranted = app.checkSelfPermission(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val bannerSpec = when {
            !overlayGranted && overlayRevoked ->
                BannerType.OVERLAY_REVOKED to "悬浮窗权限已失效，点此恢复"
            !overlayGranted && overlayAttempted ->
                BannerType.OVERLAY_REVOKED to "悬浮窗权限未开启，点此去设置"
            !snap.locationEnabled ->
                BannerType.LOCATION_DISABLED to "系统定位未开启"
            snap.permission == PermissionState.CoarseOnly ->
                BannerType.PRECISE_LOCATION to "请开启精确位置"
            floatingEnabled && !backgroundGranted ->
                BannerType.BACKGROUND_LOCATION to "后台定位需始终允许，点此设置"
            snap.stalled && floatingEnabled ->
                BannerType.LOCATION_STALLED to "后台定位中断，点此检查"
            snap.engineError != null ->
                BannerType.ENGINE_ERROR to snap.engineError!!
            else -> null
        }
        _ui.value = ConsoleUiState(
            permission = snap.permission,
            signal = snap.signal,
            speedText = snap.speedText,
            speedDimmed = snap.speedDimmed,
            accuracyPoor = snap.accuracyPoor,
            unit = snap.unit,
            satelliteText = snap.satelliteText,
            locationEnabled = snap.locationEnabled,
            keepScreenOn = keepScreenOn,
            floatingEnabled = floatingEnabled,
            overlayPermission = overlayGranted,
            alphaLevel = alphaLevel,
            banner = bannerSpec?.first,
            bannerMessage = bannerSpec?.second,
        )
    }

    companion object {
        private const val OVERLAY_RECHECK_DELAY_MS = 1000L
    }
}

class ConsoleViewModelFactory(private val app: App) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConsoleViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ConsoleViewModel(
            app = app,
            session = app.session,
            settings = app.settings,
        ) as T
    }
}
