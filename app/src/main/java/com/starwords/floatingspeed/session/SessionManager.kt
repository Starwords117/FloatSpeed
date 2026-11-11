package com.starwords.floatingspeed.session

import android.os.SystemClock
import com.starwords.floatingspeed.console.PermissionState
import com.starwords.floatingspeed.data.SettingsSource
import com.starwords.floatingspeed.domain.SessionStats
import com.starwords.floatingspeed.domain.SignalState
import com.starwords.floatingspeed.domain.SpeedProcessor
import com.starwords.floatingspeed.domain.SpeedSample
import com.starwords.floatingspeed.domain.SpeedUnit
import com.starwords.floatingspeed.domain.Units
import com.starwords.floatingspeed.location.LocationEngine
import com.starwords.floatingspeed.location.LocationStatusProvider
import com.starwords.floatingspeed.location.SatelliteProvider
import com.starwords.floatingspeed.util.DebugLog
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SessionSnapshot(
    val speedText: String = "--",
    val speedDimmed: Boolean = true,
    val accuracyPoor: Boolean = false,
    val signal: SignalState = SignalState.Searching,
    val accuracyM: Float? = null,
    val satelliteText: String = "--",
    val unit: SpeedUnit = SpeedUnit.KMH,
    val permission: PermissionState = PermissionState.NotGranted,
    val locationEnabled: Boolean = true,
    val stalled: Boolean = false,
    val engineError: String? = null,
    val stats: SessionStats = SessionStats(),
) {
    companion object {
        val EMPTY = SessionSnapshot()
    }
}

