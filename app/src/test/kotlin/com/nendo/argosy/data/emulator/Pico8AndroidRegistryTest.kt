package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.scanner.Pico8CartFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Pico8AndroidRegistryTest {

    @Test
    fun `PICO-8 for Android gets the cart as a plain path in the intent data`() {
        val def = EmulatorRegistry.getById("pico8_android")!!
        assertEquals("io.wip.pico8", def.packageName)
        assertEquals(android.content.Intent.ACTION_VIEW, def.launchAction)
        val config = def.launchConfig as LaunchConfig.Custom
        assertEquals("com.godot.game.GodotAppLauncher", config.activityClass)
        assertEquals(RomBindingFormat.ABSOLUTE_PATH, config.defaultDataBinding)
        assertTrue(config.intentExtras.isEmpty())
    }

    @Test
    fun `cart images are recognised by name or by their folder`() {
        assertTrue(Pico8CartFiles.isCartImage("Celeste.p8.png", "Downloads"))
        assertTrue(Pico8CartFiles.isCartImage("CELESTE.P8.PNG", "misc"))
        assertTrue(Pico8CartFiles.isCartImage("celeste.png", "pico8"))
        assertTrue(Pico8CartFiles.isCartImage("celeste.png", "PICO-8"))
        assertTrue(Pico8CartFiles.isCartImage("celeste.png", "pico_8"))
    }

    @Test
    fun `other images are not carts`() {
        assertFalse(Pico8CartFiles.isCartImage("Super Metroid.png", "boxart"))
        assertFalse(Pico8CartFiles.isCartImage("screenshot.png", "snes"))
        assertFalse(Pico8CartFiles.isCartImage("cover.png", "media"))
    }

    @Test
    fun `cart titles drop the whole cart suffix`() {
        assertEquals("Celeste", Pico8CartFiles.titleStem("Celeste.p8.png"))
        assertEquals("Celeste", Pico8CartFiles.titleStem("Celeste.p8"))
        assertEquals("celeste", Pico8CartFiles.titleStem("celeste.png"))
        assertEquals("Super Mario World (USA)", Pico8CartFiles.titleStem("Super Mario World (USA).sfc"))
    }
}
