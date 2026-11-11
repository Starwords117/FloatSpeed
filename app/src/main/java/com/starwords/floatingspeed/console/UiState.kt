package com.starwords.floatingspeed.console

import com.starwords.floatingspeed.domain.SignalState
import com.starwords.floatingspeed.domain.SpeedUnit

sealed interface PermissionState {
    data object NotGranted : PermissionState
    data object CoarseOnly : PermissionState
    data object Granted : PermissionState
}

enum class BannerType {
    OVERLAY_REVOKED,
    LOCATION_DISABLED,
    PRECISE_LOCATION,
    BACKGROUND_LOCATION,
    LOCATION_STALLED,
    ENGINE_ERROR,
}

data class ConsoleUiState(
    val permission: PermissionState = PermissionState.NotGranted,
    val signal: SignalState = SignalState.Searching,
    val speedText: String = "--",
    val speedDimmed: Boolean = true,
    val accuracyPoor: Boolean = false,
    val unit: SpeedUnit = SpeedUnit.KMH,
    val satelliteText: String = "--",
    val locationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val floatingEnabled: Boolean = false,
    val overlayPermission: Boolean = true,
    val alphaLevel: Int = 1,
    val banner: BannerType? = null,
    val bannerMessage: String? = null,
)

sealed interface ConsoleEvent {
    data object ToggleUnit : ConsoleEvent
    data object ToggleKeepScreenOn : ConsoleEvent
    data object ToggleFloating : ConsoleEvent
    data class SetAlpha(val level: Int) : ConsoleEvent
    data object PermissionRecheck : ConsoleEvent
    data object Resume : ConsoleEvent
}
