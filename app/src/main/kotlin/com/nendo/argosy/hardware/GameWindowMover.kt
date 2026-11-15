package com.nendo.argosy.hardware

import android.os.Build
import com.nendo.argosy.util.DisplayAffinityHelper

/**
 * Carries a running app's task to another display without relaunching it.
 */
interface GameWindowMover {
    suspend fun isAvailable(): Boolean

    suspend fun moveTask(taskId: Int, displayId: Int): Boolean
}

class ThorGameWindowMover(
    private val taskMover: ThorTaskMover = ThorTaskMover()
) : GameWindowMover {

    override suspend fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            DisplayAffinityHelper.isKnownDualScreenDevice() &&
            taskMover.isAvailable()

    override suspend fun moveTask(taskId: Int, displayId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return taskMover.moveTask(taskId, displayId)
    }
}
