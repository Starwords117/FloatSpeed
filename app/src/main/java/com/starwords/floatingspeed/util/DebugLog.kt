package com.starwords.floatingspeed.util

import android.util.Log
import com.starwords.floatingspeed.BuildConfig

object DebugLog {

    private const val TAG = "FloatSpeed"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
