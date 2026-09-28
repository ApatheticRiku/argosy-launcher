package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.repository.SiblingGroupRepository
import com.nendo.argosy.domain.model.SiblingGroup
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.domain.model.SiblingMemberKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SiblingChoiceDelegateTest {

    private lateinit var repository: SiblingGroupRepository
    private lateinit var delegate: SiblingChoiceDelegate

    private fun member(id: Long, picked: Boolean = false, shown: Boolean = false, downloaded: Boolean = false) =
        SiblingGroupMember(
            gameId = id,
            title = "Game",
            fileName = "Game ($id).sfc",
            regions = emptyList(),
            kind = SiblingMemberKind.RELEASE,
            isDownloaded = downloaded,
            isPicked = picked,
            isShown = shown
        )

    private fun group(vararg members: SiblingGroupMember) = SiblingGroup("g", 7, members.toList())

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        delegate = SiblingChoiceDelegate(repository, mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `a single-member group downloads directly without a chooser`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true))
        val downloaded = mutableListOf<Long>()

        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        assertEquals(listOf(1L), downloaded)
        assertNull(delegate.state.value)
        coVerify(exactly = 0) { repository.setPick(any()) }
    }

    @Test
    fun `a game outside every group downloads directly`() = runTest {
        coEvery { repository.groupFor(1) } returns null
        val downloaded = mutableListOf<Long>()

        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        assertEquals(listOf(1L), downloaded)
        assertNull(delegate.state.value)
    }

    @Test
    fun `an unreadable group still downloads the requested game`() = runTest {
        coEvery { repository.groupFor(1) } throws IllegalStateException("db")
        val downloaded = mutableListOf<Long>()

        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        assertEquals(listOf(1L), downloaded)
    }

    @Test
    fun `a multi-member group opens the chooser on the shown member`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1), member(2, shown = true), member(3))
        val downloaded = mutableListOf<Long>()

        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        val state = delegate.state.value!!
        assertEquals(SiblingChoicePurpose.DOWNLOAD, state.purpose)
        assertEquals(3, state.rowCount)
        assertEquals(1, state.focusIndex)
        assertTrue(downloaded.isEmpty())
    }

    @Test
    fun `choosing a member sets it as the pick and downloads that rom`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true), member(2), member(3))
        val downloaded = mutableListOf<Long>()
        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        delegate.moveFocus(2)
        delegate.confirm(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPick(3) }
        assertEquals(listOf(3L), downloaded)
        assertNull(delegate.state.value)
    }

    @Test
    fun `dismissing the download chooser downloads nothing`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true), member(2))
        val downloaded = mutableListOf<Long>()
        delegate.requestDownload(this, 1) { downloaded += it }
        advanceUntilIdle()

        delegate.dismiss()
        advanceUntilIdle()

        assertTrue(downloaded.isEmpty())
        assertNull(delegate.state.value)
        coVerify(exactly = 0) { repository.setPick(any()) }
    }

    @Test
    fun `focus wraps in both directions`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true), member(2), member(3))
        delegate.requestDownload(this, 1) { }
        advanceUntilIdle()

        delegate.moveFocus(-1)
        assertEquals(2, delegate.state.value!!.focusIndex)
        delegate.moveFocus(1)
        assertEquals(0, delegate.state.value!!.focusIndex)
    }

    @Test
    fun `the active variant chooser leads with Automatic and focuses the current pick`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1), member(2, picked = true, shown = true))

        delegate.openActiveVariant(this, 1) { }
        assertTrue(delegate.state.value!!.isLoading)
        advanceUntilIdle()

        val state = delegate.state.value!!
        assertFalse(state.isLoading)
        assertEquals(3, state.rowCount)
        assertEquals(2, state.focusIndex)
        assertEquals(2L, state.memberAt(state.focusIndex)?.gameId)
    }

    @Test
    fun `choosing a member in the active variant chooser sets the pick and reports it`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true), member(2))
        val shown = mutableListOf<Long>()
        delegate.openActiveVariant(this, 1) { shown += it }
        advanceUntilIdle()

        delegate.setFocus(2)
        delegate.confirm(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setPick(2) }
        coVerify(exactly = 0) { repository.clearPick(any()) }
        assertEquals(listOf(2L), shown)
    }

    @Test
    fun `Automatic clears the pick and reports the member the group now shows`() = runTest {
        coEvery { repository.groupFor(1) } returnsMany listOf(
            group(member(1), member(2, picked = true, shown = true)),
            group(member(1, shown = true), member(2))
        )
        val shown = mutableListOf<Long>()
        delegate.openActiveVariant(this, 1) { shown += it }
        advanceUntilIdle()

        delegate.setFocus(0)
        delegate.confirm(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.clearPick("g") }
        coVerify(exactly = 0) { repository.setPick(any()) }
        assertEquals(listOf(1L), shown)
    }

    @Test
    fun `a failed load shows the error state and confirm closes it`() = runTest {
        coEvery { repository.groupFor(1) } throws IllegalStateException("db")
        delegate.openActiveVariant(this, 1) { }
        advanceUntilIdle()

        val state = delegate.state.value!!
        assertTrue(state.loadFailed)
        assertEquals(0, state.rowCount)

        delegate.confirm(this)
        assertNull(delegate.state.value)
        coVerify(exactly = 0) { repository.setPick(any()) }
        coVerify(exactly = 0) { repository.clearPick(any()) }
    }

    @Test
    fun `a group that shrank to one member shows the empty state`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1, shown = true))
        delegate.openActiveVariant(this, 1) { }
        advanceUntilIdle()

        val state = delegate.state.value!!
        assertFalse(state.loadFailed)
        assertEquals(0, state.rowCount)
    }

    @Test
    fun `hasChoice is true only for a group with more than one member`() = runTest {
        coEvery { repository.groupFor(1) } returns group(member(1))
        coEvery { repository.groupFor(2) } returns group(member(2), member(3))
        coEvery { repository.groupFor(4) } returns null

        assertFalse(delegate.hasChoice(1))
        assertTrue(delegate.hasChoice(2))
        assertFalse(delegate.hasChoice(4))
    }
}
