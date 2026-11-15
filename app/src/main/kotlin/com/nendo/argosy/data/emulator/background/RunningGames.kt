package com.nendo.argosy.data.emulator.background

import com.nendo.argosy.data.repository.PlayStatsRepository
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What [RunningGames] last read. [playedThisRunMs] is keyed by task id, the play time counted
 * from the start of that app's run; [closingTaskIds] are the tasks a close was asked of and that
 * have not gone yet; [snapshots] holds each task's last frame, where one could be read.
 */
data class RunningGamesSnapshot(
    val apps: List<RunningApp> = emptyList(),
    val playedThisRunMs: Map<Int, Long> = emptyMap(),
    val closingTaskIds: Set<Int> = emptySet(),
    val snapshots: Map<Int, TaskSnapshot> = emptyMap()
)

/**
 * The one main-process source of which apps and games are running. Every reader of
 * [BackgroundGames.refresh] outside this class goes through [refresh] here, so the drawer badge,
 * the tile marks, the status strip and the Resume list all show one list. The list is re-read
 * every [POLL_INTERVAL_MS] only while a caller is inside [watch].
 */
@Singleton
class RunningGames @Inject constructor(
    private val backgroundGames: BackgroundGames,
    private val playStatsRepository: PlayStatsRepository,
    private val taskSnapshots: TaskSnapshots
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val refreshLock = Mutex()
    private val watchers = MutableStateFlow(0)
    private val closingTaskIds = MutableStateFlow<Set<Int>>(emptySet())
    private var started = false

    private val _state = MutableStateFlow(RunningGamesSnapshot())
    val state: StateFlow<RunningGamesSnapshot> = _state.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            watchers.map { it > 0 }.distinctUntilChanged().collectLatest { watched ->
                if (!watched) return@collectLatest
                while (isActive) {
                    refresh()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        scope.launch {
            backgroundGames.recorded.collect { refresh() }
        }
    }

    /**
     * Keeps the list polled until the calling coroutine is cancelled.
     */
    suspend fun watch() {
        watchers.update { it + 1 }
        try {
            awaitCancellation()
        } finally {
            watchers.update { it - 1 }
        }
    }

    suspend fun refresh(): List<RunningApp> = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val apps = backgroundGames.refresh()
            val played = apps.associate { app -> app.taskId to playedThisRun(app) }
            val snapshots = taskSnapshots.fetch(apps.map { it.taskId })
            _state.value = RunningGamesSnapshot(
                apps = apps,
                playedThisRunMs = played,
                closingTaskIds = closingTaskIds.value,
                snapshots = snapshots
            )
            apps
        }
    }

    /**
     * Closes the apps holding [taskIds] through [BackgroundGames.close] and returns once every one
     * is gone or [CLOSE_TIMEOUT_MS] has passed. The tasks read as closing until then.
     */
    suspend fun close(taskIds: Collection<Int>) = withContext(Dispatchers.IO) {
        val ids = taskIds.toSet()
        if (ids.isEmpty()) return@withContext
        markClosing(ids, closing = true)
        try {
            val running = backgroundGames.refresh().associateBy { it.taskId }
            ids.mapNotNull { running[it] }.forEach { backgroundGames.close(it) }
            awaitClosed(ids)
        } finally {
            markClosing(ids, closing = false)
            refresh()
        }
    }

    /**
     * [close] in this holder's own scope, for a caller that may be gone before the game is.
     */
    fun closeDetached(taskIds: Collection<Int>) {
        scope.launch { close(taskIds) }
    }

    private fun markClosing(ids: Set<Int>, closing: Boolean) {
        closingTaskIds.update { if (closing) it + ids else it - ids }
        _state.update { it.copy(closingTaskIds = closingTaskIds.value) }
    }

    private suspend fun awaitClosed(taskIds: Set<Int>) {
        val deadline = System.currentTimeMillis() + CLOSE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (backgroundGames.refresh().none { it.taskId in taskIds }) return
            delay(CLOSE_POLL_MS)
        }
    }

    private suspend fun playedThisRun(app: RunningApp): Long {
        val gameId = app.game?.gameId ?: return 0L
        val startedAt = app.runStartedAtMillis ?: return 0L
        return playStatsRepository.activePlayMsSince(gameId, Instant.ofEpochMilli(startedAt))
    }

    private companion object {
        const val TAG = "RunningGames"
        const val POLL_INTERVAL_MS = 10_000L
        const val CLOSE_TIMEOUT_MS = 20_000L
        const val CLOSE_POLL_MS = 250L
    }
}
