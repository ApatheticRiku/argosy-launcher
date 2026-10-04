package com.nendo.argosy.data.steam

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.remote.steam.SteamBrowseAssets
import com.nendo.argosy.data.remote.steam.SteamStoreBrowseApi
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SteamLibraryRepair @Inject constructor(
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val imageCacheManager: ImageCacheManager
) {
    private val api: SteamStoreBrowseApi by lazy {
        Retrofit.Builder()
            .baseUrl(STEAM_WEB_API)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(SteamStoreBrowseApi::class.java)
    }

    suspend fun restoreSources() = withContext(Dispatchers.IO) {
        val restored = gameDao.repairSteamSources(LocalPlatformIds.STEAM)
        if (restored > 0) Logger.info(TAG, "restoreSources: restored Steam source on $restored rows")
    }

    suspend fun repairCovers() = withContext(Dispatchers.IO) {
        val uncached = gameDao.getSteamGamesWithUncachedCovers(LocalPlatformIds.STEAM)
        if (uncached.isEmpty()) return@withContext
        val assets = uncached.mapNotNull { it.steamAppId }
            .chunked(BATCH_SIZE)
            .flatMap { batch -> runCatching { fetchAssets(batch) }.getOrElse { emptyList() } }
            .toMap()

        for (game in uncached) {
            val appId = game.steamAppId ?: continue
            val art = assets[appId]
            val stored = gameArtDao.getForGame(game.id).associateBy { it.slot }
            val storedCover = stored[ArtSlot.COVER.name]?.sourceUrl
            val cover = art?.let { assetUrl(it, it.libraryCapsule) }
                ?: storedCover
                ?: "$STEAM_CDN/$appId/library_600x900.jpg"
            val hero = art?.let { assetUrl(it, it.libraryHero) }
            val coverCandidates = listOfNotNull(cover, storedCover).distinct()
            recordArtSource(gameArtDao, imageCacheManager, game.id, ArtSlot.COVER, coverCandidates, game.title, steamAppId = appId)
            if (hero != null) {
                recordArtSource(gameArtDao, imageCacheManager, game.id, ArtSlot.BACKGROUND, listOf(hero), game.title, steamAppId = appId)
            }
        }
        Logger.info(TAG, "repairCovers: re-queued ${uncached.size} covers, ${assets.size} resolved from the store")
    }

    private suspend fun fetchAssets(appIds: List<Long>): List<Pair<Long, SteamBrowseAssets>> {
        val ids = appIds.joinToString(",") { "{\"appid\":$it}" }
        val input = "{\"ids\":[$ids],\"context\":{\"language\":\"english\",\"country_code\":\"US\"}," +
            "\"data_request\":{\"include_assets\":true}}"
        val response = api.getItems(input)
        if (!response.isSuccessful) return emptyList()
        return response.body()?.response?.storeItems.orEmpty().mapNotNull { item ->
            val id = item.appId ?: return@mapNotNull null
            val assets = item.assets ?: return@mapNotNull null
            id to assets
        }
    }

    private fun assetUrl(assets: SteamBrowseAssets, fileName: String?): String? {
        val format = assets.assetUrlFormat ?: return null
        val name = fileName?.takeIf { it.isNotBlank() } ?: return null
        return STEAM_ASSET_HOST + format.replace("\${FILENAME}", name)
    }

    private companion object {
        const val TAG = "SteamLibraryRepair"
        const val STEAM_WEB_API = "https://api.steampowered.com/"
        const val STEAM_ASSET_HOST = "https://shared.akamai.steamstatic.com/store_item_assets/"
        const val STEAM_CDN = "https://steamcdn-a.akamaihd.net/steam/apps"
        const val BATCH_SIZE = 50
    }
}
