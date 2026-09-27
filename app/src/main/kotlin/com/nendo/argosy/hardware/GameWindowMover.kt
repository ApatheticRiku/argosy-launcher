package com.nendo.argosy.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.util.DisplayAffinityHelper

/**
 * Carries the running built-in game's window to another display without relaunching it.
 */
interface GameWindowMover {
    suspend fun isAvailable(): Boolean

    suspend fun moveGame(displayId: Int): Boolean
}

class ThorGameWindowMover(
    context: Context,
    private val taskMover: ThorTaskMover = ThorTaskMover()
) : GameWindowMover {

    private val appContext = context.applicationContext

    override suspend fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            DisplayAffinityHelper.isKnownDualScreenDevice() &&
            taskMover.isAvailable()

    override suspend fun moveGame(displayId: Int): Boolean {
        val activityManager = appContext.getSystemService(ActivityManager::class.java) ?: return false
        val taskId = builtInGameTask(activityManager)?.taskId ?: return false
        return taskMover.moveTask(taskId, displayId)
    }
}

internal fun builtInGameTask(activityManager: ActivityManager): ActivityManager.RecentTaskInfo? {
    val gameActivity = LibretroActivity::class.java.name
    return activityManager.appTasks.firstNotNullOfOrNull { task ->
        runCatching { task.taskInfo }.getOrNull()
            ?.takeIf { it.topActivity?.className == gameActivity }
    }
}
