package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope

class SnapshotViewActions(
    private val delegate: SnapshotViewDelegate,
    private val scope: CoroutineScope,
    private val onSaveStatusChanged: (SaveStatusEvent) -> Unit
) {
    fun tapNewChannel() = delegate.tapNewChannel()

    fun tapTile(isMine: Boolean, index: Int) = delegate.tapTile(scope, isMine, index)

    fun longPressTile(isMine: Boolean, index: Int) = delegate.longPressTile(isMine, index)

    fun tapCard(index: Int) = delegate.tapCard(scope, index)

    fun tapBackup(index: Int) = delegate.tapBackup(index)

    fun tapOverlayRow(index: Int) = delegate.tapOverlayRow(scope, index, onSaveStatusChanged)

    fun closeOverlay() {
        delegate.back()
    }

    fun updateLabelText(text: String) = delegate.updateLabelText(text)

    fun confirmLabel() = delegate.confirmLabel(scope)

    fun dismissLabel() = delegate.dismissLabel()

    fun confirmShare() = delegate.confirmShare(scope)

    fun confirmDelete() = delegate.confirmDelete(scope)

    fun confirmHardcore() = delegate.confirmHardcore(scope)

    fun dismissConfirm() = delegate.dismissConfirm()
}
