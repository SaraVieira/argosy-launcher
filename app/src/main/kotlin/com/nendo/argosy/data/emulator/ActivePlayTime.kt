package com.nendo.argosy.data.emulator

/**
 * Splits a session of [totalMs] into active and standby time. Active is the smaller of time in
 * the foreground and time with the screen on; a negative measurement counts as the whole session.
 */
fun splitActivePlayTime(totalMs: Long, foregroundMs: Long, screenOnMs: Long): Pair<Long, Long> {
    val foreground = if (foregroundMs >= 0) foregroundMs else totalMs
    val screenOn = if (screenOnMs >= 0) screenOnMs else totalMs
    val active = minOf(foreground, screenOn).coerceIn(0, totalMs.coerceAtLeast(0))
    return active to (totalMs - active).coerceAtLeast(0)
}
