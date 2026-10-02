package com.nendo.argosy.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Barrier between session end and anything that reads or overwrites a save on disk.
 *
 * Orphan recovery completes [markComplete] once per process. A session end, live or recovered,
 * holds the claim from [tryClaimSessionEnd] until [releaseSessionEnd]. [awaitSettled] returns once
 * both are clear, so a launch never places a cached version over progress that is still being
 * captured.
 */
@Singleton
class SaveRecoveryGate @Inject constructor() {
    private val recovered = CompletableDeferred<Unit>()
    private val sessionEnding = MutableStateFlow(false)

    fun markComplete() {
        recovered.complete(Unit)
    }

    suspend fun await() {
        recovered.await()
    }

    fun tryClaimSessionEnd(): Boolean = sessionEnding.compareAndSet(expect = false, update = true)

    fun releaseSessionEnd() {
        sessionEnding.value = false
    }

    suspend fun awaitSettled() {
        recovered.await()
        sessionEnding.first { !it }
    }
}
