package com.nendo.argosy.ui.notification

import com.nendo.argosy.domain.model.BatteryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryNoticeForTest {

    private val bothBooleans = listOf(true, false)

    @Test
    fun `critical shows whether or not a game runs`() {
        bothBooleans.forEach { gameRunning ->
            assertEquals(
                BatteryNoticeKind.CRITICAL,
                batteryNoticeFor(listOf(BatteryEvent.CRITICAL_5), gameRunning = gameRunning)
            )
        }
    }

    @Test
    fun `critical outranks low in the same reading`() {
        assertEquals(
            BatteryNoticeKind.CRITICAL,
            batteryNoticeFor(
                listOf(BatteryEvent.LOW_15, BatteryEvent.CRITICAL_5, BatteryEvent.CRITICAL_3),
                gameRunning = false
            )
        )
    }

    @Test
    fun `low shows whether or not a game runs`() {
        bothBooleans.forEach { gameRunning ->
            assertEquals(
                BatteryNoticeKind.LOW,
                batteryNoticeFor(listOf(BatteryEvent.LOW_15), gameRunning)
            )
        }
    }

    @Test
    fun `the three and one percent re-saves earn no card`() {
        bothBooleans.forEach { gameRunning ->
            assertNull(batteryNoticeFor(listOf(BatteryEvent.CRITICAL_3), gameRunning))
            assertNull(batteryNoticeFor(listOf(BatteryEvent.CRITICAL_1), gameRunning))
        }
    }

    @Test
    fun `charger cards show only while a game runs`() {
        assertEquals(BatteryNoticeKind.CHARGING, batteryNoticeFor(listOf(BatteryEvent.PLUGGED), true))
        assertEquals(BatteryNoticeKind.UNPLUGGED, batteryNoticeFor(listOf(BatteryEvent.UNPLUGGED), true))
        assertNull(batteryNoticeFor(listOf(BatteryEvent.PLUGGED), false))
        assertNull(batteryNoticeFor(listOf(BatteryEvent.UNPLUGGED), false))
    }
}
