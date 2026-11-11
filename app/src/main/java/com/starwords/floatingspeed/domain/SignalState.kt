package com.starwords.floatingspeed.domain

sealed interface SignalState {
    data object Searching : SignalState
    data class Good(val accuracyM: Float) : SignalState
    data class Fair(val accuracyM: Float) : SignalState
    data class Poor(val accuracyM: Float) : SignalState
    data object Lost : SignalState
    data object NoPermission : SignalState
}

class SignalStateMachine {

    var state: SignalState = SignalState.Searching
        private set

    private var lastFixElapsedMs: Long? = null

    fun onFix(accuracyM: Float, elapsedMs: Long) {
        lastFixElapsedMs = elapsedMs
        state = when {
            accuracyM <= GOOD_ACCURACY_M -> SignalState.Good(accuracyM)
            accuracyM <= FAIR_ACCURACY_M -> SignalState.Fair(accuracyM)
            else -> SignalState.Poor(accuracyM)
        }
    }

    fun onTick(nowElapsedMs: Long) {
        val last = lastFixElapsedMs ?: return
        if (nowElapsedMs - last >= LOST_TIMEOUT_MS) {
            state = SignalState.Lost
        }
    }

    fun onPermissionGranted() {
        if (state == SignalState.NoPermission) {
            state = SignalState.Searching
            lastFixElapsedMs = null
        }
    }

    fun onPermissionLost() {
        state = SignalState.NoPermission
        lastFixElapsedMs = null
    }

    companion object {
        const val GOOD_ACCURACY_M = 20f
        const val FAIR_ACCURACY_M = 50f
        const val LOST_TIMEOUT_MS = 3000L
    }
}
