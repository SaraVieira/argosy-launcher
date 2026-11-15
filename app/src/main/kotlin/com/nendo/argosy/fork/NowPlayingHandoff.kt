package com.nendo.argosy.fork

import android.content.Intent
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.util.Logger

/**
 * Intent extras describing the game in play, attached to an app opened from the in-game dock.
 *
 * The keys are the same in every build. [VERSION] is always present on a handoff; any other value
 * that is unknown for the current game is absent.
 */
object NowPlayingExtras {
    const val VERSION = "com.nendo.argosy.extra.NOW_PLAYING_VERSION"
    const val SOURCE_PACKAGE = "com.nendo.argosy.extra.SOURCE_PACKAGE"
    const val GAME_TITLE = "com.nendo.argosy.extra.GAME_TITLE"
    const val PLATFORM_SLUG = "com.nendo.argosy.extra.PLATFORM_SLUG"
    const val ROM_PATH = "com.nendo.argosy.extra.ROM_PATH"
    const val EMULATOR_PACKAGE = "com.nendo.argosy.extra.EMULATOR_PACKAGE"
    const val ROMM_ID = "com.nendo.argosy.extra.ROMM_ID"
    const val IGDB_ID = "com.nendo.argosy.extra.IGDB_ID"
    const val RA_ID = "com.nendo.argosy.extra.RA_ID"
    const val SESSION_START_MS = "com.nendo.argosy.extra.SESSION_START_MS"
    const val HARDCORE = "com.nendo.argosy.extra.HARDCORE"

    const val CURRENT_VERSION = 1
}

data class NowPlaying(
    val sourcePackage: String,
    val title: String,
    val platformSlug: String? = null,
    val romPath: String? = null,
    val emulatorPackage: String? = null,
    val rommId: Long? = null,
    val igdbId: Long? = null,
    val raId: Long? = null,
    val sessionStartMillis: Long? = null,
    val hardcore: Boolean = false
) {
    fun toExtras(): Map<String, Any> = buildMap {
        put(NowPlayingExtras.VERSION, NowPlayingExtras.CURRENT_VERSION)
        put(NowPlayingExtras.SOURCE_PACKAGE, sourcePackage)
        putText(NowPlayingExtras.GAME_TITLE, title)
        putText(NowPlayingExtras.PLATFORM_SLUG, platformSlug)
        putText(NowPlayingExtras.ROM_PATH, romPath)
        putText(NowPlayingExtras.EMULATOR_PACKAGE, emulatorPackage)
        rommId?.let { put(NowPlayingExtras.ROMM_ID, it) }
        igdbId?.let { put(NowPlayingExtras.IGDB_ID, it) }
        raId?.let { put(NowPlayingExtras.RA_ID, it) }
        sessionStartMillis?.takeIf { it > 0 }?.let { put(NowPlayingExtras.SESSION_START_MS, it) }
        put(NowPlayingExtras.HARDCORE, hardcore)
    }

    private fun MutableMap<String, Any>.putText(key: String, value: String?) {
        if (!value.isNullOrBlank()) put(key, value)
    }
}

fun Intent.putNowPlaying(nowPlaying: NowPlaying): Intent {
    nowPlaying.toExtras().forEach { (key, value) ->
        when (value) {
            is Int -> putExtra(key, value)
            is Long -> putExtra(key, value)
            is Boolean -> putExtra(key, value)
            is String -> putExtra(key, value)
        }
    }
    return this
}

suspend fun loadNowPlaying(
    gameId: Long,
    gameDao: GameDao,
    sessionStateStore: SessionStateStore
): NowPlaying? {
    if (gameId <= 0) return null
    val game = runCatching { gameDao.getById(gameId) }
        .onFailure { Logger.warn(TAG, "Now-playing lookup failed for game $gameId", it) }
        .getOrNull()
    if (game == null) {
        Logger.warn(TAG, "No game row for active session game $gameId; handoff skipped")
        return null
    }
    return NowPlaying(
        sourcePackage = BuildConfig.APPLICATION_ID,
        title = game.title,
        platformSlug = game.platformSlug,
        romPath = game.localPath,
        emulatorPackage = sessionStateStore.getEmulatorPackage(),
        rommId = game.rommId,
        igdbId = game.igdbId,
        raId = game.raId,
        sessionStartMillis = sessionStateStore.getSessionStartTimeMillis(),
        hardcore = sessionStateStore.isHardcore()
    )
}

private const val TAG = "NowPlayingHandoff"
