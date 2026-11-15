package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottlePacerTest {

    private var now = 0L
    private val millis = 1_000_000L

    private fun pacer() = ThrottlePacer { now }

    private fun ThrottlePacer.chunk(tookMs: Long, multiplier: Float): Long {
        startChunk()
        now += tookMs * millis
        return owedAfterChunk(multiplier)
    }

    @Test
    fun `no throttle means no wait`() {
        assertEquals(0L, pacer().chunk(tookMs = 50, multiplier = 1.0f))
    }

    @Test
    fun `half speed waits as long as the chunk took`() {
        assertEquals(50 * millis, pacer().chunk(tookMs = 50, multiplier = 0.5f))
    }

    @Test
    fun `a quarter of the speed waits three times the chunk time`() {
        assertEquals(90 * millis, pacer().chunk(tookMs = 30, multiplier = 0.25f))
    }

    @Test
    fun `short waits build up until they are worth sleeping for`() {
        val pacer = pacer()

        assertEquals(0L, pacer.chunk(tookMs = 8, multiplier = 0.5f))
        assertEquals(0L, pacer.chunk(tookMs = 8, multiplier = 0.5f))
        assertEquals(24 * millis, pacer.chunk(tookMs = 8, multiplier = 0.5f))
    }

    @Test
    fun `time between chunks is not counted as transfer time`() {
        val pacer = pacer()
        pacer.chunk(tookMs = 40, multiplier = 0.5f)
        now += 5_000 * millis

        assertEquals(40 * millis, pacer.chunk(tookMs = 40, multiplier = 0.5f))
    }

    @Test
    fun `a paused multiplier owes nothing and clears what was owed`() {
        val pacer = pacer()
        pacer.chunk(tookMs = 8, multiplier = 0.5f)

        assertEquals(0L, pacer.chunk(tookMs = 8, multiplier = 0f))
        assertEquals(0L, pacer.chunk(tookMs = 8, multiplier = 0.5f))
    }

    @Test
    fun `the download rate under throttle tracks the link rate, not the chunk size`() {
        val fastLink = pacer()
        val waitOnFastLink = fastLink.chunk(tookMs = 25, multiplier = 0.5f)
        val slowLink = pacer()
        val waitOnSlowLink = slowLink.chunk(tookMs = 250, multiplier = 0.5f)

        assertEquals(25 * millis, waitOnFastLink)
        assertEquals(250 * millis, waitOnSlowLink)
    }
}
