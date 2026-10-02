package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.remote.romm.RomMClientSaveState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMSyncCompletePayload
import com.nendo.argosy.data.remote.romm.RomMSyncNegotiatePayload
import com.nendo.argosy.data.remote.romm.RomMSyncOperation
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NegotiatorSaveSyncStrategy @Inject constructor(
    private val connectionManager: RomMConnectionManager
) : SaveSyncStrategy {

    override suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan =
        negotiate(localInventory, romIds = null) ?: ReconcilePlan.EMPTY

    /**
     * Negotiate scoped to one game. Null when the server could not answer, which callers read as
     * an unknown server state and never as "no saves".
     */
    suspend fun planForGame(localInventory: List<LocalSaveState>, romId: Long): ReconcilePlan? =
        negotiate(localInventory.filter { it.romId == romId }, romIds = listOf(romId))
            ?.let { plan -> plan.copy(operations = plan.operations.filter { it.romId == romId }) }

    private suspend fun negotiate(localInventory: List<LocalSaveState>, romIds: List<Long>?): ReconcilePlan? {
        val api = connectionManager.getApi() ?: run {
            Logger.debug(TAG, "negotiate: no api")
            return null
        }
        val deviceId = connectionManager.getDeviceId() ?: run {
            Logger.debug(TAG, "negotiate: no deviceId")
            return null
        }

        val payload = RomMSyncNegotiatePayload(
            deviceId = deviceId,
            saves = localInventory.map {
                RomMClientSaveState(
                    romId = it.romId,
                    fileName = it.fileName,
                    slot = it.slot,
                    emulator = it.emulator,
                    contentHash = it.contentHash,
                    updatedAt = it.updatedAt,
                    fileSizeBytes = it.fileSizeBytes
                )
            },
            romIds = romIds
        )

        val response = try {
            api.negotiateSync(payload)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "negotiate failed", e)
            return null
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "negotiate: server returned ${response.code()}")
            return null
        }
        val body = response.body() ?: return null

        Logger.info(
            TAG,
            "planReconcile: sessionId=${body.sessionId} romIds=$romIds upload=${body.totalUpload} download=${body.totalDownload} conflict=${body.totalConflict} no_op=${body.totalNoOp}"
        )

        val plan = ReconcilePlan(
            sessionId = body.sessionId,
            operations = body.operations.map { it.toReconcileOperation() }
        )
        plan.operations.forEach {
            Logger.debug(
                TAG,
                "planReconcile: sessionId=${body.sessionId} romId=${it.romId} " +
                    "slot=${it.slot} action=${it.action} reason=${it.reason}"
            )
        }
        return plan
    }

    override suspend fun completeSession(
        sessionId: Long,
        operationsCompleted: Int,
        operationsFailed: Int
    ): CompleteOutcome {
        val api = connectionManager.getApi() ?: return CompleteOutcome.RETRY_LATER
        return try {
            val response = api.completeSyncSession(
                sessionId,
                RomMSyncCompletePayload(
                    operationsCompleted = operationsCompleted,
                    operationsFailed = operationsFailed
                )
            )
            val code = response.code()
            when {
                response.isSuccessful -> {
                    Logger.info(TAG, "completeSession: $sessionId done | ok=$operationsCompleted fail=$operationsFailed")
                    CompleteOutcome.ACCEPTED
                }
                code == 404 || code == 410 || code == 409 -> {
                    Logger.info(TAG, "completeSession: $sessionId already finalized server-side (HTTP $code), dropping local rows")
                    CompleteOutcome.ALREADY_FINALIZED
                }
                code in 400..499 -> {
                    Logger.error(TAG, "completeSession: $sessionId rejected with HTTP $code; dropping local rows to avoid zombie")
                    CompleteOutcome.ALREADY_FINALIZED
                }
                else -> {
                    Logger.warn(TAG, "completeSession: $sessionId server error HTTP $code, will retry")
                    CompleteOutcome.RETRY_LATER
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "completeSession: $sessionId failed", e)
            CompleteOutcome.RETRY_LATER
        }
    }

    companion object {
        private const val TAG = "NegotiatorSaveSyncStrategy"
    }
}

private fun RomMSyncOperation.toReconcileOperation(): ReconcileOperation = ReconcileOperation(
    action = when (action) {
        "upload" -> ReconcileAction.UPLOAD
        "download" -> ReconcileAction.DOWNLOAD
        "conflict" -> ReconcileAction.CONFLICT
        else -> ReconcileAction.NO_OP
    },
    romId = romId,
    saveId = saveId,
    fileName = fileName,
    slot = slot,
    emulator = emulator,
    reason = reason,
    serverUpdatedAt = serverUpdatedAt,
    serverContentHash = serverContentHash
)
