package com.nendo.argosy.data.download

import android.os.PowerManager

internal object DownloadThermalPolicy {

    fun stateFor(platformStatus: Int?, cpuTemp: Float, batteryTemp: Float): ThermalState {
        val cpuSane = cpuTemp in PLAUSIBLE_CPU_RANGE
        val batterySane = batteryTemp in PLAUSIBLE_BATTERY_RANGE
        val pastPauseLimit = (cpuSane && cpuTemp >= CPU_PAUSE_TEMP) ||
            (batterySane && batteryTemp >= BATTERY_PAUSE_TEMP)
        if (pastPauseLimit) return ThermalState.PAUSED
        if (platformStatus != null) return stateForPlatformStatus(platformStatus)
        val pastThrottleLimit = (cpuSane && cpuTemp >= CPU_THROTTLE_TEMP) ||
            (batterySane && batteryTemp >= BATTERY_THROTTLE_TEMP)
        return if (pastThrottleLimit) ThermalState.THROTTLED else ThermalState.NORMAL
    }

    fun multiplierFor(state: ThermalState): Float = when (state) {
        ThermalState.PAUSED -> 0f
        ThermalState.THROTTLED -> THROTTLED_MULTIPLIER
        ThermalState.NORMAL -> 1.0f
    }

    private fun stateForPlatformStatus(status: Int): ThermalState = when {
        status >= PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.PAUSED
        status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.THROTTLED
        else -> ThermalState.NORMAL
    }

    private const val THROTTLED_MULTIPLIER = 0.5f
    private const val CPU_THROTTLE_TEMP = 85f
    private const val CPU_PAUSE_TEMP = 90f
    private const val BATTERY_THROTTLE_TEMP = 38f
    private const val BATTERY_PAUSE_TEMP = 43f
    private val PLAUSIBLE_CPU_RANGE = 0f..120f
    private val PLAUSIBLE_BATTERY_RANGE = 0f..70f
}
