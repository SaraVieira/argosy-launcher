package com.nendo.argosy.fork

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameTwinShortcutTest {

    private val info = ApplicationInfo()

    private fun packageManager(launchable: Boolean, label: String = "Game Twin"): PackageManager = mockk {
        every { getLaunchIntentForPackage(GameTwinShortcut.PACKAGE) } returns if (launchable) mockk<Intent>() else null
        every { getApplicationInfo(GameTwinShortcut.PACKAGE, 0) } returns info
        every { getApplicationLabel(info) } returns label
    }

    @Test
    fun `installed app gives its own label`() {
        assertEquals("Game Twin", GameTwinShortcut.label(packageManager(launchable = true)))
    }

    @Test
    fun `app that cannot be launched gives no shortcut`() {
        assertNull(GameTwinShortcut.label(packageManager(launchable = false)))
    }

    @Test
    fun `blank label gives no shortcut`() {
        assertNull(GameTwinShortcut.label(packageManager(launchable = true, label = " ")))
    }

    @Test
    fun `package manager failure gives no shortcut`() {
        val failing = mockk<PackageManager> {
            every { getLaunchIntentForPackage(any()) } throws SecurityException("hidden")
        }
        assertNull(GameTwinShortcut.label(failing))
    }
}
