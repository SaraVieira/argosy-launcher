package com.nendo.argosy.fork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NowPlayingHandoffTest {

    @Test
    fun `full session carries every field`() {
        val extras = NowPlaying(
            sourcePackage = "dev.saravieira.argosy",
            title = "Super Metroid",
            platformSlug = "snes",
            romPath = "/storage/emulated/0/ROMs/snes/Super Metroid.sfc",
            emulatorPackage = "com.retroarch",
            rommId = 42L,
            igdbId = 1103L,
            raId = 1446L,
            sessionStartMillis = 1_794_731_500_000L,
            hardcore = true
        ).toExtras()

        assertEquals(
            mapOf(
                NowPlayingExtras.VERSION to NowPlayingExtras.CURRENT_VERSION,
                NowPlayingExtras.SOURCE_PACKAGE to "dev.saravieira.argosy",
                NowPlayingExtras.GAME_TITLE to "Super Metroid",
                NowPlayingExtras.PLATFORM_SLUG to "snes",
                NowPlayingExtras.ROM_PATH to "/storage/emulated/0/ROMs/snes/Super Metroid.sfc",
                NowPlayingExtras.EMULATOR_PACKAGE to "com.retroarch",
                NowPlayingExtras.ROMM_ID to 42L,
                NowPlayingExtras.IGDB_ID to 1103L,
                NowPlayingExtras.RA_ID to 1446L,
                NowPlayingExtras.SESSION_START_MS to 1_794_731_500_000L,
                NowPlayingExtras.HARDCORE to true
            ),
            extras
        )
    }

    @Test
    fun `unknown values are left out`() {
        val extras = NowPlaying(
            sourcePackage = "dev.saravieira.argosy",
            title = "Celeste",
            platformSlug = "",
            romPath = null,
            emulatorPackage = "  ",
            sessionStartMillis = 0L
        ).toExtras()

        assertEquals(
            setOf(
                NowPlayingExtras.VERSION,
                NowPlayingExtras.SOURCE_PACKAGE,
                NowPlayingExtras.GAME_TITLE,
                NowPlayingExtras.HARDCORE
            ),
            extras.keys
        )
        assertEquals(false, extras[NowPlayingExtras.HARDCORE])
    }

    @Test
    fun `blank title is not sent as a title`() {
        val extras = NowPlaying(sourcePackage = "dev.saravieira.argosy", title = "").toExtras()

        assertFalse(extras.containsKey(NowPlayingExtras.GAME_TITLE))
        assertEquals(NowPlayingExtras.CURRENT_VERSION, extras[NowPlayingExtras.VERSION])
    }

    @Test
    fun `keys stay in the public namespace`() {
        val extras = NowPlaying(
            sourcePackage = "dev.saravieira.argosy.debug",
            title = "Tetris",
            rommId = 1L,
            igdbId = 2L,
            raId = 3L,
            sessionStartMillis = 5L
        ).toExtras()

        extras.keys.forEach { key ->
            assert(key.startsWith("com.nendo.argosy.extra.")) { "unexpected key $key" }
        }
    }
}
