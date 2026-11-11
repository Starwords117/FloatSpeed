package com.starwords.floatingspeed.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.starwords.floatingspeed.domain.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

interface SettingsSource {
    val unit: Flow<SpeedUnit>
}

class SettingsRepository(private val context: Context) : SettingsSource {

    private object Keys {
        val UNIT = stringPreferencesKey("unit")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HAD_ACTIVE_SESSION = booleanPreferencesKey("had_active_session")
        val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
        val OVERLAY_X = intPreferencesKey("overlay_x")
        val OVERLAY_Y = intPreferencesKey("overlay_y")
        val OVERLAY_ALPHA = intPreferencesKey("overlay_alpha")
    }

    override val unit: Flow<SpeedUnit> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.UNIT]
            ?.let { name -> SpeedUnit.entries.firstOrNull { it.name == name } }
            ?: SpeedUnit.KMH
    }

    val keepScreenOn: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    val hadActiveSession: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.HAD_ACTIVE_SESSION] ?: false
    }

    val floatingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.FLOATING_ENABLED] ?: false
    }

    val overlayX: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_X] ?: UNSET_POSITION
    }

    val overlayY: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_Y] ?: UNSET_POSITION
    }

    val overlayAlpha: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_ALPHA] ?: ALPHA_MEDIUM
    }

    suspend fun setUnit(unit: SpeedUnit) {
        context.settingsDataStore.edit { it[Keys.UNIT] = unit.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setHadActiveSession(active: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAD_ACTIVE_SESSION] = active }
    }

    suspend fun setFloatingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FLOATING_ENABLED] = enabled }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.settingsDataStore.edit {
            it[Keys.OVERLAY_X] = x
            it[Keys.OVERLAY_Y] = y
        }
    }

    suspend fun setOverlayAlpha(level: Int) {
        context.settingsDataStore.edit { it[Keys.OVERLAY_ALPHA] = level.coerceIn(0, 2) }
    }

    companion object {
        const val UNSET_POSITION = -1
        const val ALPHA_LOW = 0
        const val ALPHA_MEDIUM = 1
        const val ALPHA_HIGH = 2
    }
}
