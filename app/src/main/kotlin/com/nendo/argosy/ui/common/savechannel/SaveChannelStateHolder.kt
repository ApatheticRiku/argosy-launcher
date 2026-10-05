package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.sync.snapshot.SnapshotLibrary
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveChannelStateHolder @Inject constructor() {
    val state = MutableStateFlow(SaveChannelState())
    var currentGameId: Long = 0
    var rawEntries: List<UnifiedSaveEntry> = emptyList()
    var pendingSaveStatusChanged: ((SaveStatusEvent) -> Unit)? = null
    var snapshotLibrary: SnapshotLibrary? = null
    var snapshotHistories: Map<String, List<RomMSnapshot>> = emptyMap()
}
