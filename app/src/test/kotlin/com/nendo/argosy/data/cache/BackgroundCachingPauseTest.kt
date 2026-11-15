package com.nendo.argosy.data.cache

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCachingPauseTest {

    @Test
    fun `nothing is paused until an owner pauses`() {
        val pause = BackgroundCachingPause()

        assertFalse(pause.isPaused)
    }

    @Test
    fun `an owner's repeated pauses are released by its single resume`() {
        val pause = BackgroundCachingPause()
        val screen = Any()

        pause.pause(screen)
        pause.pause(screen)
        pause.resume(screen)

        assertFalse(pause.isPaused)
    }

    @Test
    fun `one owner resuming leaves another owner's pause in place`() {
        val pause = BackgroundCachingPause()
        val topScreen = Any()
        val bottomScreen = Any()

        pause.pause(topScreen)
        pause.pause(bottomScreen)
        pause.resume(topScreen)

        assertTrue(pause.isPaused)

        pause.resume(bottomScreen)

        assertFalse(pause.isPaused)
    }

    @Test
    fun `resuming an owner that never paused changes nothing`() {
        val pause = BackgroundCachingPause()
        val screen = Any()
        pause.pause(screen)

        assertFalse(pause.resume(Any()))
        assertTrue(pause.isPaused)
    }
}
