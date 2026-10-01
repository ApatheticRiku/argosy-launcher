package com.nendo.argosy.data.repository

import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The once-per-process library pointer repair. Callers of [start] and [await] share one pass at a
 * time, and a completed pass is never rerun. A pass that found storage not ready does not count as
 * completed, so the next caller tries again.
 */
@Singleton
class LibraryPointerRepair @Inject constructor(
    private val gameRepository: GameRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "LibraryPointerRepair")
    private val mutex = Mutex()

    @Volatile
    private var completed = false

    fun start() {
        scope.launch { await() }
    }

    suspend fun await() {
        if (completed) return
        mutex.withLock {
            if (completed) return
            val startedAt = System.currentTimeMillis()
            try {
                completed = gameRepository.repairLibraryPointers()
                if (completed) {
                    Logger.info(TAG, "Library pointer repair finished in ${System.currentTimeMillis() - startedAt}ms")
                } else {
                    Logger.warn(TAG, "Library pointer repair deferred: storage not ready")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                completed = true
                Logger.error(TAG, "Library pointer repair failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "LibraryPointerRepair"
    }
}
