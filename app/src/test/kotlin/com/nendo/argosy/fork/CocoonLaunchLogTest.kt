package com.nendo.argosy.fork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CocoonLaunchLogTest {

    private val header = Regex("""══+ (?:Launch|Swap): (.+?) ══+""")
    private val gameLine = Regex("""Game: (.+?) \| File: (.+)""")
    private val retroArchPlayer = Regex("""Player: .+? - (\S+) \(([^/]+)/([^)]+)\)""")
    private val standalonePlayer = Regex("""Player: \S+(?:\s\S+)* \(([^/]+)/([^)]+)\)""")
    private val intentLine =
        Regex("""Intent: action=([^,]+), data=([^,]+), component=ComponentInfo\{([^/]+)/([^}]+)\}""")
    private val filePath = Regex("""\{file\.path\} → (.+)""")
    private val success = Regex("""(?:Launch|Swap) SUCCESS""")

    private val launchedAt = LocalDateTime.of(2026, 11, 15, 14, 2, 3, 45_000_000)

    private fun block(title: String, romPath: String?) = CocoonLaunchLog.lines(
        title = title,
        romPath = romPath,
        gameId = 42L,
        sourcePackage = "dev.saravieira.argosy",
        activityClass = "com.nendo.argosy.ArgosyActivity",
        launchedAt = launchedAt
    )

    private fun List<String>.match(regex: Regex): MatchResult? = firstNotNullOfOrNull { regex.find(it) }

    @Test
    fun `block parses as a successful Game Twin launch`() {
        val lines = block("Legend of Zelda, The - A Link to the Past", "/storage/emulated/0/ROMs/snes/Zelda, The (USA).sfc")

        assertEquals("Legend of Zelda, The - A Link to the Past", lines.match(header)?.groupValues?.get(1))
        assertEquals(
            listOf("Legend of Zelda, The - A Link to the Past", "Zelda, The (USA).sfc"),
            lines.match(gameLine)?.groupValues?.drop(1)
        )
        assertEquals("/storage/emulated/0/ROMs/snes/Zelda, The (USA).sfc", lines.match(filePath)?.groupValues?.get(1))
        assertEquals(
            listOf(
                "android.intent.action.VIEW",
                "argosy://launch?game_id=42",
                "dev.saravieira.argosy",
                "com.nendo.argosy.ArgosyActivity"
            ),
            lines.match(intentLine)?.groupValues?.drop(1)
        )
        assertTrue(lines.any { success.containsMatchIn(it) })
        assertTrue(lines.first().startsWith("[2026-11-15 14:02:03.045] "))
    }

    @Test
    fun `player line reads as a standalone app, never a RetroArch core`() {
        val lines = block("Sonic - The Hedgehog", "/storage/emulated/0/ROMs/genesis/Sonic.md")

        assertNull(lines.match(retroArchPlayer))
        assertEquals(
            listOf("dev.saravieira.argosy", "com.nendo.argosy.ArgosyActivity"),
            lines.match(standalonePlayer)?.groupValues?.drop(1)
        )
    }

    @Test
    fun `game without a local file still names the game`() {
        val lines = block("Celeste", null)

        assertEquals("Celeste", lines.match(header)?.groupValues?.get(1))
        assertNull(lines.match(gameLine))
        assertNull(lines.match(filePath))
        assertTrue(lines.any { success.containsMatchIn(it) })
    }

    @Test
    fun `title whitespace is collapsed onto one line`() {
        val lines = block("  Final\nFantasy   VI ", "/roms/ff6.sfc")

        assertEquals("Final Fantasy VI", lines.match(header)?.groupValues?.get(1))
        assertEquals("Final Fantasy VI", lines.match(gameLine)?.groupValues?.get(1))
    }

    @Test
    fun `blank title writes nothing`() {
        assertTrue(block("   ", "/roms/x.sfc").isEmpty())
    }
}
