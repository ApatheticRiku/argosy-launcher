package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private fun pathOnDisk(path: String?): Boolean = path != null && File(path).exists()

private val GameFileEntity.isOnDisk: Boolean get() = pathOnDisk(localPath)

private val GameEntity.primaryFileName: String?
    get() = rommFileName ?: localPath?.substringAfterLast('/')

/**
 * True when this file may run in place of [game]'s own rom as a variant session: it belongs to
 * [game], the platform offers variants, both the row and its category are launch targets, and it
 * is not [game]'s primary file.
 */
fun GameFileEntity.isLaunchableVariantOf(game: GameEntity): Boolean =
    gameId == game.id &&
        game.platformSlug !in VariantCategory.VARIANT_EXCLUDED_PLATFORMS &&
        isLaunchTarget &&
        VariantCategory.fromKey(category).isLaunchTarget &&
        fileName != game.primaryFileName &&
        (localPath == null || localPath != game.localPath)

data class VariantOption(
    val fileId: Long?,
    val fileName: String,
    val category: String,
    val isDownloaded: Boolean,
    val isMultiDisc: Boolean,
    val fileSizeBytes: Long
)

@Singleton
class VariantResolver @Inject constructor(
    private val gameFileDao: GameFileDao
) {
    suspend fun resolveVariant(game: GameEntity): GameFileEntity? =
        listOfNotNull(game.activeVariantFileId, game.lastPlayedFileId).firstNotNullOfOrNull { fileId ->
            gameFileDao.getById(fileId)?.takeIf { it.isLaunchableVariantOf(game) && it.isOnDisk }
        }

    suspend fun getVariantOptions(game: GameEntity): List<VariantOption>? {
        val variants = gameFileDao.getVariantsForGame(game.id).filter { it.isLaunchableVariantOf(game) }
        if (variants.isEmpty()) return null

        val primary = VariantOption(
            fileId = null,
            fileName = game.primaryFileName ?: game.title,
            category = VariantCategory.GAME.key,
            isDownloaded = pathOnDisk(game.localPath),
            isMultiDisc = game.isMultiDisc,
            fileSizeBytes = game.fileSizeBytes ?: 0
        )
        return listOf(primary) + variants.map { file ->
            VariantOption(
                fileId = file.id,
                fileName = file.fileName,
                category = file.category,
                isDownloaded = file.isOnDisk,
                isMultiDisc = file.isMultiDisc,
                fileSizeBytes = file.fileSize
            )
        }
    }
}
