package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatermelonDSRegistryTest {

    private fun variant(packageName: String): EmulatorDef {
        val family = EmulatorRegistry.findFamilyForPackage(packageName)!!
        return EmulatorRegistry.createDefFromFamily(family, packageName)
    }

    @Test
    fun `release build keeps its id under the new name and repository`() {
        val def = EmulatorRegistry.getByPackage("me.magnum.melondualds")!!
        assertEquals("melondualds", def.id)
        assertEquals("WatermelonDS", def.displayName)
        assertEquals("me.magnum.melondualds.LAUNCH_ROM", def.launchAction)
        assertEquals(ReleaseSource.GitHub("SapphireRhodonite/WatermelonDS"), def.releaseSource)
        assertEquals("https://github.com/SapphireRhodonite/WatermelonDS/releases", def.downloadUrl)
        assertTrue(def.drawsBothScreens)
    }

    @Test
    fun `nightly build launches through its own LAUNCH_ROM action`() {
        val def = variant("me.magnum.melondualds.nightly")
        assertEquals("melondualds_me_magnum_melondualds_nightly", def.id)
        assertEquals("WatermelonDS (Nightly)", def.displayName)
        assertEquals("me.magnum.melondualds.nightly.LAUNCH_ROM", def.launchAction)
        assertTrue(def.drawsBothScreens)
        val config = def.launchConfig as LaunchConfig.Custom
        assertEquals("me.magnum.melonds.ui.emulator.EmulatorActivity", config.activityClass)
        assertEquals(ExtraValue.FileUri, config.intentExtras["uri"])
        assertEquals(RomBindingFormat.FILE_PROVIDER, config.defaultDataBinding)
    }

    @Test
    fun `debug build launches through its own LAUNCH_ROM action`() {
        assertEquals("me.magnum.melondualds.dev.LAUNCH_ROM", variant("me.magnum.melondualds.dev").launchAction)
    }

    @Test
    fun `variants resolve WatermelonDS paths and melonDS forks stay melonDS`() {
        assertEquals("melondualds", EmulatorRegistry.familyBaseIdFor("melondualds_me_magnum_melondualds_nightly"))
        assertEquals("melonds", EmulatorRegistry.findFamilyForPackage("me.magnum.melonds.nightly")!!.baseId)
        assertEquals(android.content.Intent.ACTION_VIEW, variant("me.magnum.melonds.nightly").launchAction)
    }
}
