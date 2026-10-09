package com.nendo.argosy.ui.conflict

import android.app.Application
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.ConflictResolutionService
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.domain.usecase.savechannel.ResolveSnapshotConflictUseCase
import com.nendo.argosy.ui.components.SaveConflictInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SnapshotChoiceRoute { LEGACY_KEEP_LOCAL, LEGACY_KEEP_SERVER, SNAPSHOT }

internal fun routeSnapshotChoice(choice: SnapshotConflictChoice, legacyResolves: Boolean): SnapshotChoiceRoute =
    when (choice) {
        SnapshotConflictChoice.MINE ->
            if (legacyResolves) SnapshotChoiceRoute.LEGACY_KEEP_LOCAL else SnapshotChoiceRoute.SNAPSHOT
        SnapshotConflictChoice.THEIRS ->
            if (legacyResolves) SnapshotChoiceRoute.LEGACY_KEEP_SERVER else SnapshotChoiceRoute.SNAPSHOT
        SnapshotConflictChoice.BRANCH, SnapshotConflictChoice.REVERT -> SnapshotChoiceRoute.SNAPSHOT
    }

internal fun offersSnapshotChoices(parkedDowngrade: Boolean, snapshotServer: Boolean): Boolean =
    !parkedDowngrade && snapshotServer

