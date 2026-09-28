package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameGroupPickDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.UserRomsHiddenDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameGroupMemberRow
import com.nendo.argosy.data.local.entity.GameGroupPickEntity
import com.nendo.argosy.data.local.entity.GameSiblingRow
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.SiblingMemberKind
import com.nendo.argosy.domain.model.SiblingPickChange
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SiblingGroupRepositoryTest {

    private val owner = 5L

    private lateinit var gameDao: GameDao
    private lateinit var pickDao: GameGroupPickDao
    private lateinit var platformDao: PlatformDao
    private lateinit var userRomsHiddenDao: UserRomsHiddenDao
    private lateinit var appPreferences: AppPreferencesRepository
    private lateinit var repository: SiblingGroupRepository

    private val rows = mutableMapOf<Long, GameSiblingRow>()
    private var picks = listOf<GameGroupPickEntity>()
    private val seededOwners = mutableSetOf<Long>()
    private val hiddenIds = mutableSetOf<Long>()
    private var fullPassDone = true

    private fun row(
        id: Long,
        group: String,
        regions: String = "USA",
        fileName: String = "Game (USA).sfc",
        hack: Boolean = false,
        downloaded: Boolean = false,
        visible: Boolean = true
    ) = GameSiblingRow(
        id = id,
        siblingGroupKey = group,
        isHackVariant = hack,
        isTranslationVariant = false,
        rommMainSibling = false,
        rommFileName = fileName,
        regions = regions,
        localPath = if (downloaded) "/roms/$id" else null,
        isGroupVisible = visible
    )

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        pickDao = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        userRomsHiddenDao = mockk(relaxed = true)
        appPreferences = mockk(relaxed = true)
        val overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true)
        val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true)

        coEvery { overlayWriter.activeOwnerId() } returns owner
        coEvery { syncPreferences.getRegionPriority() } returns listOf("USA", "Europe", "Japan")
        coEvery { gameDao.getSiblingRows() } answers { rows.values.toList() }
        coEvery { gameDao.getSiblingRowsForGroups(any()) } answers {
            val keys = firstArg<List<String>>()
            rows.values.filter { it.siblingGroupKey in keys }
        }
        coEvery { gameDao.setGroupVisible(any(), any()) } answers {
            val ids = firstArg<List<Long>>()
            val visible = secondArg<Boolean>()
            ids.forEach { id -> rows[id] = rows.getValue(id).copy(isGroupVisible = visible) }
        }
        coEvery { pickDao.picksForOwner(any()) } answers {
            val requested = firstArg<Long?>()
            picks.filter { it.ownerUserId == requested }
        }
        coEvery { userRomsHiddenDao.hiddenGameIds(any()) } answers { hiddenIds.toList() }
        coEvery { appPreferences.isSiblingPickSeedDone(any()) } answers { firstArg<Long>() in seededOwners }
        coEvery { appPreferences.setSiblingPickSeedDone(any()) } answers { seededOwners += firstArg<Long>() }
        coEvery { appPreferences.isSiblingFullPassDone() } answers { fullPassDone }
        coEvery { appPreferences.setSiblingFullPassDone() } answers { fullPassDone = true }

        repository = SiblingGroupRepository(
            gameDao = gameDao,
            pickDao = pickDao,
            platformDao = platformDao,
            userRomsHiddenDao = userRomsHiddenDao,
            overlayWriter = overlayWriter,
            syncPreferencesRepository = syncPreferences,
            appPreferencesRepository = appPreferences,
            apiClient = mockk(relaxed = true)
        )
    }

    private fun put(vararg members: GameSiblingRow) = members.forEach { rows[it.id] = it }

    @Test
    fun `recompute hides every member but the representative and keeps hacks`() = runTest {
        put(
            row(1, "g", regions = "Japan", fileName = "a"),
            row(2, "g", regions = "USA", fileName = "b"),
            row(3, "g", hack = true),
            row(4, "solo")
        )

        repository.recomputeAll()

        assertEquals(
            mapOf(1L to false, 2L to true, 3L to true, 4L to true),
            rows.mapValues { it.value.isGroupVisible }
        )
    }

    @Test
    fun `recompute is idempotent`() = runTest {
        put(
            row(1, "g", regions = "Japan", fileName = "a"),
            row(2, "g", regions = "USA", fileName = "b"),
            row(3, "h", regions = "USA"),
            row(4, "h", regions = "Europe", visible = false)
        )

        repository.recomputeAll()
        val afterFirst = rows.toMap()
        repository.recomputeAll()

        assertEquals(afterFirst, rows.toMap())
        coVerify(exactly = 1) { gameDao.setGroupVisible(listOf(1L), false) }
        coVerify(exactly = 0) { gameDao.setGroupVisible(any(), true) }
    }

    @Test
    fun `the local pick decides the group's entry`() = runTest {
        put(row(1, "g", regions = "Japan", fileName = "a"), row(2, "g", regions = "USA", fileName = "b"))
        picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "g", gameId = 1))

        repository.recomputeGroups(listOf("g"))

        assertEquals(mapOf(1L to true, 2L to false), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `setting a pick stores it for the active account and recomputes its group`() = runTest {
        put(row(1, "g", regions = "Japan", fileName = "a"), row(2, "g", regions = "USA", fileName = "b"))
        coEvery { gameDao.getSiblingGroupKey(1) } returns "g"
        coEvery { pickDao.set(owner, "g", 1) } answers {
            picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "g", gameId = 1))
        }

        repository.setPick(1)

        coVerify { pickDao.set(owner, "g", 1) }
        assertEquals(mapOf(1L to true, 2L to false), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `recompute shows rows that have left every group`() = runTest {
        repository.recomputeAll()

        coVerify { gameDao.showUngroupedRows() }
    }

    @Test
    fun `before the first full pass every member stays visible`() = runTest {
        fullPassDone = false
        put(
            row(1, "g", regions = "Japan", fileName = "a"),
            row(2, "g", regions = "USA", fileName = "b"),
            row(3, "h", regions = "Europe", visible = false)
        )

        repository.recomputeAll()
        repository.recomputeGroups(listOf("g", "h"))

        assertEquals(mapOf(1L to true, 2L to true, 3L to true), rows.mapValues { it.value.isGroupVisible })
        coVerify(exactly = 0) { gameDao.setGroupVisible(any(), false) }
    }

    @Test
    fun `before the first full pass a pick does not collapse its group`() = runTest {
        fullPassDone = false
        put(row(1, "g", regions = "Japan", fileName = "a"), row(2, "g", regions = "USA", fileName = "b"))
        picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "g", gameId = 1))

        repository.recomputeGroups(listOf("g"))

        assertEquals(mapOf(1L to true, 2L to true), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `completing the full pass records it and collapses every group`() = runTest {
        fullPassDone = false
        put(
            row(1, "g", regions = "Japan", fileName = "a"),
            row(2, "g", regions = "USA", fileName = "b"),
            row(3, "solo")
        )

        repository.completeFullPass()

        coVerify(exactly = 1) { appPreferences.setSiblingFullPassDone() }
        assertEquals(mapOf(1L to false, 2L to true, 3L to true), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `the seed picks the one downloaded member of each group, once`() = runTest {
        put(
            row(1, "a", downloaded = true), row(2, "a"),
            row(3, "b", downloaded = true), row(4, "b", downloaded = true),
            row(5, "c"), row(6, "c"),
            row(7, "solo", downloaded = true)
        )

        repository.seedPicksOnce(owner)
        repository.seedPicksOnce(owner)

        coVerify(exactly = 1) { pickDao.insertAllMissing(owner, mapOf("a" to 1L)) }
        coVerify(exactly = 1) { appPreferences.setSiblingPickSeedDone(owner) }
    }

    @Test
    fun `each signed-in account gets its own one-time seed`() = runTest {
        val other = 9L
        put(row(1, "a", downloaded = true), row(2, "a"))

        repository.seedPicksOnce(owner)
        repository.seedPicksOnce(other)
        repository.seedPicksOnce(owner)
        repository.seedPicksOnce(other)

        coVerify(exactly = 1) { pickDao.insertAllMissing(owner, mapOf("a" to 1L)) }
        coVerify(exactly = 1) { pickDao.insertAllMissing(other, mapOf("a" to 1L)) }
        assertEquals(setOf(owner, other), seededOwners)
    }

    @Test
    fun `a second account's seed skips only the groups that account already picked`() = runTest {
        val other = 9L
        put(row(1, "a", downloaded = true), row(2, "a"), row(3, "b", downloaded = true), row(4, "b"))
        picks = listOf(GameGroupPickEntity(ownerUserId = other, groupKey = "a", gameId = 2))

        repository.seedPicksOnce(owner)
        repository.seedPicksOnce(other)

        coVerify(exactly = 1) { pickDao.insertAllMissing(owner, mapOf("a" to 1L, "b" to 3L)) }
        coVerify(exactly = 1) { pickDao.insertAllMissing(other, mapOf("b" to 3L)) }
    }

    @Test
    fun `the seed leaves groups that already have a pick`() = runTest {
        put(row(1, "a", downloaded = true), row(2, "a"))
        picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "a", gameId = 2))

        repository.seedPicksOnce(owner)

        coVerify { pickDao.insertAllMissing(owner, emptyMap()) }
    }

    @Test
    fun `the seed waits for a signed-in account`() = runTest {
        put(row(1, "a", downloaded = true), row(2, "a"))

        repository.seedPicksOnce(null)

        coVerify(exactly = 0) { pickDao.insertAllMissing(any(), any()) }
        coVerify(exactly = 0) { appPreferences.setSiblingPickSeedDone(any()) }
    }

    @Test
    fun `a member this account hid is left out of the ranking and the next one is shown`() = runTest {
        put(row(1, "g", regions = "USA", fileName = "a"), row(2, "g", regions = "Japan", fileName = "b"))
        hiddenIds += 1L

        repository.recomputeAll()

        assertEquals(mapOf(1L to true, 2L to true), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `a hidden local pick falls back to the ranking`() = runTest {
        put(
            row(1, "g", regions = "Japan", fileName = "a"),
            row(2, "g", regions = "USA", fileName = "b"),
            row(3, "g", regions = "Europe", fileName = "c")
        )
        picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "g", gameId = 1))
        hiddenIds += 1L

        repository.recomputeAll()

        assertEquals(mapOf(1L to true, 2L to true, 3L to false), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `hiding the shown member promotes the next one, and unhiding restores it`() = runTest {
        put(row(1, "g", regions = "USA", fileName = "a"), row(2, "g", regions = "Japan", fileName = "b"))
        coEvery { gameDao.getSiblingGroupKey(1) } returns "g"
        repository.recomputeAll()
        assertEquals(mapOf(1L to true, 2L to false), rows.mapValues { it.value.isGroupVisible })

        hiddenIds += 1L
        repository.onHiddenChanged(1)
        assertEquals(true, rows.getValue(2).isGroupVisible)

        hiddenIds -= 1L
        repository.onHiddenChanged(1)
        assertEquals(mapOf(1L to true, 2L to false), rows.mapValues { it.value.isGroupVisible })
    }

    @Test
    fun `a hide change on a game outside every group recomputes nothing`() = runTest {
        coEvery { gameDao.getSiblingGroupKey(4) } returns null

        repository.onHiddenChanged(4)

        coVerify(exactly = 0) { gameDao.getSiblingRowsForGroups(any()) }
        coVerify(exactly = 0) { platformDao.updateGameCount(any(), any()) }
    }

    private fun entity(id: Long, group: String?, platformId: Long = 7) = GameEntity(
        id = id,
        platformId = platformId,
        title = "Game",
        sortTitle = "game",
        localPath = null,
        rommId = id,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        siblingGroupKey = group
    )

    private fun member(
        id: Long,
        fileName: String,
        hack: Boolean = false,
        translation: Boolean = false,
        downloaded: Boolean = false,
        visible: Boolean = true
    ) = GameGroupMemberRow(
        id = id,
        title = "Game",
        rommFileName = fileName,
        regions = "USA, Europe",
        localPath = if (downloaded) "/roms/$id" else null,
        isHackVariant = hack,
        isTranslationVariant = translation,
        isGroupVisible = visible
    )

    @Test
    fun `a game outside every group has no members to choose from`() = runTest {
        coEvery { gameDao.getById(1) } returns entity(1, group = null)

        assertNull(repository.groupFor(1))
    }

    @Test
    fun `group members carry their kind, download state and the shown entry`() = runTest {
        coEvery { gameDao.getById(1) } returns entity(1, "g")
        coEvery { gameDao.getGroupMembers("g", 7, owner) } returns listOf(
            member(1, "Game (USA).sfc", downloaded = true),
            member(2, "Game (Japan).sfc", visible = false),
            member(3, "Game (USA) (Hack).sfc", hack = true),
            member(4, "Game (Japan) (T-En).sfc", translation = true, visible = false),
            member(5, "Game (USA) (Beta).sfc", visible = false)
        )

        val group = repository.groupFor(1)!!

        assertEquals(
            listOf(
                2L to SiblingMemberKind.RELEASE,
                1L to SiblingMemberKind.RELEASE,
                5L to SiblingMemberKind.PRE_RELEASE,
                4L to SiblingMemberKind.TRANSLATION,
                3L to SiblingMemberKind.HACK
            ),
            group.members.map { it.gameId to it.kind }
        )
        assertEquals(listOf(1L), group.members.filter { it.isDownloaded }.map { it.gameId })
        assertEquals(1L, group.shownMember?.gameId)
        assertEquals(listOf("USA", "Europe"), group.members.first().regions)
        assertEquals(false, group.hasPick)
    }

    @Test
    fun `the local pick is the shown member even when it is a hack`() = runTest {
        coEvery { gameDao.getById(1) } returns entity(1, "g")
        coEvery { pickDao.pickFor(owner, "g") } returns 3
        coEvery { gameDao.getGroupMembers("g", 7, owner) } returns listOf(
            member(1, "Game (USA).sfc", visible = false),
            member(3, "Game (USA) (Hack).sfc", hack = true)
        )

        val group = repository.groupFor(1)!!

        assertEquals(3L, group.shownMember?.gameId)
        assertEquals(listOf(3L), group.members.filter { it.isPicked }.map { it.gameId })
    }

    @Test
    fun `a pick on a member this account hid is ignored`() = runTest {
        coEvery { gameDao.getById(1) } returns entity(1, "g")
        coEvery { pickDao.pickFor(owner, "g") } returns 9
        coEvery { gameDao.getGroupMembers("g", 7, owner) } returns listOf(
            member(1, "Game (USA).sfc"),
            member(2, "Game (Japan).sfc", visible = false)
        )

        val group = repository.groupFor(1)!!

        assertEquals(false, group.hasPick)
        assertEquals(1L, group.shownMember?.gameId)
    }

    @Test
    fun `setting a pick rewrites the game count of every platform the group spans`() = runTest {
        put(row(1, "g", regions = "Japan", fileName = "a"), row(2, "g", regions = "USA", fileName = "b"))
        coEvery { gameDao.getSiblingGroupKey(1) } returns "g"
        coEvery { gameDao.getPlatformIdsForGroup("g") } returns listOf(7L)
        coEvery { gameDao.countByPlatform(7, owner) } returns 41

        repository.setPick(1)

        coVerify(exactly = 1) { platformDao.updateGameCount(7, 41) }
    }

    @Test
    fun `clearing a pick removes it, recomputes the group and rewrites counts`() = runTest {
        put(row(1, "g", regions = "Japan", fileName = "a", visible = true), row(2, "g", regions = "USA", fileName = "b", visible = false))
        coEvery { gameDao.getPlatformIdsForGroup("g") } returns listOf(7L)
        coEvery { gameDao.countByPlatform(7, owner) } returns 12

        repository.clearPick("g")

        coVerify { pickDao.clear(owner, "g") }
        assertEquals(mapOf(1L to false, 2L to true), rows.mapValues { it.value.isGroupVisible })
        coVerify(exactly = 1) { platformDao.updateGameCount(7, 12) }
    }

    private fun membersFromRows() {
        coEvery { gameDao.getById(any()) } answers {
            val id = firstArg<Long>()
            entity(id, rows[id]?.siblingGroupKey)
        }
        coEvery { gameDao.getGroupMembers(any(), 7, owner) } answers {
            val key = firstArg<String>()
            rows.values.filter { it.siblingGroupKey == key }.map {
                member(it.id, it.rommFileName.orEmpty(), hack = it.isHackVariant, visible = it.isGroupVisible)
            }
        }
    }

    @Test
    fun `setting a pick announces the picked member as the group's shown entry`() = runTest {
        put(row(1, "g", regions = "Japan", fileName = "a"), row(2, "g", regions = "USA", fileName = "b"))
        membersFromRows()
        coEvery { gameDao.getSiblingGroupKey(1) } returns "g"
        coEvery { pickDao.set(owner, "g", 1) } answers {
            picks = listOf(GameGroupPickEntity(ownerUserId = owner, groupKey = "g", gameId = 1))
        }
        coEvery { pickDao.pickFor(owner, "g") } answers { picks.firstOrNull()?.gameId }
        val changes = mutableListOf<SiblingPickChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.pickChanges.toList(changes)
        }

        repository.setPick(1)

        assertEquals(listOf(SiblingPickChange(memberIds = setOf(1L, 2L), shownGameId = 1L)), changes)
    }

    @Test
    fun `clearing a pick announces the ranked member as the group's shown entry`() = runTest {
        put(
            row(1, "g", regions = "Japan", fileName = "a", visible = true),
            row(2, "g", regions = "USA", fileName = "b", visible = false)
        )
        membersFromRows()
        val changes = mutableListOf<SiblingPickChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.pickChanges.toList(changes)
        }

        repository.clearPick("g")

        assertEquals(listOf(SiblingPickChange(memberIds = setOf(1L, 2L), shownGameId = 2L)), changes)
    }

    @Test
    fun `a game outside every group announces nothing when picked`() = runTest {
        coEvery { gameDao.getSiblingGroupKey(4) } returns null
        val changes = mutableListOf<SiblingPickChange>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.pickChanges.toList(changes)
        }

        repository.setPick(4)

        assertEquals(emptyList<SiblingPickChange>(), changes)
    }
}
