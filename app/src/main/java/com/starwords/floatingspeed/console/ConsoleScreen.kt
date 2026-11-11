package com.starwords.floatingspeed.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starwords.floatingspeed.data.SettingsRepository
import com.starwords.floatingspeed.domain.SignalState
import com.starwords.floatingspeed.domain.SpeedUnit
import com.starwords.floatingspeed.domain.Units
import kotlin.math.roundToInt

@Composable
fun ConsoleScreen(
    state: ConsoleUiState,
    onEvent: (ConsoleEvent) -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "悬浮测速",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatusRow(state = state)
            val bannerAction = when (state.banner) {
                BannerType.OVERLAY_REVOKED -> onOpenOverlaySettings
                BannerType.LOCATION_DISABLED -> onOpenLocationSettings
                BannerType.PRECISE_LOCATION,
                BannerType.BACKGROUND_LOCATION,
                BannerType.LOCATION_STALLED,
                BannerType.ENGINE_ERROR,
                null,
                -> onOpenAppSettings
            }
            if (state.banner != null && state.bannerMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                NoticeBanner(title = state.bannerMessage, onAction = bannerAction)
            }
            Spacer(modifier = Modifier.weight(1f))
            SpeedPreview(state = state)
            Spacer(modifier = Modifier.weight(1f))
            SettingsCard(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun StatusRow(state: ConsoleUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(signalColor(state.signal)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = signalLabel(state.signal),
            style = MaterialTheme.typography.bodyMedium,
            color = signalColor(state.signal),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "🛰 ${state.satelliteText}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpeedPreview(state: ConsoleUiState, modifier: Modifier = Modifier) {
    val description = "当前速度 ${state.speedText} ${Units.spokenUnit(state.unit)}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.speedText,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    state.speedDimmed ->
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    state.accuracyPoor -> Color(0xFFFFC107)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = " ${Units.unitLabel(state.unit)}",
                style = MaterialTheme.typography.titleLarge,
                color = if (state.speedDimmed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Text(
            text = hintFor(state.signal),
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.speedDimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

@Composable
private fun SettingsCard(state: ConsoleUiState, onEvent: (ConsoleEvent) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            SettingRow(label = "悬浮测速") {
                Switch(
                    checked = state.floatingEnabled,
                    onCheckedChange = { onEvent(ConsoleEvent.ToggleFloating) },
                )
            }
            SettingRow(label = "单位") {
                FilterChip(
                    selected = state.unit == SpeedUnit.KMH,
                    onClick = { if (state.unit != SpeedUnit.KMH) onEvent(ConsoleEvent.ToggleUnit) },
                    label = { Text("km/h") },
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = state.unit == SpeedUnit.MPH,
                    onClick = { if (state.unit != SpeedUnit.MPH) onEvent(ConsoleEvent.ToggleUnit) },
                    label = { Text("mph") },
                )
            }
            SettingRow(label = "透明度") {
                AlphaChip("低", state.alphaLevel == SettingsRepository.ALPHA_LOW) {
                    onEvent(ConsoleEvent.SetAlpha(SettingsRepository.ALPHA_LOW))
                }
                Spacer(modifier = Modifier.width(6.dp))
                AlphaChip("中", state.alphaLevel == SettingsRepository.ALPHA_MEDIUM) {
                    onEvent(ConsoleEvent.SetAlpha(SettingsRepository.ALPHA_MEDIUM))
                }
                Spacer(modifier = Modifier.width(6.dp))
                AlphaChip("高", state.alphaLevel == SettingsRepository.ALPHA_HIGH) {
                    onEvent(ConsoleEvent.SetAlpha(SettingsRepository.ALPHA_HIGH))
                }
            }
            SettingRow(label = "屏幕常亮") {
                Switch(
                    checked = state.keepScreenOn,
                    onCheckedChange = { onEvent(ConsoleEvent.ToggleKeepScreenOn) },
                )
            }
        }
    }
}

@Composable
private fun AlphaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

@Composable
private fun NoticeBanner(title: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(text = "去设置")
            }
        }
    }
}

private fun signalLabel(signal: SignalState): String = when (signal) {
    SignalState.Searching -> "正在搜星"
    is SignalState.Good -> "信号良好 · 精度 ${signal.accuracyM.roundToInt()}m"
    is SignalState.Fair -> "信号一般 · 精度 ${signal.accuracyM.roundToInt()}m"
    is SignalState.Poor -> "信号差 · 精度 ${signal.accuracyM.roundToInt()}m"
    SignalState.Lost -> "信号丢失"
    SignalState.NoPermission -> "无定位权限"
}

private fun signalColor(signal: SignalState): Color = when (signal) {
    SignalState.Searching -> Color(0xFF90CAF9)
    is SignalState.Good -> Color(0xFF4CAF50)
    is SignalState.Fair -> Color(0xFFFFA000)
    is SignalState.Poor -> Color(0xFFE53935)
    SignalState.Lost -> Color(0xFF9E9E9E)
    SignalState.NoPermission -> Color(0xFFE53935)
}

private fun hintFor(signal: SignalState): String = when (signal) {
    SignalState.Searching -> "正在搜星…"
    SignalState.Lost -> "信号丢失"
    SignalState.NoPermission -> "缺少定位权限"
    is SignalState.Good, is SignalState.Fair, is SignalState.Poor -> ""
}
