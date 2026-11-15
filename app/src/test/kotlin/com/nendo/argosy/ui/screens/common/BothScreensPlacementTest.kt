package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.util.DisplayAffinityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BothScreensPlacementTest {

    @Test
    fun `every Azahar build draws both screens`() {
        for (pkg in listOf("org.azahar_emu.azahar", "io.github.lime3ds.android", "io.github.azahar_emu.nightly")) {
            assertTrue(pkg, EmulatorLaunchTargetResolver.drawsBothScreens(pkg))
        }
    }

    @Test
    fun `melonDualDS and PokeDaisy draw both screens`() {
        for (pkg in listOf("me.magnum.melondualds", "com.pokedaisy.app")) {
            assertTrue(pkg, EmulatorLaunchTargetResolver.drawsBothScreens(pkg))
        }
    }

    @Test
    fun `emulators without a second screen of their own keep the normal placement`() {
        for (pkg in listOf("io.github.borked3ds.android", "org.citra.emu", "org.citra.citra_emu", "me.magnum.melonds")) {
            assertFalse(pkg, EmulatorLaunchTargetResolver.drawsBothScreens(pkg))
        }
    }

    @Test
    fun `a game that draws both screens opens on display zero while it is lit`() {
        assertEquals(0, DisplayAffinityHelper.resolveBothScreensDisplayId(defaultDisplayLit = true))
        assertNull(DisplayAffinityHelper.resolveBothScreensDisplayId(defaultDisplayLit = false))
    }
}
