package com.nendo.argosy.ui.common.savechannel.snapshot

import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.sync.snapshot.SnapshotChannelEntry
import com.nendo.argosy.ui.common.savechannel.SaveChannelStateHolder
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotChannelMenuTest {

    private val holder = SaveChannelStateHolder()
    private val runner = mockk<SnapshotActionRunner>(relaxed = true)
    private val delegate = SnapshotViewDelegate(holder, runner)
    private val mapper = SnapshotUiMapper(mediaUrl = { null })

    private fun showOwn(vararg labels: String, deviceChannelId: String? = null) {
        val tiles = labels.map { label ->
            mapper.tile(SnapshotChannelEntry(RomMChannel(id = "id-$label", label = label), emptyList()), deviceChannelId)
        }
        holder.state.value = holder.state.value.copy(
            snapshot = SnapshotViewState(isLoading = false, mine = tiles, stop = SnapshotStop.MineTiles)
        )
    }

    private val menuActions get() = holder.state.value.snapshot?.channelMenu?.actions

    @Test
    fun `the default channel menu offers no rename`() {
        showOwn("default", deviceChannelId = "id-default")

        delegate.openChannelActions("id-default")

        assertEquals(
            listOf(SnapshotChannelAction.SHARE, SnapshotChannelAction.DELETE),
            menuActions
        )
    }

    @Test
    fun `a default label in any case offers no rename`() {
        showOwn("Default")

        delegate.openChannelActions("id-Default")

        assertEquals(
            listOf(SnapshotChannelAction.USE_ON_DEVICE, SnapshotChannelAction.SHARE, SnapshotChannelAction.DELETE),
            menuActions
        )
    }

    @Test
    fun `a named own channel still offers rename`() {
        showOwn("Speedrun")

        delegate.openChannelActions("id-Speedrun")

        assertEquals(
            listOf(
                SnapshotChannelAction.USE_ON_DEVICE,
                SnapshotChannelAction.RENAME,
                SnapshotChannelAction.SHARE,
                SnapshotChannelAction.DELETE
            ),
            menuActions
        )
    }
}
