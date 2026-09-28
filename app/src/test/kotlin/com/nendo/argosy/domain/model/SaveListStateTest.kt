package com.nendo.argosy.domain.model

import com.nendo.argosy.data.local.entity.SaveSyncEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveListStateTest {

    private val synced = SaveSyncEntity.STATUS_SYNCED
    private val localNewer = SaveSyncEntity.STATUS_LOCAL_NEWER
    private val pending = SaveSyncEntity.STATUS_PENDING_UPLOAD
    private val serverNewer = SaveSyncEntity.STATUS_SERVER_NEWER
    private val conflict = SaveSyncEntity.STATUS_CONFLICT
    private val hardcore = SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION

    @Test
    fun `each stored status maps to its list state`() {
        assertEquals(SaveListState.SYNCED, SaveListState.of(synced))
        assertEquals(SaveListState.LOCAL_AHEAD, SaveListState.of(localNewer))
        assertEquals(SaveListState.LOCAL_AHEAD, SaveListState.of(pending))
        assertEquals(SaveListState.SERVER_AHEAD, SaveListState.of(serverNewer))
        assertEquals(SaveListState.NEEDS_ATTENTION, SaveListState.of(conflict))
        assertEquals(SaveListState.NEEDS_ATTENTION, SaveListState.of(hardcore))
        assertNull(SaveListState.of("SOMETHING_ELSE"))
    }

    @Test
    fun `attention outranks server newer in either order`() {
        assertEquals(SaveListState.NEEDS_ATTENTION, SaveListState.worstOf(listOf(conflict, serverNewer)))
        assertEquals(SaveListState.NEEDS_ATTENTION, SaveListState.worstOf(listOf(serverNewer, hardcore)))
    }

    @Test
    fun `server newer outranks local newer in either order`() {
        assertEquals(SaveListState.SERVER_AHEAD, SaveListState.worstOf(listOf(serverNewer, localNewer)))
        assertEquals(SaveListState.SERVER_AHEAD, SaveListState.worstOf(listOf(pending, serverNewer)))
    }

    @Test
    fun `local newer outranks synced in either order`() {
        assertEquals(SaveListState.LOCAL_AHEAD, SaveListState.worstOf(listOf(synced, localNewer)))
        assertEquals(SaveListState.LOCAL_AHEAD, SaveListState.worstOf(listOf(pending, synced)))
    }

    @Test
    fun `equal states collapse to that state`() {
        assertEquals(SaveListState.SYNCED, SaveListState.worstOf(listOf(synced, synced)))
    }

    @Test
    fun `unknown statuses are ignored and alone give nothing`() {
        assertEquals(SaveListState.SYNCED, SaveListState.worstOf(listOf("UNKNOWN", synced)))
        assertNull(SaveListState.worstOf(listOf("UNKNOWN")))
        assertNull(SaveListState.worstOf(emptyList()))
    }

    @Test
    fun `worst is taken per game`() {
        val rows = listOf(
            1L to synced,
            1L to serverNewer,
            2L to localNewer,
            3L to "UNKNOWN"
        )

        assertEquals(
            mapOf(1L to SaveListState.SERVER_AHEAD, 2L to SaveListState.LOCAL_AHEAD),
            SaveListState.worstByGame(rows)
        )
    }
}
