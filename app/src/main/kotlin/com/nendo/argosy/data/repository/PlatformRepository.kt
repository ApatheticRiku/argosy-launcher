package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.PlatformEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformRepository @Inject constructor(
    private val platformDao: PlatformDao
) {
    /**
     * Places [orderedIds], which may be a subset of all platforms, in that sequence within the slots
     * they already hold in the stored order; answers false when the order did not change.
     */
    suspend fun applyPlatformOrder(orderedIds: List<Long>): Boolean {
        val ordered = platformDao.getAllPlatforms()
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        val byId = ordered.associateBy { it.id }
        val placed = orderedIds.distinct().mapNotNull { byId[it] }
        val placedIds = placed.mapTo(HashSet()) { it.id }
        val slots = ordered.indices.filter { ordered[it].id in placedIds }
        val reordered = ordered.toMutableList()
        slots.zip(placed).forEach { (slot, platform) -> reordered[slot] = platform }
        if (reordered.map { it.id } == ordered.map { it.id }) return false
        reordered.forEachIndexed { position, platform ->
            if (platform.sortOrder != position) platformDao.updateSortOrder(platform.id, position)
        }
        return true
    }

    fun observeVisiblePlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeVisiblePlatforms()

    fun observeAllPlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeAllPlatforms()

    fun observePlatformsWithGames(): Flow<List<PlatformEntity>> =
        platformDao.observePlatformsWithGames()

    fun observeConfigurablePlatforms(): Flow<List<PlatformEntity>> =
        platformDao.observeConfigurablePlatforms()

    suspend fun getById(id: Long): PlatformEntity? =
        platformDao.getById(id)

    suspend fun getPlatformsWithGames(): List<PlatformEntity> =
        platformDao.getPlatformsWithGames()

    suspend fun getAllPlatforms(): List<PlatformEntity> =
        platformDao.getAllPlatforms()

    suspend fun getAllPlatformIds(): Set<Long> =
        platformDao.getAllPlatforms().mapTo(HashSet()) { it.id }

    suspend fun getAllPlatformsOrdered(): List<PlatformEntity> =
        platformDao.getAllPlatformsOrdered()

    suspend fun getSyncEnabledPlatforms(): List<PlatformEntity> =
        platformDao.getSyncEnabledPlatforms()

    suspend fun getEnabledPlatformCount(): Int =
        platformDao.getEnabledPlatformCount()

    suspend fun getTotalPlatformCount(): Int =
        platformDao.getTotalPlatformCount()

    suspend fun insert(platform: PlatformEntity) =
        platformDao.insert(platform)

    suspend fun updateSyncEnabled(platformId: Long, enabled: Boolean) =
        platformDao.updateSyncEnabled(platformId, enabled)

    suspend fun updateVisibility(platformId: Long, visible: Boolean) =
        platformDao.updateVisibility(platformId, visible)

    suspend fun updateCustomRomPath(platformId: Long, path: String?) =
        platformDao.updateCustomRomPath(platformId, path)

    suspend fun updateCombineContent(platformId: Long, enabled: Boolean) =
        platformDao.updateCombineContent(platformId, enabled)

    suspend fun isCombineContentEnabled(platformId: Long): Boolean =
        platformDao.getCombineContent(platformId) == true
}
