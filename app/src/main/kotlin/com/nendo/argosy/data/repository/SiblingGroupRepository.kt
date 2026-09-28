package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameGroupPickDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.UserRomsHiddenDao
import com.nendo.argosy.data.local.entity.GameGroupMemberRow
import com.nendo.argosy.data.local.entity.GameSiblingRow
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApiClient
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.RomMSiblingIdentity
import com.nendo.argosy.domain.model.SeedCandidate
import com.nendo.argosy.domain.model.SiblingGroup
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.domain.model.SiblingGroupRanking
import com.nendo.argosy.domain.model.SiblingMember
import com.nendo.argosy.domain.model.SiblingMemberKind
import com.nendo.argosy.domain.model.SiblingPickChange
import com.nendo.argosy.domain.model.SiblingPickSeed
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SiblingGroupRepository"
private const val UPDATE_CHUNK = 500
private const val PICK_CHANGE_BUFFER = 16

@Singleton
class SiblingGroupRepository @Inject constructor(
    private val gameDao: GameDao,
    private val pickDao: GameGroupPickDao,
    private val platformDao: PlatformDao,
    private val userRomsHiddenDao: UserRomsHiddenDao,
    private val overlayWriter: GameUserOverlayWriter,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val apiClient: RomMApiClient
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val mutex = Mutex()

    private val _pickChanges = MutableSharedFlow<SiblingPickChange>(extraBufferCapacity = PICK_CHANGE_BUFFER)

    /**
     * One entry per platform of a group whose pick was stored or cleared, emitted once the group's
     * visibility is rewritten.
     */
    val pickChanges: SharedFlow<SiblingPickChange> = _pickChanges.asSharedFlow()

    fun start() {
        scope.launch {
            combine(
                overlayWriter.observeActiveOwnerId(),
                syncPreferencesRepository.regionPriority
            ) { owner, priority -> owner to priority }
                .distinctUntilChanged()
                .collect { (owner, priority) ->
                    val collapse = appPreferencesRepository.isSiblingFullPassDone()
                    mutex.withLock {
                        seedPicksOnceLocked(owner)
                        recomputeAllLocked(owner, priority, collapse)
                    }
                }
        }
    }

    suspend fun recomputeAll(): Unit = withContext(Dispatchers.IO) {
        val owner = overlayWriter.activeOwnerId()
        val priority = syncPreferencesRepository.getRegionPriority()
        val collapse = appPreferencesRepository.isSiblingFullPassDone()
        mutex.withLock { recomputeAllLocked(owner, priority, collapse) }
    }

    /**
     * Records that a complete library pass has refreshed every row's group key and flags, then
     * recomputes every group. Before the first call, every row stays visible.
     */
    suspend fun completeFullPass(): Unit = withContext(Dispatchers.IO) {
        appPreferencesRepository.setSiblingFullPassDone()
        recomputeAll()
    }

    suspend fun recomputeGroups(groupKeys: Collection<String>): Unit = withContext(Dispatchers.IO) {
        recomputeGroupsFor(overlayWriter.activeOwnerId(), groupKeys)
    }

    suspend fun pickFor(groupKey: String): Long? =
        pickDao.pickFor(overlayWriter.activeOwnerId(), groupKey)

    suspend fun setPick(gameId: Long): Unit = withContext(Dispatchers.IO) {
        val groupKey = gameDao.getSiblingGroupKey(gameId) ?: return@withContext
        val owner = overlayWriter.activeOwnerId()
        pickDao.set(owner, groupKey, gameId)
        recomputeGroupsFor(owner, listOf(groupKey))
        writeGameCounts(owner, groupKey)
        announcePickChange(groupKey)
    }

    suspend fun clearPick(groupKey: String): Unit = withContext(Dispatchers.IO) {
        val owner = overlayWriter.activeOwnerId()
        pickDao.clear(owner, groupKey)
        recomputeGroupsFor(owner, listOf(groupKey))
        writeGameCounts(owner, groupKey)
        announcePickChange(groupKey)
    }

    private suspend fun announcePickChange(groupKey: String) {
        val covered = HashSet<Long>()
        gameDao.getSiblingRowsForGroups(listOf(groupKey)).forEach { row ->
            if (!covered.add(row.id)) return@forEach
            val group = groupFor(row.id) ?: return@forEach
            group.members.mapTo(covered) { it.gameId }
            SiblingPickChange.of(group)?.let { _pickChanges.tryEmit(it) }
        }
    }

    suspend fun onHiddenChanged(gameId: Long): Unit = withContext(Dispatchers.IO) {
        val groupKey = gameDao.getSiblingGroupKey(gameId) ?: return@withContext
        val owner = overlayWriter.activeOwnerId()
        recomputeGroupsFor(owner, listOf(groupKey))
        writeGameCounts(owner, groupKey)
    }

    /**
     * The members of [gameId]'s sibling group on its platform that this account has not hidden, or
     * null when the game belongs to no group. A single-member result means there is nothing to choose.
     */
    suspend fun groupFor(gameId: Long): SiblingGroup? = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext null
        val groupKey = game.siblingGroupKey ?: return@withContext null
        val owner = overlayWriter.activeOwnerId()
        val rows = gameDao.getGroupMembers(groupKey, game.platformId, owner)
        val pick = pickDao.pickFor(owner, groupKey)?.takeIf { picked -> rows.any { it.id == picked } }
        val shownId = pick ?: rows.firstOrNull { it.isGroupVisible && !it.isHackVariant }?.id
        SiblingGroup(
            groupKey = groupKey,
            platformId = game.platformId,
            members = rows.map { it.toGroupMember(isPicked = it.id == pick, isShown = it.id == shownId) }
                .sortedWith(compareBy<SiblingGroupMember> { it.kind.ordinal }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName ?: it.title }
                    .thenBy { it.gameId })
        )
    }

    private suspend fun writeGameCounts(ownerUserId: Long?, groupKey: String) {
        gameDao.getPlatformIdsForGroup(groupKey).forEach { platformId ->
            platformDao.updateGameCount(platformId, gameDao.countByPlatform(platformId, ownerUserId))
        }
    }

    private fun GameGroupMemberRow.toGroupMember(isPicked: Boolean, isShown: Boolean) = SiblingGroupMember(
        gameId = id,
        title = title,
        fileName = rommFileName,
        regions = regions.regionTokens(),
        kind = when {
            isHackVariant -> SiblingMemberKind.HACK
            isTranslationVariant -> SiblingMemberKind.TRANSLATION
            RomMSiblingIdentity.isPreRelease(rommFileName) -> SiblingMemberKind.PRE_RELEASE
            else -> SiblingMemberKind.RELEASE
        },
        isDownloaded = localPath != null,
        isPicked = isPicked,
        isShown = isShown
    )

    private suspend fun recomputeGroupsFor(ownerUserId: Long?, groupKeys: Collection<String>) {
        if (groupKeys.isEmpty()) return
        val priority = syncPreferencesRepository.getRegionPriority()
        val collapse = appPreferencesRepository.isSiblingFullPassDone()
        mutex.withLock {
            gameDao.showUngroupedRows()
            val rows = groupKeys.distinct().chunked(UPDATE_CHUNK).flatMap { gameDao.getSiblingRowsForGroups(it) }
            applyVisibility(rows, ownerUserId, priority, collapse)
        }
    }

    /**
     * Re-reads this account's RomM main-sibling flag for [gameId] with one `GET /api/roms/{id}`
     * and recomputes its group when the flag changed. A failed request leaves the stored flag.
     */
    suspend fun refreshRommMainSibling(gameId: Long): Unit = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext
        val rommId = game.rommId?.takeIf { it > 0 } ?: return@withContext
        val rom = when (val fetched = apiClient.getRom(rommId)) {
            is RomMResult.Success -> fetched.data
            is RomMResult.Error -> {
                Logger.debug(TAG, "refreshRommMainSibling: rom $rommId unavailable (${fetched.message})")
                return@withContext
            }
        }
        val isMain = rom.romUser?.isMainSibling ?: return@withContext
        if (isMain == game.rommMainSibling) return@withContext
        gameDao.setRommMainSibling(gameId, isMain)
        game.siblingGroupKey?.let { recomputeGroups(listOf(it)) }
    }

    internal suspend fun seedPicksOnce(ownerUserId: Long?) {
        mutex.withLock { seedPicksOnceLocked(ownerUserId) }
    }

    private suspend fun seedPicksOnceLocked(ownerUserId: Long?) {
        if (ownerUserId == null) return
        if (appPreferencesRepository.isSiblingPickSeedDone(ownerUserId)) return
        val picked = pickDao.picksForOwner(ownerUserId).mapTo(mutableSetOf()) { it.groupKey }
        val candidates = gameDao.getSiblingRows().map {
            SeedCandidate(it.id, it.siblingGroupKey, isDownloaded = it.localPath != null)
        }
        val seeds = SiblingPickSeed.picks(candidates, picked)
        pickDao.insertAllMissing(ownerUserId, seeds)
        appPreferencesRepository.setSiblingPickSeedDone(ownerUserId)
        Logger.info(TAG, "seedPicksOnce: seeded ${seeds.size} picks for owner $ownerUserId")
    }

    private suspend fun recomputeAllLocked(
        ownerUserId: Long?,
        regionPriority: List<String>,
        collapse: Boolean
    ) {
        gameDao.showUngroupedRows()
        applyVisibility(gameDao.getSiblingRows(), ownerUserId, regionPriority, collapse)
    }

    private suspend fun applyVisibility(
        rows: List<GameSiblingRow>,
        ownerUserId: Long?,
        regionPriority: List<String>,
        collapse: Boolean
    ) {
        if (rows.isEmpty()) return
        val picks = pickDao.picksForOwner(ownerUserId).associate { it.groupKey to it.gameId }
        val hiddenForOwner = userRomsHiddenDao.hiddenGameIds(ownerUserId).toHashSet()
        val show = mutableListOf<Long>()
        val hide = mutableListOf<Long>()
        rows.groupBy { it.siblingGroupKey }.forEach { (groupKey, groupRows) ->
            val visible = if (collapse) {
                SiblingGroupRanking.visibleMembers(
                    groupRows.filterNot { it.id in hiddenForOwner }.map { it.toMember() },
                    picks[groupKey],
                    regionPriority
                )
            } else {
                groupRows.mapTo(HashSet()) { it.id }
            }
            groupRows.forEach { row ->
                val shouldShow = row.id in visible || row.id in hiddenForOwner
                if (shouldShow != row.isGroupVisible) {
                    if (shouldShow) show += row.id else hide += row.id
                }
            }
        }
        show.chunked(UPDATE_CHUNK).forEach { gameDao.setGroupVisible(it, true) }
        hide.chunked(UPDATE_CHUNK).forEach { gameDao.setGroupVisible(it, false) }
        if (show.isNotEmpty() || hide.isNotEmpty()) {
            Logger.info(TAG, "applyVisibility: shown ${show.size}, hidden ${hide.size}")
        }
    }

    private fun GameSiblingRow.toMember() = SiblingMember(
        gameId = id,
        isHack = isHackVariant,
        isRommMain = rommMainSibling,
        isPreRelease = RomMSiblingIdentity.isPreRelease(rommFileName),
        isTranslation = isTranslationVariant,
        regions = regions.regionTokens(),
        fileName = rommFileName.orEmpty()
    )

    private fun String?.regionTokens(): List<String> =
        this?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
}
