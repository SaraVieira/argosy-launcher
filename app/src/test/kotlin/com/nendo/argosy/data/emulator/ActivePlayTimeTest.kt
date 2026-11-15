package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivePlayTimeTest {

    @Test
    fun `active time is the smaller of foreground and screen-on time`() {
        assertEquals(30_000L to 70_000L, splitActivePlayTime(100_000, 30_000, 80_000))
        assertEquals(20_000L to 80_000L, splitActivePlayTime(100_000, 90_000, 20_000))
        assertEquals(50_000L to 50_000L, splitActivePlayTime(100_000, 50_000, 50_000))
    }

    @Test
    fun `an unmeasured value counts as the whole session`() {
        assertEquals(40_000L to 60_000L, splitActivePlayTime(100_000, 40_000, -1))
        assertEquals(100_000L to 0L, splitActivePlayTime(100_000, -1, -1))
    }

    @Test
    fun `active time never exceeds the session`() {
        assertEquals(100_000L to 0L, splitActivePlayTime(100_000, 150_000, 150_000))
    }
}