class SaveConflictPrompts @Inject constructor(
    private val application: Application,
    private val playSessionTracker: PlaySessionTracker,
    private val gameRepository: GameRepository,
    private val pendingConflictDao: PendingConflictDao,
    private val conflictResolutionService: ConflictResolutionService,
    private val saveSyncRepository: SaveSyncRepository,
    private val syncQueueManager: SyncQueueManager,
    private val resolveSnapshotConflict: ResolveSnapshotConflictUseCase
) {
    private lateinit var scope: CoroutineScope

    private val _saveConflictInfo = MutableStateFlow<SaveConflictInfo?>(null)
    val saveConflictInfo: StateFlow<SaveConflictInfo?> = _saveConflictInfo.asStateFlow()

    private val _saveConflictButtonIndex = MutableStateFlow(0)
    val saveConflictButtonIndex: StateFlow<Int> = _saveConflictButtonIndex.asStateFlow()

    private val _backgroundConflictInfo = MutableStateFlow<ConflictInfo?>(null)
    val backgroundConflictInfo: StateFlow<ConflictInfo?> = _backgroundConflictInfo.asStateFlow()

    private val _backgroundConflictButtonIndex = MutableStateFlow(0)
    val backgroundConflictButtonIndex: StateFlow<Int> = _backgroundConflictButtonIndex.asStateFlow()

    private val _backgroundConflictSnapshot = MutableStateFlow(false)
    val backgroundConflictSnapshot: StateFlow<Boolean> = _backgroundConflictSnapshot.asStateFlow()

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            playSessionTracker.pendingSessionConflict.collect { event ->
                if (event == null) return@collect
                val game = gameRepository.getById(event.gameId)
                _saveConflictInfo.value = SaveConflictInfo(
                    gameId = event.gameId,
                    gameName = game?.title ?: application.getString(R.string.ui_save_conflict_unknown_game),
                    emulatorId = event.emulatorId,
                    channelName = event.channelName,
                    localTimestamp = event.localTimestamp,
                    serverTimestamp = event.serverTimestamp,
                    serverDeviceName = event.serverDeviceName,
                    conflictId = event.conflictId,
                    isHardcoreDowngrade = event.isHardcoreDowngrade,
                    snapshotConflict = offersSnapshotChoices(
                        parkedDowngrade = event.isHardcoreDowngrade,
                        snapshotServer = resolveSnapshotConflict.appliesTo(event.gameId)
                    )
                )
                _saveConflictButtonIndex.value = 0
            }
        }
        scope.launch {
            syncQueueManager.pendingConflicts.collect { conflicts ->
                val first = conflicts.firstOrNull()
                if (first != null) {
                    _backgroundConflictSnapshot.value = offersSnapshotChoices(
                        parkedDowngrade = resolveSnapshotConflict.isParkedDowngrade(first.conflictId),
                        snapshotServer = resolveSnapshotConflict.appliesTo(first.gameId)
                    )
                    _backgroundConflictInfo.value = first
                    _backgroundConflictButtonIndex.value = 0
                } else {
                    _backgroundConflictInfo.value = null
                }
            }
        }
    }

    fun dismissSaveConflict() = answerSaveConflict(ConflictResolution.SKIP)

    fun forceUploadConflictSave() = answerSaveConflict(ConflictResolution.KEEP_LOCAL)

    fun moveSaveConflictFocus(direction: Int) {
        val lastIndex = (_saveConflictInfo.value?.optionCount ?: 2) - 1
        _saveConflictButtonIndex.value = (_saveConflictButtonIndex.value + direction).coerceIn(0, lastIndex)
    }

    fun confirmSaveConflict() {
        val info = _saveConflictInfo.value ?: return
        val index = _saveConflictButtonIndex.value
        when {
            info.snapshotConflict -> SnapshotConflictChoice.entries.getOrNull(index)
                ?.let(::answerSnapshotConflict) ?: dismissSaveConflict()
            index == 0 -> dismissSaveConflict()
            else -> forceUploadConflictSave()
        }
    }

    fun answerSnapshotConflict(choice: SnapshotConflictChoice) {
        val info = _saveConflictInfo.value ?: return
        when (routeSnapshotChoice(choice, legacyResolves = info.conflictId != null)) {
            SnapshotChoiceRoute.LEGACY_KEEP_LOCAL -> return answerSaveConflict(ConflictResolution.KEEP_LOCAL)
            SnapshotChoiceRoute.LEGACY_KEEP_SERVER -> return answerSaveConflict(ConflictResolution.KEEP_SERVER)
            SnapshotChoiceRoute.SNAPSHOT -> Unit
        }
        closeSaveConflict()
        scope.launch {
            resolveSnapshotConflict(
                gameId = info.gameId,
                emulatorId = info.emulatorId,
                channelName = info.channelName,
                choice = choice,
                pendingConflictId = info.conflictId
            )
        }
    }

    fun moveBackgroundConflictFocus(direction: Int) {
        val lastIndex = if (_backgroundConflictSnapshot.value) SnapshotConflictChoice.entries.size else 2
        _backgroundConflictButtonIndex.value = (_backgroundConflictButtonIndex.value + direction).coerceIn(0, lastIndex)
    }

    fun confirmBackgroundConflict() {
        val index = _backgroundConflictButtonIndex.value
        if (_backgroundConflictSnapshot.value) {
            SnapshotConflictChoice.entries.getOrNull(index)?.let(::resolveBackgroundSnapshotConflict)
                ?: resolveBackgroundConflict(ConflictResolution.SKIP)
            return
        }
        resolveBackgroundConflict(
            when (index) {
                0 -> ConflictResolution.KEEP_LOCAL
                1 -> ConflictResolution.KEEP_SERVER
                else -> ConflictResolution.SKIP
            }
        )
    }

    fun resolveBackgroundSnapshotConflict(choice: SnapshotConflictChoice) {
        when (routeSnapshotChoice(choice, legacyResolves = true)) {
            SnapshotChoiceRoute.LEGACY_KEEP_LOCAL -> return resolveBackgroundConflict(ConflictResolution.KEEP_LOCAL)
            SnapshotChoiceRoute.LEGACY_KEEP_SERVER -> return resolveBackgroundConflict(ConflictResolution.KEEP_SERVER)
            SnapshotChoiceRoute.SNAPSHOT -> Unit
        }
        val info = _backgroundConflictInfo.value ?: return
        val conflictId = info.conflictId
        if (conflictId == null) {
            syncQueueManager.resolveConflict(info.gameId, ConflictResolution.SKIP)
        } else {
            syncQueueManager.withdrawConflict(conflictId)
        }
        scope.launch {
            resolveSnapshotConflict(
                gameId = info.gameId,
                emulatorId = null,
                channelName = info.channelName,
                choice = choice,
                pendingConflictId = conflictId
            )
        }
    }

    fun resolveBackgroundConflict(resolution: ConflictResolution) {
        val info = _backgroundConflictInfo.value ?: return
        val conflictId = info.conflictId
        if (conflictId == null) {
            syncQueueManager.resolveConflict(info.gameId, resolution)
            return
        }
        syncQueueManager.withdrawConflict(conflictId)
        scope.launch {
            val stored = pendingConflictDao.getById(conflictId) ?: return@launch
            conflictResolutionService.resolve(stored, resolution)
        }
    }

    private fun answerSaveConflict(resolution: ConflictResolution) {
        val info = _saveConflictInfo.value
        closeSaveConflict()
        if (info == null) return
        scope.launch {
            val stored = info.conflictId?.let { pendingConflictDao.getById(it) }
            if (stored != null) {
                conflictResolutionService.resolve(stored, resolution)
            } else if (resolution == ConflictResolution.KEEP_LOCAL) {
                saveSyncRepository.uploadSave(
                    gameId = info.gameId,
                    emulatorId = info.emulatorId,
                    channelName = info.channelName,
                    forceOverwrite = true
                )
            } else {
                saveSyncRepository.clearDirtyFlags(info.gameId)
            }
        }
    }

    private fun closeSaveConflict() {
        _saveConflictInfo.value = null
        _saveConflictButtonIndex.value = 0
        playSessionTracker.clearPendingSessionConflict()
    }
}