class SessionManager(
    private val locationEngine: LocationEngine,
    private val satelliteEngine: SatelliteProvider,
    private val statusProvider: LocationStatusProvider,
    private val unitSource: SettingsSource,
) {

    private val holders = AtomicInteger(0)
    private val processor = SpeedProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(SessionSnapshot.EMPTY)
    val state: StateFlow<SessionSnapshot> = _state.asStateFlow()

    private var tickJob: Job? = null
    private var enginesRunning = false
    private var permission: PermissionState = PermissionState.NotGranted
    private var locationEnabled = true
    private var unit = SpeedUnit.KMH
    private var engineError: String? = null
    private var lastSpeedKmh: Float? = null
    private var latestAccuracyM: Float? = null
    private var lostSinceMs: Long? = null
    private var lastSampleAtMs: Long? = null
    private var hadSample = false
    private var tickCount = 0L
    private var enginesStartedAtMs = 0L
    private var lastRebindAtMs = 0L

    init {
        unitSource.unit.onEach {
            unit = it
            publish()
        }.launchIn(scope)
        satelliteEngine.info.onEach {
            publish()
        }.launchIn(scope)
    }

    fun retain(): Boolean {
        val n = holders.incrementAndGet()
        DebugLog.d("session retain=$n")
        if (n == 1) {
            ensureTickLoop()
            refreshExternalState()
        }
        return n == 1
    }

    fun release(): Boolean {
        while (true) {
            val current = holders.get()
            if (current <= 0) {
                return false
            }
            if (holders.compareAndSet(current, current - 1)) {
                DebugLog.d("session release=${current - 1}")
                if (current == 1) {
                    stopEnginesInternal()
                    tickJob?.cancel()
                    tickJob = null
                }
                return current == 1
            }
        }
    }

    fun holderCount(): Int = holders.get()

    fun enginesActive(): Boolean = enginesRunning

    fun refresh() {
        refreshExternalState()
    }

    private fun ensureTickLoop() {
        if (tickJob != null) return
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun refreshExternalState() {
        val current = statusProvider.permissionState()
        if (current == PermissionState.NotGranted && permission != PermissionState.NotGranted) {
            stopLocationEngines()
        }
        permission = current
        locationEnabled = statusProvider.isLocationEnabled()
        if (current == PermissionState.NotGranted) {
            processor.onPermissionLost()
            publish()
            return
        }
        processor.onPermissionGranted()
        if (!enginesRunning && holders.get() > 0) {
            startLocationEngines()
            publish()
            return
        }
        if (current == PermissionState.Granted && enginesRunning) {
            satelliteEngine.start()
        }
        publish()
    }

    private fun startLocationEngines() {
        locationEngine.start(::onLocationSample) { message ->
            engineError = message
            publish()
        }
        if (permission == PermissionState.Granted) {
            satelliteEngine.start()
        }
        enginesRunning = true
        val now = SystemClock.elapsedRealtime()
        enginesStartedAtMs = now
        lastRebindAtMs = now
        DebugLog.d("session engines start holders=${holders.get()}")
    }

    private fun stopLocationEngines() {
        if (!enginesRunning) return
        locationEngine.stop()
        satelliteEngine.stop()
        enginesRunning = false
        DebugLog.d("session engines stop")
    }

    private fun stopEnginesInternal() {
        stopLocationEngines()
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        tickCount++
        processor.onTick(now)
        maybeRebind(now)
        if (tickCount % EXTERNAL_RECHECK_TICKS == 0L) {
            refreshExternalState()
        } else {
            publish()
        }
    }

    private fun maybeRebind(now: Long) {
        if (!enginesRunning || permission == PermissionState.NotGranted) return
        val lastActivity = lastSampleAtMs ?: enginesStartedAtMs
        if (now - lastActivity >= REBIND_TIMEOUT_MS && now - lastRebindAtMs >= REBIND_MIN_INTERVAL_MS) {
            lastRebindAtMs = now
            DebugLog.d("session engines rebind")
            locationEngine.start(::onLocationSample) { message ->
                engineError = message
                publish()
            }
        }
    }

    private fun onLocationSample(sample: SpeedSample) {
        val now = SystemClock.elapsedRealtime()
        lastSampleAtMs = now
        hadSample = true
        val frame = processor.onSample(sample)
        latestAccuracyM = sample.accuracyM
        if (frame != null) {
            DebugLog.d(
                "frame raw=${frame.rawSpeedKmh} filt=${frame.speedKmh} acc=${frame.accuracyM}",
            )
            lastSpeedKmh = frame.speedKmh
        }
        publish()
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val signal = processor.signalState
        lostSinceMs = if (signal == SignalState.Lost) lostSinceMs ?: now else null
        val lostFor = lostSinceMs?.let { now - it }
        val showDash = when {
            permission == PermissionState.NotGranted -> true
            signal == SignalState.Searching || signal == SignalState.NoPermission -> true
            lastSpeedKmh == null -> true
            signal == SignalState.Lost -> lostFor == null || lostFor >= DASH_DELAY_MS
            else -> false
        }
        val stalled = enginesRunning &&
            permission != PermissionState.NotGranted &&
            hadSample &&
            lastSampleAtMs != null &&
            now - lastSampleAtMs!! >= STALL_TIMEOUT_MS
        _state.value = SessionSnapshot(
            speedText = if (showDash) "--" else Units.formatSpeed(lastSpeedKmh ?: 0f, unit),
            speedDimmed = signal == SignalState.Lost || signal == SignalState.NoPermission,
            accuracyPoor = latestAccuracyM != null && latestAccuracyM!! > POOR_DISPLAY_ACCURACY_M,
            signal = signal,
            accuracyM = latestAccuracyM,
            satelliteText = satelliteEngine.info.value.display(now),
            unit = unit,
            permission = permission,
            locationEnabled = locationEnabled,
            stalled = stalled,
            engineError = engineError,
            stats = processor.stats,
        )
    }

    companion object {
        private const val TICK_MS = 500L
        private const val DASH_DELAY_MS = 500L
        private const val POOR_DISPLAY_ACCURACY_M = 50f
        private const val EXTERNAL_RECHECK_TICKS = 10L
        private const val STALL_TIMEOUT_MS = 10_000L
        private const val REBIND_TIMEOUT_MS = 10_000L
        private const val REBIND_MIN_INTERVAL_MS = 10_000L
    }
}
