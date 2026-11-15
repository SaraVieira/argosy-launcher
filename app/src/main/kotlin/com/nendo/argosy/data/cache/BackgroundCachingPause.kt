package com.nendo.argosy.data.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * The owners currently pausing background image caching. Caching is paused while at least one
 * owner holds a pause; an owner's repeated pauses collapse into one, released by one resume.
 */
class BackgroundCachingPause {
    private val owners = ConcurrentHashMap.newKeySet<Any>()

    val isPaused: Boolean get() = owners.isNotEmpty()

    fun pause(owner: Any): Boolean = owners.add(owner)

    fun resume(owner: Any): Boolean = owners.remove(owner)
}
