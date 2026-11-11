package com.starwords.floatingspeed.location

import com.starwords.floatingspeed.domain.SpeedSample

interface LocationEngine {

    fun start(onSample: (SpeedSample) -> Unit, onError: (String) -> Unit = {})

    fun stop()
}
