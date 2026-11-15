package com.nendo.argosy.fork

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardAppShortcut
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InGameAppPinsTest {

    private fun packageManager(installed: Map<String, String>): PackageManager = mockk {
        every { getLaunchIntentForPackage(any()) } answers {
            if (firstArg<String>() in installed) mockk<Intent>() else null
        }
        every { getApplicationInfo(any<String>(), 0) } answers {
            ApplicationInfo().apply { packageName = firstArg() }
        }
        every { getApplicationLabel(any()) } answers {
            installed.getValue(firstArg<ApplicationInfo>().packageName)
        }
    }

    @Test
    fun `Game Twin is pinned until the user changes the pins`() {
        assertEquals(listOf("com.twinlore.app"), InGameAppPins.DEFAULT)
    }

    @Test
    fun `pinned apps keep their order and drop anything not installed`() {
        val pm = packageManager(mapOf("com.twinlore.app" to "Game Twin", "org.notes" to "Notes"))

        assertEquals(
            listOf(DashboardAppShortcut("org.notes", "Notes"), DashboardAppShortcut("com.twinlore.app", "Game Twin")),
            InGameAppPins.shortcuts(pm, listOf("org.notes", "com.gone", "com.twinlore.app"))
        )
    }

    @Test
    fun `blank label or a failing lookup gives no shortcut`() {
        assertNull(InGameAppPins.label(packageManager(mapOf("org.blank" to " ")), "org.blank"))
        val failing = mockk<PackageManager> {
            every { getLaunchIntentForPackage(any()) } throws SecurityException("hidden")
        }
        assertNull(InGameAppPins.label(failing, "org.any"))
    }
}
