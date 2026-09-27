package com.nendo.argosy.hardware

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "ThorTaskMover"

/**
 * Root task mover over the AYN Thor's `PServerBinder`: sends only the `id` probe and
 * `am display move-stack <taskId> <displayId>`, which on Android 13 calls
 * `IActivityTaskManager.moveRootTaskToDisplay`, reparenting the live task without a relaunch.
 */
class ThorTaskMover {

    @Volatile
    private var available: Boolean? = null

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        available ?: execute(PROBE_COMMAND).also { available = it }
    }

    suspend fun moveTask(taskId: Int, displayId: Int): Boolean = withContext(Dispatchers.IO) {
        if (taskId < 0 || displayId < 0) return@withContext false
        if (!isAvailable()) return@withContext false
        execute(String.format(Locale.ROOT, MOVE_TEMPLATE, taskId, displayId))
    }

    private fun execute(command: String): Boolean {
        val binder = serviceBinder() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, RUN_MODE))
            binder.transact(TRANSACTION_RUN, data, reply, 0)
        } catch (e: Exception) {
            Log.w(TAG, "PServerBinder transaction failed", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun serviceBinder(): IBinder? = try {
        Class.forName(SERVICE_MANAGER_CLASS)
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE_NAME) as? IBinder
    } catch (e: Exception) {
        Log.w(TAG, "PServerBinder lookup failed", e)
        null
    }

    private companion object {
        const val SERVICE_MANAGER_CLASS = "android.os.ServiceManager"
        const val SERVICE_NAME = "PServerBinder"
        const val TRANSACTION_RUN = 0
        const val RUN_MODE = "0"
        const val PROBE_COMMAND = "id"
        const val MOVE_TEMPLATE = "am display move-stack %d %d"
    }
}
