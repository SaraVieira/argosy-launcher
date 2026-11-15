package com.nendo.argosy.data.download

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadThermalPolicyTest {

    private val coolCpu = 50f
    private val chargingBattery = 40f

    @Test
    fun `a warm charging battery alone no longer throttles when the platform reports no pressure`() {
        val state = DownloadThermalPolicy.stateFor(PowerManager.THERMAL_STATUS_NONE, coolCpu, chargingBattery)

        assertEquals(ThermalState.NORMAL, state)
    }

    @Test
    fun `light and moderate platform pressure keep full speed`() {
        listOf(PowerManager.THERMAL_STATUS_LIGHT, PowerManager.THERMAL_STATUS_MODERATE).forEach { status ->
            assertEquals(ThermalState.NORMAL, DownloadThermalPolicy.stateFor(status, coolCpu, 30f))
        }
    }

    @Test
    fun `severe and critical platform pressure throttle`() {
        listOf(PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_CRITICAL).forEach { status ->
            assertEquals(ThermalState.THROTTLED, DownloadThermalPolicy.stateFor(status, coolCpu, 30f))
        }
    }

    @Test
    fun `emergency and shutdown platform pressure pause`() {
        listOf(PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN).forEach { status ->
            assertEquals(ThermalState.PAUSED, DownloadThermalPolicy.stateFor(status, coolCpu, 30f))
        }
    }

    @Test
    fun `temperatures past the pause limits pause even when the platform reports no pressure`() {
        assertEquals(
            ThermalState.PAUSED,
            DownloadThermalPolicy.stateFor(PowerManager.THERMAL_STATUS_NONE, coolCpu, 43f)
        )
        assertEquals(
            ThermalState.PAUSED,
            DownloadThermalPolicy.stateFor(PowerManager.THERMAL_STATUS_NONE, 90f, 30f)
        )
    }

    @Test
    fun `without a platform status the temperature thresholds decide`() {
        assertEquals(ThermalState.THROTTLED, DownloadThermalPolicy.stateFor(null, coolCpu, chargingBattery))
        assertEquals(ThermalState.THROTTLED, DownloadThermalPolicy.stateFor(null, 85f, 30f))
        assertEquals(ThermalState.NORMAL, DownloadThermalPolicy.stateFor(null, coolCpu, 30f))
    }

    @Test
    fun `implausible readings are ignored`() {
        assertEquals(ThermalState.NORMAL, DownloadThermalPolicy.stateFor(null, 500f, -5f))
    }

    @Test
    fun `throttled downloads run at half speed and paused ones not at all`() {
        assertEquals(1.0f, DownloadThermalPolicy.multiplierFor(ThermalState.NORMAL))
        assertEquals(0.5f, DownloadThermalPolicy.multiplierFor(ThermalState.THROTTLED))
        assertEquals(0f, DownloadThermalPolicy.multiplierFor(ThermalState.PAUSED))
    }
}
