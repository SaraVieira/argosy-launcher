package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamicomDiskSystemRegistryTest {

    private val disksysMd5 = "ca30b50f880eb660a320674ed365ef7a"

    @Test
    fun `every RetroArch build is offered for the Famicom Disk System`() {
        val offered = EmulatorRegistry.getForPlatform("fds").map { it.id }
        listOf("retroarch", "retroarch_64", "retroarch_32").forEach { id ->
            assertTrue(id, id in offered)
        }
    }

    @Test
    fun `a RetroArch fork package also runs the Famicom Disk System`() {
        val family = EmulatorRegistry.getEmulatorFamilies().first { it.baseId == "retroarch" }
        assertTrue("fds" in family.supportedPlatforms)
    }

    @Test
    fun `RetroArch is the recommended emulator for the Famicom Disk System`() {
        assertEquals(
            listOf("retroarch", "retroarch_64", "retroarch_32"),
            EmulatorRegistry.getRecommendedEmulators()["fds"]
        )
    }

    @Test
    fun `only the cores that load fds files are offered, FCEUmm first`() {
        assertEquals("fceumm", EmulatorRegistry.getPreferredCore("fds"))
        assertEquals("fceumm", EmulatorRegistry.getDefaultCore("fds")?.id)
        val cores = EmulatorRegistry.getCoresForPlatform("fds").map { it.id }
        assertEquals(listOf("fceumm", "nestopia", "mesen"), cores)
        assertEquals(listOf("fceumm", "nestopia", "mesen"), EmulatorRegistry.getRetroArchCorePatterns()["fds"])
    }

    @Test
    fun `the disk system BIOS is required and lands in RetroArch's system folder by name`() {
        val bios = BiosPathRegistry.getBiosRequirements("fds").single()
        assertEquals("disksys.rom", bios.fileName)
        assertEquals(disksysMd5, bios.md5Hash)
        assertTrue(bios.isRequired)
        assertEquals("disksys.rom", BiosPathRegistry.getRetroArchBiosName(disksysMd5))
    }
}
