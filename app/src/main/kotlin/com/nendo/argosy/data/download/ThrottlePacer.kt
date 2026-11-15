package com.nendo.argosy.data.download

import kotlinx.coroutines.delay

/**
 * Paces a download loop to a fraction of the throughput it actually achieves. Each chunk is timed
 * from [startChunk] to [endChunk]; with a throttle multiplier `m` the loop then waits
 * `elapsed * (1/m - 1)`. A multiplier of 0.5 halves the transfer rate whatever the link speed.
 * Waits shorter than [MIN_WAIT_NANOS] accumulate until they reach it.
 */
class ThrottlePacer(private val nanoTime: () -> Long = System::nanoTime) {
    private var chunkStartedAt = nanoTime()
    private var owedNanos = 0L

    fun startChunk() {
        chunkStartedAt = nanoTime()
    }

    suspend fun endChunk(multiplier: Float) {
        val waitNanos = owedAfterChunk(multiplier)
        if (waitNanos > 0) delay(waitNanos / NANOS_PER_MILLI)
    }

    internal fun owedAfterChunk(multiplier: Float): Long {
        val elapsed = (nanoTime() - chunkStartedAt).coerceAtLeast(0L)
        if (multiplier <= 0f || multiplier >= 1f) {
            owedNanos = 0L
            return 0L
        }
        owedNanos += (elapsed * (1.0 / multiplier - 1.0)).toLong()
        if (owedNanos < MIN_WAIT_NANOS) return 0L
        return owedNanos.also { owedNanos = 0L }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MIN_WAIT_NANOS = 20 * NANOS_PER_MILLI
    }
}
