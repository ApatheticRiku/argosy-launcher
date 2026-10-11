package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.R
import com.nendo.argosy.ui.common.savechannel.SaveChannelStateHolder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotLabelEntryTest {

    private val holder = SaveChannelStateHolder()
    private val runner = mockk<SnapshotActionRunner>(relaxed = true)
    private val delegate = SnapshotViewDelegate(holder, runner)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun enter(mode: SnapshotLabelMode, text: String) {
        holder.state.value = holder.state.value.copy(
            snapshot = SnapshotViewState(
                labelEntry = SnapshotLabelEntryUi(mode = mode, text = text, channelId = "c-1", snapshotId = 4L, romFileId = 9L)
            )
        )
    }

    private val entry get() = holder.state.value.snapshot?.labelEntry

    @Test
    fun `naming a new channel default is refused inline`() {
        enter(SnapshotLabelMode.NEW_CHANNEL, " Default ")

        delegate.confirmLabel(scope)

        assertEquals(R.string.save_channels_label_error_reserved, entry?.error)
        verify(exactly = 0) { runner.newChannel(any(), any(), any()) }
    }

    @Test
    fun `renaming or forking to default is refused inline`() {
        enter(SnapshotLabelMode.RENAME, "DEFAULT")
        delegate.confirmLabel(scope)
        assertEquals(R.string.save_channels_label_error_reserved, entry?.error)

        enter(SnapshotLabelMode.FORK, "default")
        delegate.confirmLabel(scope)
        assertEquals(R.string.save_channels_label_error_reserved, entry?.error)

        verify(exactly = 0) { runner.rename(any(), any(), any()) }
        verify(exactly = 0) { runner.push(any(), any(), any()) }
    }

    @Test
    fun `typing clears the error and another name goes through`() {
        enter(SnapshotLabelMode.NEW_CHANNEL, "default")
        delegate.confirmLabel(scope)

        delegate.updateLabelText("Speedrun")
        assertNull(entry?.error)
        delegate.confirmLabel(scope)

        verify { runner.newChannel(scope, "Speedrun", null) }
    }
}
