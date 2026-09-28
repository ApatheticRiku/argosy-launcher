package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.HomeTileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val OWNER = 1L
private const val PAGED = "PAGED"
private const val SCROLL = "SCROLL"

@RunWith(AndroidJUnit4::class)
class HomeTileDaoGridKindSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var dao: HomeTileDao

    private fun tile(id: Long, kind: String, page: Int, row: Int = 0) = HomeTileEntity(
        id = id,
        ownerUserId = OWNER,
        gridKind = kind,
        pageIndex = page,
        columnIndex = 0,
        rowIndex = row,
        targetType = "APP",
        packageName = "pkg.$id"
    )

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.homeTileDao()
        dao.insert(tile(1L, PAGED, 0))
        dao.insert(tile(2L, PAGED, 1))
        dao.insert(tile(3L, PAGED, 2))
        dao.insert(tile(4L, SCROLL, 0, row = 30))
        dao.insert(tile(5L, SCROLL, 0, row = 31))
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun each_kind_observes_only_its_own_tiles() = runBlocking {
        assertEquals(listOf(1L, 2L, 3L), dao.observeTiles(OWNER, PAGED).first().map { it.id })
        assertEquals(listOf(4L, 5L), dao.observeTiles(OWNER, SCROLL).first().map { it.id })
    }

    @Test
    fun page_reads_and_counts_are_scoped_by_kind() = runBlocking {
        assertEquals(listOf(1L), dao.getPage(OWNER, PAGED, 0).map { it.id })
        assertEquals(listOf(4L, 5L), dao.getPage(OWNER, SCROLL, 0).map { it.id })
        assertEquals(2, dao.getMaxPageIndex(OWNER, PAGED))
        assertEquals(0, dao.getMaxPageIndex(OWNER, SCROLL))
    }

    @Test
    fun deleting_paged_page_zero_leaves_the_scroll_grid_alone() = runBlocking {
        dao.deleteEpisodesForPage(OWNER, PAGED, 0)
        dao.deletePage(OWNER, PAGED, 0)
        dao.shiftPagesDown(OWNER, PAGED, 0)

        assertEquals(listOf(0, 1), dao.observeTiles(OWNER, PAGED).first().map { it.pageIndex })
        assertEquals(listOf(4L, 5L), dao.observeTiles(OWNER, SCROLL).first().map { it.id })
    }

    @Test
    fun a_row_written_without_a_kind_is_paged() = runBlocking {
        dao.insert(
            HomeTileEntity(ownerUserId = OWNER, pageIndex = 5, columnIndex = 0, rowIndex = 0, targetType = "APP")
        )

        assertEquals(5, dao.getMaxPageIndex(OWNER, PAGED))
        assertEquals(2, dao.observeTiles(OWNER, SCROLL).first().size)
    }
}
