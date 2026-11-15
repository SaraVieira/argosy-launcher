package com.nendo.argosy.fork

import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.util.Logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A Cocoon-format `launch_debug.log` in the `Argosy` folder of primary storage, holding one block
 * for the most recent game launch.
 *
 * The block's launch intent is Argosy's own `argosy://launch?game_id=` deep link.
 */
object CocoonLaunchLog {
    const val FOLDER = "Argosy"
    const val FILENAME = "launch_debug.log"

    private const val TAG = "CocoonLaunchLog"
    private const val LAUNCH_URI_PREFIX = "argosy://launch?game_id="
    private const val BAR = "══════"
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun folderPath(): String = "${StoragePathUtils.primaryExternalRoot}/$FOLDER"

    fun write(fileAccessLayer: FileAccessLayer, game: GameEntity, launchedAt: Instant) {
        val lines = lines(
            title = game.title,
            romPath = game.localPath,
            gameId = game.id,
            sourcePackage = BuildConfig.APPLICATION_ID,
            activityClass = ArgosyActivity::class.java.name,
            launchedAt = LocalDateTime.ofInstant(launchedAt, ZoneId.systemDefault())
        )
        if (lines.isEmpty()) return
        val folder = folderPath()
        val written = runCatching {
            fileAccessLayer.mkdirs(folder)
            fileAccessLayer.writeBytes("$folder/$FILENAME", lines.joinToString("\n", postfix = "\n").toByteArray())
        }.onFailure { Logger.warn(TAG, "Launch log write failed for game ${game.id}", it) }
            .getOrDefault(false)
        if (!written) Logger.warn(TAG, "Launch log not written to $folder/$FILENAME")
    }

    internal fun lines(
        title: String,
        romPath: String?,
        gameId: Long,
        sourcePackage: String,
        activityClass: String,
        launchedAt: LocalDateTime
    ): List<String> {
        val name = title.replace(Regex("\\s+"), " ").trim()
        if (name.isEmpty()) return emptyList()
        val path = romPath?.trim()?.takeIf { it.isNotEmpty() && it.lines().size == 1 }
        val stamp = "[${launchedAt.format(timestampFormat)}]"
        val component = "$sourcePackage/$activityClass"
        return buildList {
            add("$stamp [----] $BAR Launch: $name $BAR")
            if (path != null) add("$stamp [INFO] Game: $name | File: ${path.substringAfterLast('/')}")
            add("$stamp [INFO] Player: Argosy ($component)")
            if (path != null) add("$stamp [INFO] {file.path} → $path")
            add(
                "$stamp [INFO] Intent: action=android.intent.action.VIEW, " +
                    "data=$LAUNCH_URI_PREFIX$gameId, component=ComponentInfo{$component}"
            )
            add("$stamp [INFO] Intent extras: none")
            add("$stamp [INFO] Launch SUCCESS — taskId=null")
        }
    }
}
