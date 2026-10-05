package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.repository.PreLaunchSyncResult
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a game's default save channel to [SnapshotSyncEngine] when the server speaks the snapshot
 * API and Sigil collects the game, translating the outcome into what the save sync facade returns.
 * Each call answers null when the game stays on the legacy endpoints.
 */
@Singleton
class SnapshotSyncRouter @Inject constructor(
    private val engine: SnapshotSyncEngine,
    private val sigilSaveHandler: SigilSaveHandler
) {
    private suspend fun handles(gameId: Long, emulatorId: String, channelName: String?): Boolean =
        SaveSyncApiClient.namedChannelOrNull(channelName) == null && engine.isEligible(gameId, emulatorId)

    suspend fun preLaunch(gameId: Long, emulatorId: String, channelName: String?): PreLaunchSyncResult? {
        if (!handles(gameId, emulatorId, channelName)) return null
        return when (val result = engine.sync(gameId, emulatorId)) {
            SnapshotSyncResult.NotEligible -> null
            SnapshotSyncResult.NoConnection -> PreLaunchSyncResult.NoConnection
            is SnapshotSyncResult.Conflict -> PreLaunchSyncResult.LocalModified(
                localSavePath = sigilSaveHandler.route(gameId, emulatorId)?.root.orEmpty(),
                serverTimestamp = timestampOf(result),
                channelName = channelName
            )
            is SnapshotSyncResult.Failed -> {
                Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | pre-launch sync failed, launching the local save | ${result.reason}")
                PreLaunchSyncResult.LocalIsNewer
            }
            SnapshotSyncResult.UpToDate, is SnapshotSyncResult.Pushed, is SnapshotSyncResult.Applied,
            is SnapshotSyncResult.HardcoreDowngrade -> PreLaunchSyncResult.LocalIsNewer
        }
    }

    suspend fun upload(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        keepLocal: Boolean,
        isHardcore: Boolean
    ): SaveSyncResult? {
        if (!handles(gameId, emulatorId, channelName)) return null
        val result = if (keepLocal) {
            engine.keepLocal(gameId, emulatorId, isHardcore)
        } else {
            engine.sync(gameId, emulatorId, isHardcore)
        }
        return toSaveSyncResult(gameId, result)
    }

    suspend fun download(gameId: Long, emulatorId: String, channelName: String?): SaveSyncResult? {
        if (!handles(gameId, emulatorId, channelName)) return null
        return toSaveSyncResult(gameId, engine.keepServer(gameId, emulatorId))
    }

    private fun toSaveSyncResult(gameId: Long, result: SnapshotSyncResult): SaveSyncResult? = when (result) {
        SnapshotSyncResult.NotEligible -> null
        SnapshotSyncResult.NoConnection -> SaveSyncResult.NotConfigured
        SnapshotSyncResult.UpToDate -> SaveSyncResult.Success(noOp = true)
        is SnapshotSyncResult.Pushed -> SaveSyncResult.Success()
        is SnapshotSyncResult.Applied -> SaveSyncResult.Success()
        is SnapshotSyncResult.Conflict -> SaveSyncResult.Conflict(
            gameId = gameId,
            localTimestamp = Instant.now(),
            serverTimestamp = timestampOf(result),
            serverDeviceName = result.current?.device?.takeIf { it.isOwn }?.name
        )
        is SnapshotSyncResult.HardcoreDowngrade ->
            SaveSyncResult.Error("the server's save is from a hardcore session; keeping this save needs approval")
        is SnapshotSyncResult.Failed -> SaveSyncResult.Error(result.reason)
    }

    private fun timestampOf(result: SnapshotSyncResult.Conflict): Instant =
        result.current?.createdAt?.let { runCatching { SaveSyncApiClient.parseTimestamp(it) }.getOrNull() } ?: Instant.now()

    private companion object {
        const val TAG = "SnapshotSyncRouter"
    }
}
