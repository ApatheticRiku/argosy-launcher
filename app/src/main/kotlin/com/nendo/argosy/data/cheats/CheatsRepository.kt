package com.nendo.argosy.data.cheats

import android.content.Context
import android.provider.Settings
import android.text.Html
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.local.entity.CheatVariantTuple
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CheatsRepository"
internal const val CHEATS_REFETCH_TTL_MS = 7L * 24 * 60 * 60 * 1000

@Singleton
class CheatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cheatsService: CheatsService,
    private val cheatDao: CheatDao,
    private val gameDao: GameDao
) {
    private val deviceToken: String by lazy {
        getOrCreateDeviceToken()
    }

    fun isConfigured(): Boolean = cheatsService.isConfigured()

    suspend fun syncCheatsForGame(game: GameEntity): Boolean {
        if (!isConfigured()) {
            Logger.debug(TAG, "CheatsDB not configured, skipping sync for game=${game.id}")
            return false
        }

        val now = System.currentTimeMillis()

        if (!shouldFetch(game, now)) {
            Logger.debug(TAG, "Cheats still fresh for game=${game.id}, skipping")
            return true
        }

        val mappedPlatform = mapPlatformSlug(game.platformSlug)
        if (mappedPlatform == null) {
            Logger.debug(TAG, "Platform ${game.platformSlug} not supported for cheats lookup")
            gameDao.updateCheatsFetchedAt(game.id, now)
            return true
        }

        Logger.debug(TAG, "Fetching cheats for game=${game.id}, name='${game.title}', platform=$mappedPlatform")

        val fileName = game.rommFileName ?: game.localPath?.substringAfterLast("/")
        val parsed = if (fileName != null) RomFileNameParser.parse(fileName) else null

        return try {
            val result = cheatsService.lookupByName(
                game.title,
                mappedPlatform,
                deviceToken,
                region = parsed?.region,
                version = parsed?.version
            )

            if (result != null && result.variants.isNotEmpty()) {
                val enabledKeys = cheatDao.getEnabledServerCheatKeys(game.id).map {
                    Triple(it.variantRegion, it.variantVersion, it.cheatIndex)
                }.toSet()
                cheatDao.deleteServerCheats(game.id)

                val entities = result.variants.flatMap { variant ->
                    variant.cheats.map { cheat ->
                        val key = Triple(variant.region, variant.version, cheat.index)
                        CheatEntity(
                            gameId = game.id,
                            cheatIndex = cheat.index,
                            description = Html.fromHtml(
                                cheat.description,
                                Html.FROM_HTML_MODE_LEGACY
                            ).toString(),
                            code = cheat.code,
                            enabled = key in enabledKeys,
                            variantRegion = variant.region,
                            variantVersion = variant.version
                        )
                    }
                }

                cheatDao.insertAll(entities)

                if (result.variants.size == 1) {
                    val only = result.variants.first()
                    gameDao.updateCheatsSelectedVariant(game.id, only.region, only.version)
                }

                Logger.info(TAG, "Stored ${entities.size} cheats across ${result.variants.size} variants for game=${game.id} (${result.gameName})")
            } else {
                Logger.debug(TAG, "No cheats found for game=${game.id}")
            }

            gameDao.updateCheatsFetchedAt(game.id, now)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to sync cheats for game=${game.id}: ${e.message}")
            false
        }
    }

    private suspend fun shouldFetch(game: GameEntity, now: Long): Boolean {
        val fetchedAt = game.cheatsFetchedAt ?: return true
        if (mapPlatformSlug(game.platformSlug) == null) return false
        val serverCheatCount = cheatDao.getServerCheatCount(game.id)
        if (serverCheatCount == 0) return true
        return (now - fetchedAt) >= CHEATS_REFETCH_TTL_MS
    }

    suspend fun getCheatsForGame(gameId: Long): List<CheatEntity> {
        val game = gameDao.getById(gameId) ?: return emptyList()
        val region = game.cheatsSelectedRegion
        val version = game.cheatsSelectedVersion

        return if (region != null && version != null) {
            cheatDao.getCheatsForVariant(gameId, region, version)
        } else {
            cheatDao.getCheatsForGame(gameId)
        }
    }

    suspend fun getEnabledCheats(gameId: Long): List<CheatEntity> {
        return cheatDao.getEnabledCheats(gameId)
    }

    suspend fun setCheatEnabled(cheatId: Long, enabled: Boolean) {
        cheatDao.setEnabled(cheatId, enabled)
    }

    suspend fun setAllCheatsEnabled(gameId: Long, enabled: Boolean) {
        cheatDao.setAllEnabled(gameId, enabled)
    }

    suspend fun getCheatCount(gameId: Long): Int {
        return cheatDao.getCheatCount(gameId)
    }

    suspend fun getVariantsForGame(gameId: Long): List<CheatVariantTuple> {
        return cheatDao.getVariantsForGame(gameId)
    }

    suspend fun selectVariant(gameId: Long, region: String, version: String) {
        gameDao.updateCheatsSelectedVariant(gameId, region, version)
    }

    /**
     * Returns the stored cheat variant, or the one variant the ROM file name identifies
     * (region and revision tags), persisting it so later loads agree.
     */
    suspend fun resolveSelectedVariant(gameId: Long): Pair<String, String>? {
        val game = gameDao.getById(gameId) ?: return null
        val region = game.cheatsSelectedRegion
        val version = game.cheatsSelectedVersion
        if (region != null && version != null) return region to version

        val fileName = game.rommFileName ?: game.localPath?.substringAfterLast("/") ?: return null
        val parsed = RomFileNameParser.parse(fileName)
        val parsedRegion = parsed.region ?: return null
        val parsedVersion = parsed.version.orEmpty()
        val match = cheatDao.getVariantsForGame(gameId)
            .filter {
                it.variantRegion.equals(parsedRegion, ignoreCase = true) &&
                    it.variantVersion.equals(parsedVersion, ignoreCase = true)
            }
            .singleOrNull() ?: return null
        gameDao.updateCheatsSelectedVariant(gameId, match.variantRegion, match.variantVersion)
        Logger.debug(TAG, "Inferred cheat variant for game=$gameId from '$fileName': ${match.variantRegion} ${match.variantVersion}")
        return match.variantRegion to match.variantVersion
    }

    private fun mapPlatformSlug(platform: String): String? {
        return when (platform.lowercase()) {
            "nes", "nintendo_nes", "famicom" -> "nes"
            "snes", "super_nes", "sfc" -> "snes"
            "gb", "game_boy", "gameboy" -> "gb"
            "gbc", "game_boy_color" -> "gbc"
            "gba", "game_boy_advance" -> "gba"
            "n64", "nintendo_64" -> "n64"
            "nds", "nintendo_ds" -> "nds"
            "fds", "famicom_disk_system" -> "fds"
            "genesis", "mega_drive", "megadrive", "sega_genesis", "sega_megadrive" -> "genesis"
            "sms", "master_system", "sega_master_system" -> "sms"
            "gg", "game_gear", "sega_game_gear" -> "gg"
            "sega32x", "32x" -> "32x"
            "segacd", "mega_cd", "sega_cd" -> "segacd"
            "dc", "dreamcast", "sega_dreamcast" -> "dc"
            "saturn", "sega_saturn" -> "saturn"
            "psx", "ps1", "playstation", "sony_playstation" -> "psx"
            "psp", "playstation_portable", "sony_psp" -> "psp"
            "tg16", "pc_engine", "turbografx_16", "pce" -> "tg16"
            "tgcd", "pce_cd", "pc_engine_cd" -> "tgcd"
            "atari2600", "atari_2600" -> "atari2600"
            "atari7800", "atari_7800" -> "atari7800"
            "lynx", "atari_lynx" -> "lynx"
            else -> null
        }
    }

    private fun getOrCreateDeviceToken(): String {
        val prefs = context.getSharedPreferences("cheats_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_token", null)
        if (existing != null) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }

        val token = if (androidId.length >= 8) {
            androidId
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }

        prefs.edit().putString("device_token", token).apply()
        return token
    }
}
