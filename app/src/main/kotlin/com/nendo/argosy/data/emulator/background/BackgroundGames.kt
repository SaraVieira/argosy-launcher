package com.nendo.argosy.data.emulator.background

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.view.Display
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.streaming.StreamPhase
import com.nendo.argosy.data.streaming.StreamSessionManager
import java.io.File
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An app with a task on screen or in the background. [game] is set when the app is an emulator
 * still holding the game a session of ours ended in. [runStartedAtMillis] is when the process
 * holding it started, the start of the run its play time is counted from.
 */
data class RunningApp(
    val taskId: Int,
    val packageName: String,
    val displayId: Int,
    val rssKb: Long?,
    val game: BackgroundGame?,
    val runStartedAtMillis: Long?,
    val hardcore: Boolean,
    val slotIndex: Int?,
    val platformSlug: String? = null
)

/**
 * The apps and games left running. External apps are listed only on a device with a root shell,
 * where the task list and process table can be read; a hosted stream is listed everywhere.
 * [KeepAlivePolicy] decides which games stay alive.
 */
@Singleton
class BackgroundGames @Inject constructor(
    private val application: Application,
    private val store: BackgroundGameStore,
    private val playSessionTracker: PlaySessionTracker,
    private val keepAlivePolicy: KeepAlivePolicy,
    private val gameLauncher: GameLauncher,
    private val appsRepository: AppsRepository,
    private val streamSessions: dagger.Lazy<StreamSessionManager>
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "BackgroundGames")
    private var started = false

    private val _recorded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recorded: SharedFlow<Unit> = _recorded.asSharedFlow()

    val isSupported: Boolean get() = keepAlivePolicy.isSupported

    suspend fun keepsAlive(emulatorPackage: String): Boolean = keepAlivePolicy.keepsAlive(emulatorPackage)

    fun start() {
        if (started) return
        started = true
        scope.launch { keepAlivePolicy.grantUsageAccessOnRoot() }
        scope.launch { restoreTransitionScale() }
        scope.launch {
            var previous: ActiveSession? = null
            playSessionTracker.activeSession.collect { current ->
                val ended = previous
                previous = current
                if (current == null && ended != null) remember(ended)
                if (current != null) store.remove(current.emulatorPackage)
            }
        }
    }

    /**
     * Records the game [session] leaves running in its emulator, when that emulator stays alive.
     * Every session end is recorded on its own; a caller that needs the record at once can call
     * this directly.
     */
    suspend fun remember(session: ActiveSession) {
        if (session.isStream) return
        if (!keepsAlive(session.emulatorPackage)) return
        val pid = read()?.processes?.get(session.emulatorPackage)?.pid ?: return
        store.put(
            BackgroundGame(
                emulatorPackage = session.emulatorPackage,
                gameId = session.gameId,
                variantFileId = session.variantFileId,
                pid = pid,
                endedAtMillis = System.currentTimeMillis()
            )
        )
        Logger.debug(TAG, "kept ${session.emulatorPackage} pid=$pid for gameId=${session.gameId}")
        _recorded.tryEmit(Unit)
    }

    /**
     * Every app with a task that the player could open from a launcher, every Argosy install excluded, joined
     * to the game its emulator holds, then the stream this device hosts. Off root only the stream
     * is listed. Records whose emulator process is gone or was restarted are dropped from the store
     * here.
     */
    suspend fun refresh(): List<RunningApp> = externalApps() + listOfNotNull(streamApp())

    private suspend fun externalApps(): List<RunningApp> {
        if (!isSupported) return emptyList()
        val snapshot = read() ?: return emptyList()
        val games = liveRecords(snapshot)
        val now = System.currentTimeMillis()
        return snapshot.tasks
            .filter { !appsRepository.isArgosy(it.packageName) && isLaunchable(it.packageName) }
            .distinctBy { it.packageName }
            .map { task ->
                val process = snapshot.processes[task.packageName]
                RunningApp(
                    taskId = task.taskId,
                    packageName = task.packageName,
                    displayId = task.displayId,
                    rssKb = process?.rssKb,
                    game = games[task.packageName],
                    runStartedAtMillis = process?.let { now - it.elapsedMs },
                    hardcore = false,
                    slotIndex = null
                )
            }
    }

    private fun isLaunchable(packageName: String): Boolean {
        val packageManager = application.packageManager
        return packageManager.getLaunchIntentForPackage(packageName) != null ||
            packageManager.getLeanbackLaunchIntentForPackage(packageName) != null
    }

    private fun streamApp(): RunningApp? {
        val session = streamSessions.get().session.value
            ?.takeIf { it.isHost && it.phase != StreamPhase.ENDED } ?: return null
        val taskId = session.taskId ?: return null
        return RunningApp(
            taskId = taskId,
            packageName = StreamSessionManager.STREAM_PACKAGE,
            displayId = Display.INVALID_DISPLAY,
            rssKb = null,
            game = BackgroundGame(
                emulatorPackage = StreamSessionManager.STREAM_PACKAGE,
                gameId = session.target.gameId,
                variantFileId = null,
                pid = Process.myPid(),
                endedAtMillis = 0L
            ),
            runStartedAtMillis = session.startedAtMillis,
            hardcore = false,
            slotIndex = null,
            platformSlug = session.target.platform
        )
    }

    /**
     * The running app holding [gameId] (and [variantFileId]) in [emulatorPackage], or null when
     * that emulator is not running it.
     */
    suspend fun liveGame(gameId: Long, variantFileId: Long?, emulatorPackage: String): RunningApp? =
        liveApp(emulatorPackage)?.takeIf { it.game?.gameId == gameId && it.game.variantFileId == variantFileId }

    /**
     * The app [packageName] runs, for an external emulator.
     */
    suspend fun liveApp(packageName: String): RunningApp? =
        if (!isSupported) null
        else externalApps().firstOrNull { it.packageName == packageName }

    /**
     * Brings [taskId] to the front so the app resumes where it was, with no transition: it replaces
     * whatever Argosy last drew. Another app's task falls back to the system's transition when the
     * root shell cannot switch transitions off. False when the system refused.
     */
    suspend fun bringToFront(taskId: Int): Boolean = withContext(Dispatchers.IO) {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val own = ownTask(activityManager, taskId)
        if (own != null && startWithoutTransition(own, taskId)) return@withContext true
        if (own == null) return@withContext withTransitionsOff { moveTaskToFront(activityManager, taskId) }
        moveTaskToFront(activityManager, taskId)
    }

    private fun moveTaskToFront(activityManager: ActivityManager, taskId: Int): Boolean = try {
        activityManager.moveTaskToFront(taskId, 0)
        true
    } catch (e: SecurityException) {
        Logger.warn(TAG, "moveTaskToFront refused for task $taskId", e)
        false
    }

    suspend fun <T> withTransitionsOff(switch: () -> T): T = withContext(Dispatchers.IO) {
        if (!RootShell.isAvailable) return@withContext switch()
        val result = RootShell.run(
            application,
            "settings get global $TRANSITION_SCALE && settings put global $TRANSITION_SCALE 0"
        )
        if (result?.succeeded != true) return@withContext switch()
        val previous = result.output.firstOrNull()?.trim()?.toFloatOrNull() ?: DEFAULT_TRANSITION_SCALE
        if (previous == 0f) return@withContext switch()
        if (!transitionScaleRestore.exists()) transitionScaleRestore.writeText(previous.toString())
        val outcome = switch()
        scope.launch {
            delay(TRANSITION_RESTORE_DELAY_MS)
            restoreTransitionScale()
        }
        outcome
    }

    private val transitionScaleRestore: File get() = File(application.filesDir, TRANSITION_RESTORE_FILE)

    private fun restoreTransitionScale() {
        val previous = transitionScaleRestore.takeIf { it.exists() }?.readText()?.trim()?.toFloatOrNull() ?: return
        if (RootShell.run(application, "settings put global $TRANSITION_SCALE $previous")?.succeeded == true) {
            transitionScaleRestore.delete()
        }
    }

    private fun ownTask(activityManager: ActivityManager, taskId: Int): ActivityManager.AppTask? =
        activityManager.appTasks.firstOrNull { task -> runCatching { task.taskInfo.taskId }.getOrNull() == taskId }

    private fun startWithoutTransition(task: ActivityManager.AppTask, taskId: Int): Boolean = try {
        val root = Intent(task.taskInfo.baseIntent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        application.startActivity(root)
        true
    } catch (e: RuntimeException) {
        Logger.warn(TAG, "task $taskId would not restart in place", e)
        false
    }

    /**
     * Closes [app]. A hosted stream saves and ends; an external app is force-stopped.
     */
    suspend fun close(app: RunningApp) {
        if (app.packageName == StreamSessionManager.STREAM_PACKAGE) {
            streamSessions.get().saveAndExit()
            return
        }
        store.remove(app.packageName)
        gameLauncher.forceStopEmulator(app.packageName)
    }

    private fun liveRecords(snapshot: Snapshot): Map<String, BackgroundGame> {
        val live = mutableMapOf<String, BackgroundGame>()
        for (record in store.all()) {
            if (snapshot.processes[record.emulatorPackage]?.pid == record.pid) {
                live[record.emulatorPackage] = record
            } else {
                store.remove(record.emulatorPackage)
            }
        }
        return live
    }

    private data class Snapshot(val tasks: List<RunningTask>, val processes: Map<String, ProcessStat>)

    private suspend fun read(): Snapshot? = withContext(Dispatchers.IO) {
        val result = RootShell.run(application, "am stack list\necho $PS_MARKER\nps -A -o PID,RSS,ETIME,NAME")
            ?.takeIf { it.succeeded }
            ?: return@withContext null
        val split = result.output.indexOf(PS_MARKER)
        if (split < 0) return@withContext null
        Snapshot(
            tasks = RunningTaskParser.parseTasks(result.output.subList(0, split)),
            processes = RunningTaskParser.parseProcesses(result.output.subList(split + 1, result.output.size))
        )
    }

    private companion object {
        const val TAG = "BackgroundGames"
        const val TRANSITION_SCALE = "transition_animation_scale"
        const val TRANSITION_RESTORE_FILE = "transition_scale_restore"
        const val TRANSITION_RESTORE_DELAY_MS = 1_000L
        const val DEFAULT_TRANSITION_SCALE = 1f
        const val PS_MARKER = "--argosy-ps--"
    }
}
