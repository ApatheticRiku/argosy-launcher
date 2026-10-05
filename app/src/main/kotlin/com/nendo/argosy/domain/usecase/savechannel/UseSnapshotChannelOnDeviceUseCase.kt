package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotActionResult
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelService
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncEngine
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncResult
import javax.inject.Inject

/**
 * Makes a RomM channel the one a game syncs on this device. The save on disk is synced to the
 * channel it belonged to first, then the game switches channel, states included, and takes the
 * new channel's current.
 */
class UseSnapshotChannelOnDeviceUseCase @Inject constructor(
    private val activeSaveRepository: ActiveSaveRepository,
    private val activateSaveChannel: ActivateSaveChannelUseCase,
    private val channelService: SnapshotChannelService,
    private val engine: SnapshotSyncEngine
) {
    suspend operator fun invoke(
        gameId: Long,
        emulatorId: String,
        romFileId: Long,
        channel: RomMChannel
    ): SnapshotActionResult {
        val previous = activeSaveRepository.getActiveChannel(gameId)
        when (val synced = engine.sync(gameId, emulatorId, previous)) {
            is SnapshotSyncResult.Conflict -> return SnapshotActionResult.Stale
            is SnapshotSyncResult.HardcoreDowngrade -> return SnapshotActionResult.HardcoreDowngrade
            is SnapshotSyncResult.Failed -> return SnapshotActionResult.Failed(synced.reason)
            else -> Unit
        }
        val argosyChannel = channelService.argosyChannelOf(channel)
        activateSaveChannel(gameId, argosyChannel)
        channelService.rememberDeviceChannel(gameId, argosyChannel, channel.id, romFileId)
        return when (val applied = engine.keepServer(gameId, emulatorId, argosyChannel)) {
            is SnapshotSyncResult.Failed -> SnapshotActionResult.Failed(applied.reason)
            else -> SnapshotActionResult.Done
        }
    }
}
