package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.model.orderedForEveryGame
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PLATFORM_ROW_LIMIT = 20
private const val LEADING_PAGE_SIZE = 20
private const val DISCOVERY_BATCH = 20

private data class PresentedRow(
    val games: List<HomeGameUi>,
    val byId: Map<Long, HomeGameUi>
)

/**
 * Loads one platform's games for a home row. Both displays load through this, so a platform row
 * orders, caps and repairs the same way on either screen.
 */
class PlatformGameLoader(
    private val gameRepository: GameRepository,
    private val downloadFileStatusRepository: DownloadFileStatusRepository
) {

    /**
     * Hands the row to [publish], which answers whether the row is still wanted; false ends the load.
     *
     * A capped row publishes once. A row carrying every game publishes its leading page first when
     * [publishLeadingPage] is set, in the order the whole list will show, then the whole list with
     * `complete` true. Stale local paths among the loaded games are repaired last, and the row is
     * published again only when a repair changed a game.
     */
    suspend fun load(
        platformId: Long,
        showsEveryGame: Boolean,
        installedOnly: Boolean,
        publishLeadingPage: Boolean,
        toUi: suspend (GameEntity, ResolvedGameArt?) -> HomeGameUi,
        publish: (games: List<HomeGameUi>, complete: Boolean) -> Boolean
    ) {
        if (!showsEveryGame) {
            val entities = gameRepository.getByPlatformSorted(platformId, limit = PLATFORM_ROW_LIMIT)
            val row = present(entities, installedOnly, ordered = false, toUi = toUi)
            if (!publish(row.games, true)) return
            repair(entities, row, installedOnly, ordered = false, toUi = toUi, publish = publish)
            return
        }

        val leading = gameRepository.getByPlatformTitleOrdered(platformId, LEADING_PAGE_SIZE)
        val leadingIsWhole = leading.size < LEADING_PAGE_SIZE
        val leadingRow = if (publishLeadingPage) {
            present(leading, installedOnly, ordered = true, toUi = toUi)
        } else {
            null
        }
        if (leadingRow != null && !publish(leadingRow.games, leadingIsWhole)) return

        val entities = if (leadingIsWhole) {
            leading
        } else {
            leading + gameRepository.getByPlatformTitleOrdered(
                platformId,
                limit = Int.MAX_VALUE,
                offset = LEADING_PAGE_SIZE
            )
        }
        val row = if (leadingIsWhole && leadingRow != null) {
            leadingRow
        } else {
            val whole = present(
                entities,
                installedOnly,
                ordered = true,
                toUi = toUi,
                reuse = leadingRow?.byId.orEmpty()
            )
            if (!publish(whole.games, true)) return
            whole
        }
        repair(entities, row, installedOnly, ordered = true, toUi = toUi, publish = publish)
    }

    /**
     * Re-resolves up to a batch of games whose local file is missing, returning the ids it asked the
     * repository to re-check. Store-managed games and games with no server copy are never candidates.
     */
    suspend fun discoverStalePaths(games: List<GameEntity>): List<Long> = withContext(Dispatchers.IO) {
        val stale = mutableListOf<Long>()
        for (game in games) {
            if (stale.size == DISCOVERY_BATCH) break
            if (game.source == GameSource.STEAM || game.source == GameSource.ANDROID_APP) continue
            if (game.rommId == null) continue
            val path = game.localPath
            if (path != null && downloadFileStatusRepository.pathExists(path)) continue
            stale += game.id
        }
        stale.forEach { gameRepository.validateAndDiscoverGame(it) }
        stale
    }

    private suspend fun repair(
        entities: List<GameEntity>,
        row: PresentedRow,
        installedOnly: Boolean,
        ordered: Boolean,
        toUi: suspend (GameEntity, ResolvedGameArt?) -> HomeGameUi,
        publish: (games: List<HomeGameUi>, complete: Boolean) -> Boolean
    ) {
        val checked = discoverStalePaths(entities).toSet()
        if (checked.isEmpty()) return
        val loadedById = entities.associateBy { it.id }
        val changed = gameRepository.getByIds(checked.toList())
            .filter { it != loadedById[it.id] }
            .associateBy { it.id }
        if (changed.isEmpty()) return
        val candidates = entities
            .filter { it.id in row.byId || it.id in changed }
            .map { changed[it.id] ?: it }
        val repaired = present(
            candidates,
            installedOnly,
            ordered = ordered,
            toUi = toUi,
            reuse = row.byId - changed.keys
        )
        publish(repaired.games, true)
    }

    private suspend fun present(
        entities: List<GameEntity>,
        installedOnly: Boolean,
        ordered: Boolean,
        toUi: suspend (GameEntity, ResolvedGameArt?) -> HomeGameUi,
        reuse: Map<Long, HomeGameUi> = emptyMap()
    ): PresentedRow = withContext(Dispatchers.Default) {
        val byId = LinkedHashMap<Long, HomeGameUi>(entities.size)
        val art = gameRepository.getArt(entities.map { it.id }.filterNot { it in reuse })
        for (entity in entities) {
            val known = reuse[entity.id]
            if (known != null) {
                byId[entity.id] = known
                continue
            }
            if (installedOnly && !downloadFileStatusRepository.isContentAvailable(entity)) continue
            byId[entity.id] = toUi(entity, art[entity.id])
        }
        val games = byId.values.toList()
        PresentedRow(
            games = if (ordered) orderedForEveryGame(games, HomeGameUiSortProps) else games,
            byId = byId
        )
    }
}
