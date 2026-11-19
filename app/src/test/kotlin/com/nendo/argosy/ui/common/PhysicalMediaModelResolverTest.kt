package com.nendo.argosy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalMediaModelResolverTest {

    @Test
    fun vitaUsesCardEvidenceAndRespectsDigitalEditions() {
        assertEquals("vita", physicalMediaModelFor("psvita", title = "Killzone: Mercenary"))
        assertEquals("vita", physicalMediaModelFor("vita", fileName = "Gravity Rush (USA).zip"))
        assertEquals("vita", physicalMediaModelFor("vita", fileName = "New Game.psv"))
        assertNull(physicalMediaModelFor("vita", fileName = "Gravity Rush [PSN].zip"))
        assertNull(physicalMediaModelFor("vita", title = "Gravity Rush", fileName = "download.pkg"))
        assertNull(physicalMediaModelFor("vita", title = "TxK"))
    }

    @Test
    fun cabinetServesArcadeAndCapcomPlaySystems() {
        listOf("arcade", "cps1", "cps2", "cps3", "cps-1", "CPS-2", "cps-3").forEach { platform ->
            assertEquals(platform, "arcade", physicalMediaModelFor(platform, fileName = "1941.zip"))
        }
        listOf("neogeoaes", "neogeo", "mame", "fbneo", "cps4").forEach { platform ->
            assertNull(platform, physicalMediaModelFor(platform, fileName = "1941.zip"))
        }
    }

    @Test
    fun mapsCanonicalPlatformsAndAliasesToAllModelBasenames() {
        val expected = mapOf(
            "game_boy_advance" to "gba", "gameboy" to "gb", "game-boy-color" to "gbc",
            "atari2600" to "atari2600", "atari-2600" to "atari2600",
            "nes" to "nes", "snes" to "snes", "sfam" to "snes-pal", "nintendo-64" to "n64",
            "nintendo_ds" to "ds", "n3ds" to "3ds", "nswitch" to "switch", "megadrive" to "md",
            "mastersystem" to "sms", "sgg" to "gg", "pc-engine" to "tg16",
            "playstation-portable" to "psp", "playstation" to "disc"
        )
        expected.forEach { (platform, model) -> assertEquals(platform, model, physicalMediaModelFor(platform)) }
    }

    @Test
    fun distinguishesHuCardsFromDiscsAndNintendoCartridgesFromMagneticDisks() {
        assertEquals("tg16", physicalMediaModelFor("pc-engine-supergrafx"))
        assertEquals("disc", physicalMediaModelFor("pc-engine-cd"))
        assertEquals("disc", physicalMediaModelFor("sega-cd"))
        assertNull(physicalMediaModelFor("fds"))
        assertNull(physicalMediaModelFor("64dd"))
        assertNull(physicalMediaModelFor("android"))
        assertNull(physicalMediaModelFor("steam"))
        assertNull(physicalMediaModelFor("psp-minis"))
    }

    @Test
    fun resolvesSnesShellFromRomRegionThenTaggedFilenameThenPlatform() {
        assertEquals("snes", physicalMediaModelFor("snes", "Brazil", "Game (Europe).sfc"))
        assertEquals("snes-pal", physicalMediaModelFor("snes", "Japan"))
        assertEquals("snes-pal", physicalMediaModelFor("snes", "Korea"))
        assertEquals("snes-pal", physicalMediaModelFor("snes", fileName = "/games/Game (Europe) (Rev 1).sfc"))
        assertEquals("snes-pal", physicalMediaModelFor("snes", title = "Game [Australia]"))
        assertEquals("snes-pal", physicalMediaModelFor("snes-pal"))
        assertEquals("snes", physicalMediaModelFor("snes", title = "Japan Pro Golf"))
    }

    @Test
    fun diskImageFormatsSelectTheDiskUsedByTheDrive() {
        assertEquals("floppy35", physicalMediaModelFor("commodore-amiga", fileName = "Game.ADZ"))
        assertEquals("floppy35", physicalMediaModelFor("amiga", fileName = "Game.adf.zip"))
        assertEquals("floppy525", physicalMediaModelFor("commodore64", fileName = "Game.D64.7z"))
        assertEquals("floppy525", physicalMediaModelFor("c128", fileName = "Game.d71"))
        assertEquals("floppy35", physicalMediaModelFor("c64", fileName = "Game.d81"))
        assertEquals("floppy525", physicalMediaModelFor("c64", fileName = "Game.zip", localPath = "/roms/Game.g64"))
        assertEquals("cassette", physicalMediaModelFor("c64", fileName = "Game.tap"))
        assertNull(physicalMediaModelFor("c64", fileName = "Game.crt"))
    }

    @Test
    fun commodore64ArchiveDefaultYieldsToKnownMedia() {
        assertEquals("floppy525", physicalMediaModelFor("commodore64", fileName = "Ultimate Wizard.cmd.7Z"))
        assertEquals("floppy35", physicalMediaModelFor("c64", fileName = "Game.d81.7z"))
        assertEquals("floppy35", physicalMediaModelFor("c64", fileName = "Game.7z", localPath = "/roms/Game.d81"))
        assertNull(physicalMediaModelFor("c64", fileName = "Game.7z", localPath = "/roms/Game.crt"))
        assertEquals("cassette", physicalMediaModelFor("c64", fileName = "Game.tap.7z"))
        assertNull(physicalMediaModelFor("c64", fileName = "Game.zip"))
        assertNull(physicalMediaModelFor("c128", fileName = "Game.7z"))
    }

    @Test
    fun amigaArchivesDefaultToThreeAndAHalfInchFloppies() {
        assertEquals("floppy35", physicalMediaModelFor("amiga", fileName = "Agony_v2.1_0960.LHA"))
        assertNull(physicalMediaModelFor("amiga", fileName = "MegaLoMania.lha.p2k.cfg"))
        assertEquals("floppy35", physicalMediaModelFor("amiga", fileName = "Bridge Strike.m3u"))
        assertNull(physicalMediaModelFor("c64", fileName = "Game.lha"))
    }

    @Test
    fun dosUsesRawDisketteCapacityOrAnExplicitSize() {
        assertEquals("floppy525", physicalMediaModelFor("msdos", fileName = "Disk.ima", fileSizeBytes = 1200L * 1024))
        assertEquals("floppy35", physicalMediaModelFor("dos", fileName = "Disk.img", fileSizeBytes = 1440L * 1024))
        assertEquals("floppy35", physicalMediaModelFor("dos", fileName = "Game (3.5 inch).zip"))
        assertEquals("floppy35", physicalMediaModelFor("dos", fileName = "v3.5.zip"))
        assertEquals("floppy35", physicalMediaModelFor("dos", fileName = "Disk.img.zip", fileSizeBytes = 360L * 1024))
        assertNull(physicalMediaModelFor("dos", fileName = "Game.zip", fileSizeBytes = 720L * 1024, localPath = "Disk.img"))
        assertEquals("disc", physicalMediaModelFor("dos", fileName = "Game.iso", fileSizeBytes = 720L * 1024))
    }

    @Test
    fun msxDistinguishesDiskReleasesAndLeavesAmbiguousSizesUnchanged() {
        assertEquals("floppy35", physicalMediaModelFor("msx", fileName = "Game.dsk", fileSizeBytes = 720L * 1024))
        assertEquals("floppy525", physicalMediaModelFor("msx2", fileName = "Game (5.25-inch).dsk.zip"))
        assertNull(physicalMediaModelFor("msx", fileName = "Game.dsk", fileSizeBytes = 360L * 1024))
        assertEquals("msx", physicalMediaModelFor("msx2", fileName = "Game.rom"))
        assertEquals("msx", physicalMediaModelFor("msx2", fileName = "Game.zip"))
        assertEquals("cassette", physicalMediaModelFor("msx", fileName = "Game.zip", localPath = "/msx/Game.cas"))
        assertNull(physicalMediaModelFor("msx", fileName = "Game.zip", localPath = "/msx/Game.dsk"))
        assertNull(physicalMediaModelFor("acpc", fileName = "Game.dsk", fileSizeBytes = 194816))
        assertNull(physicalMediaModelFor("fds", fileName = "Game.fds"))
    }

    @Test
    fun resolvesComputerEditionsAndDiskAlternatives() {
        assertEquals("floppy525", physicalMediaModelFor("sharp-x68000", fileName = "Game.XDF"))
        assertEquals("floppy525", physicalMediaModelFor("x68k", fileName = "Game.m3u"))
        assertEquals("floppy35", physicalMediaModelFor("x68000", fileName = "Game (X68000 Compact).dim"))
        assertEquals("floppy35", physicalMediaModelFor("x68000-compact", fileName = "Game.xdf"))
        assertNull(physicalMediaModelFor("x68000", fileName = "Game (CD).m3u"))
        assertNull(physicalMediaModelFor("x68000", fileName = "System.hdf"))
        assertEquals("disc", physicalMediaModelFor("scummvm", fileName = "Myst Masterpiece (DVD Windows)"))
        assertEquals("disc", physicalMediaModelFor("dos", fileName = "Game_(CD-ROM).zip"))
        assertEquals("floppy525", physicalMediaModelFor("scummvm", title = "Game (5.25 inch floppy)"))
        assertEquals("floppy35", physicalMediaModelFor("scummvm", title = "Game (DOS floppy)"))
        assertNull(physicalMediaModelFor("scummvm", fileName = "Blackwell Legacy (Windows)"))
        assertNull(physicalMediaModelFor("dos", fileName = "Unknown.img"))
        assertEquals("floppy525", physicalMediaModelFor("atari8bit", fileName = "Game.xex"))
        assertEquals("floppy35", physicalMediaModelFor("atari8bit", fileName = "Game (3.5-inch).atr"))
        assertNull(physicalMediaModelFor("atari8bit", fileName = "Game.car"))
        assertEquals("floppy35", physicalMediaModelFor("msx", fileName = "Snatcher.m3u"))
        assertEquals("floppy35", physicalMediaModelFor("msx2", fileName = "Princess Maker (Floppy).zip"))
        assertEquals("msx", physicalMediaModelFor("msx2", fileName = "Floppy.rom.zip"))
    }

    @Test
    fun usesPhysicalFormatSpecificModels() {
        assertEquals("gc_mini_dvd", physicalMediaModelFor("ngc"))
        assertEquals("dreamcast_gd", physicalMediaModelFor("dc"))
        assertEquals("pico8", physicalMediaModelFor("pico-8", fileName = "Celeste.p8.PNG"))
        assertNull(physicalMediaModelFor("pico8", fileName = "Celeste.p8"))
        assertEquals("atari2600", physicalMediaModelFor("atari7800"))
        assertNull(physicalMediaModelFor("atari5200"))
    }

    @Test
    fun choosesTheFamicomShellForJapaneseReleases() {
        assertEquals("famicom_cart", physicalMediaModelFor("family-computer"))
        assertEquals("famicom_cart", physicalMediaModelFor("nes", "Japan,Unlicensed"))
        assertEquals("famicom_cart", physicalMediaModelFor("nes", fileName = "Game (Japan) (En).zip"))
        assertEquals("famicom_cart", physicalMediaModelFor("nes", title = "Famicom Jump"))
        assertEquals("nes", physicalMediaModelFor("nes", "Japan,USA"))
        assertEquals("nes", physicalMediaModelFor("nes", fileName = "Game (Japan, USA).zip"))
        assertEquals("nes", physicalMediaModelFor("nes", "World", title = "Famicom Jump"))
        assertEquals("nes", physicalMediaModelFor("nes", title = "Japan Championship"))
    }

    @Test
    fun spectrumDefaultsYieldToDiskAndNonTapeFormats() {
        listOf("Cybernoid.7z", "Game.zip", "Game.sna", "Game.z80", "Game.szx", "Game.tzx.gz", "Game.PZX")
            .forEach { assertEquals(it, "cassette", physicalMediaModelFor("zxs", fileName = it)) }
        assertEquals("floppy35", physicalMediaModelFor("zx", fileName = "Game (3.5-inch).mgt"))
        assertEquals("floppy525", physicalMediaModelFor("zx", fileName = "Game.zip", localPath = "/roms/Game (5.25-inch).trd"))
        listOf("Game.dck.zip", "Game.dsk.7z", "Game (Disk Edition).7z", "Game (TR-DOS).z80", "Game.PCM.zip", "Sound.wav")
            .forEach { assertNull(it, physicalMediaModelFor("zx-spectrum", fileName = it)) }
        assertNull(physicalMediaModelFor("zx", fileName = "Game.zip", localPath = "/roms/Game.dsk"))
    }

    @Test
    fun cassetteImageFormatsAreScopedToTheirSystems() {
        val examples = mapOf(
            "c64" to "Game.T64", "c128" to "Game.TAP.gz", "vic20" to "Game.tap",
            "acpc" to "Game.cdt", "amstrad-cpc" to "Game.tzx.zip",
            "msx" to "Game.CAS.7z", "msx2" to "Game.cas", "atari8bit" to "Game.cas"
        )
        examples.forEach { (platform, name) -> assertEquals(platform, "cassette", physicalMediaModelFor(platform, fileName = name)) }
        assertEquals("cassette", physicalMediaModelFor("c64", fileName = "Game.7z", localPath = "Game.tap"))
        assertNull(physicalMediaModelFor("msx", fileName = "Game.t64"))
        assertNull(physicalMediaModelFor("dos", fileName = "Game.cas"))
    }

    @Test
    fun taggedCassetteReleasesDoNotMisclassifyOtherMedia() {
        assertEquals("cassette", physicalMediaModelFor("c64", fileName = "Game (Tape).7z"))
        assertEquals("cassette", physicalMediaModelFor("msx", fileName = "Game [Cassette Edition].zip"))
        assertEquals("cassette", physicalMediaModelFor("atari8bit", fileName = "Game.zip", title = "Game (Tape Side A)"))
        assertEquals("msx", physicalMediaModelFor("msx", fileName = "Tape Worm.zip"))
        assertEquals("msx", physicalMediaModelFor("msx", fileName = "Mixtape Edition.zip"))
        assertEquals("msx", physicalMediaModelFor("msx", fileName = "Game (Tape).rom.zip"))
        assertNull(physicalMediaModelFor("c64", fileName = "Game (Tape).pcm.7z"))
        assertNull(physicalMediaModelFor("acpc", fileName = "Game (Tape).dsk"))
        assertEquals("floppy35", physicalMediaModelFor("c64", fileName = "Game (Tape).7z", localPath = "Game.d81"))
    }

    @Test
    fun verifiedArchiveMembersSelectDisksAcrossArchiveNamesAndRepacking() {
        val msxDiskSha1 = "d8150e889b32275e7dd2b0e42d2a2769c6e57053"
        val spectrumDiskSha1 = "8b51197edf36b0cee13a1edff56ba582cc68d356"
        assertEquals("floppy35", physicalMediaModelFor("msx2", fileName = "renamed.zip", sha1Hash = msxDiskSha1.uppercase()))
        assertEquals("floppy525", physicalMediaModelFor("zxs", fileName = "renamed.7z", sha1Hash = spectrumDiskSha1))
        assertEquals("floppy35", physicalMediaModelFor("zxs", fileName = "Disk (3.5-inch).zip", sha1Hash = spectrumDiskSha1))
        assertEquals("cassette", physicalMediaModelFor("zxs", fileName = "other.zip", sha1Hash = msxDiskSha1))
        assertEquals("cassette", physicalMediaModelFor("zxs", fileName = "other.zip", localPath = "/roms/Game.tap", sha1Hash = spectrumDiskSha1))
        assertEquals("msx", physicalMediaModelFor("msx", fileName = "other.zip", sha1Hash = "8a21d05ee36ee56b9994f7b1869761633f1a289d"))
        assertEquals("msx", physicalMediaModelFor("msx", fileName = "other.zip", sha1Hash = spectrumDiskSha1))
        assertNull(physicalMediaModelFor("zxs", fileName = "Plus3.dsk"))
        assertEquals("floppy525", physicalMediaModelFor("zxs", fileName = "Disk.trd"))
    }
}
