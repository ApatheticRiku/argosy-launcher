package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.sync.snapshot.SnapshotLibrary
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveChannelStateHolder @Inject constructor() {
    val state = MutableStateFlow(SaveChannelState())
    var currentGameId: Long = 0
    var rawEntries: List<UnifiedSaveEntry> = emptyList()
    var pendingSaveStatusChanged: ((SaveStatusEvent) -> Unit)? = null
    val snapshotLibrary = MutableStateFlow<SnapshotLibrary?>(null)
    val snapshotHistories = MutableStateFlow<Map<String, List<RomMSnapshot>>>(emptyMap())

    /**
     * Save status changes made away from the game detail screen, for the game detail screen to
     * apply when it is next on top.
     */
    val saveStatusEvents = MutableSharedFlow<SaveStatusEvent>(extraBufferCapacity = 1)
}
