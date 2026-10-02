package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.sync.record
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingConflictDaoSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var dao: PendingConflictDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.pendingConflictDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun conflict(slot: String?, rommSaveId: Long? = null) = PendingConflictEntity(
        gameId = 7L,
        rommSaveId = rommSaveId,
        fileName = "save.srm",
        slot = slot,
        localUpdatedAt = null,
        serverUpdatedAt = null,
        ownerUserId = 3L
    )

    @Test
    fun recordingTheSameConflictWithNoServerSaveTwiceKeepsOneRow() = runBlocking {
        val first = dao.record(conflict(slot = "autosave"))
        val second = dao.record(conflict(slot = "autosave"))

        assertEquals(first, second)
        assertEquals(1, dao.getOpenConflicts(listOf(3L)).size)
    }

    @Test
    fun conflictsInDifferentSlotsStayApart() = runBlocking {
        val autosave = dao.record(conflict(slot = "autosave"))
        val named = dao.record(conflict(slot = "Before boss"))

        assertNotEquals(autosave, named)
        assertEquals(2, dao.getOpenConflicts(listOf(3L)).size)
    }

    @Test
    fun aDismissedConflictThatRecursReopensUnderItsOwnId() = runBlocking {
        val id = dao.record(conflict(slot = "autosave", rommSaveId = 50L))
        dao.dismiss(id)

        val again = dao.record(conflict(slot = "autosave", rommSaveId = 50L))

        assertEquals(id, again)
        assertEquals(1, dao.getOpenConflicts(listOf(3L)).size)
    }
}
