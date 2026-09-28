package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileEpisodeEntity
import com.nendo.argosy.data.local.entity.HomeTileTarget
import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.firstFreeScrollRect
import com.nendo.argosy.domain.model.minimumSpanFor
import com.nendo.argosy.domain.model.reflowScrollTiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The custom home grid's tiles. Placement rules live in the domain model; this layer only stores
 * and resolves.
 */
@Singleton
class HomeTileRepository @Inject constructor(
    private val homeTileDao: HomeTileDao
) {

    /**
     * The stored tiles, each carrying whatever run was chosen for it.
     *
     * The two tables are read together rather than a tile being asked for its episodes when one is
     * drawn: a page holds a handful of tiles and the chosen runs are a handful of rows, so one join
     * on every emission is cheaper than a query per tile and, more importantly, keeps a tile and its
     * run arriving as one value instead of a tile that briefly has no run.
     */
    fun observeTiles(ownerUserId: Long?, kind: HomeGridKind): Flow<List<HomeTile>> =
        combine(
            homeTileDao.observeTiles(ownerUserId, kind.name),
            homeTileDao.observeAllEpisodes()
        ) { rows, episodes ->
            val runs = episodes.groupBy { it.tileId }
            rows.map { row ->
                row.toDomain(
                    runs[row.id].orEmpty().sortedBy { it.orderIndex }.map { it.itemId }
                )
            }
        }

    suspend fun pageCount(ownerUserId: Long?): Int =
        (homeTileDao.getMaxPageIndex(ownerUserId, HomeGridKind.PAGED.name)?.plus(1) ?: 0)
            .coerceAtLeast(DEFAULT_PAGE_COUNT)

    /**
     * Stores a tile on the [kind] grid and, when it was given one, the run it should play. [rect] is
     * raised to the floor the target's kind imposes before it is written. A scrolling grid has no
     * pages, so its tiles are stored on page 0 whatever [pageIndex] says.
     */
    suspend fun place(
        ownerUserId: Long?,
        kind: HomeGridKind,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef,
        playlist: List<String> = emptyList()
    ): Long {
        val id = homeTileDao.insert(entityFor(ownerUserId, kind, pageIndex, rect, target))
        writePlaylist(id, playlist)
        return id
    }

    /**
     * Rewrites only the stored row's page and rectangle; every other column keeps its stored value.
     * [rect] is raised to the target's minimum span, and a scrolling tile stays on page 0.
     */
    suspend fun move(
        tile: HomeTile,
        rect: TileRect,
        pageIndex: Int = tile.pageIndex
    ) {
        val row = homeTileDao.getById(tile.id) ?: return
        homeTileDao.update(row.placedAt(pageIndex, rect.atLeast(tile.minSpan)))
    }

    /**
     * Repacks the scrolling grid's tiles from [from] onto [to] and writes every moved row in one
     * update. Paged tiles are never read or written, and nothing happens unless both layouts scroll
     * and differ in scroll axis or lane count.
     */
    suspend fun reflowScroll(ownerUserId: Long?, from: CustomGridLayout, to: CustomGridLayout) {
        if (!from.needsScrollReflowTo(to)) return
        val rows = homeTileDao.getPage(ownerUserId, HomeGridKind.SCROLL.name, 0)
        val stored = rows.associateBy { it.id }
        val moved = reflowScrollTiles(rows.map { it.toDomain(emptyList()) }, from, to)
            .mapNotNull { tile ->
                stored[tile.id]?.takeIf { it.rect() != tile.rect }?.placedAt(0, tile.rect)
            }
        if (moved.isNotEmpty()) homeTileDao.updateAll(moved)
    }

    suspend fun setCoverScale(tileId: Long, scale: TileCoverScale) {
        val row = homeTileDao.getById(tileId) ?: return
        homeTileDao.update(row.copy(coverScale = scale.name))
    }

    /**
     * Changes what a stored tile points at, reading its page, rectangle and cover scale from the
     * stored row. The span only grows when the new target's minimum demands it. The playlist is
     * replaced, not merged.
     */
    suspend fun retarget(tileId: Long, target: HomeTileTargetRef, playlist: List<String>) {
        val row = homeTileDao.getById(tileId) ?: return
        homeTileDao.update(
            entityFor(
                ownerUserId = row.ownerUserId,
                kind = gridKindOf(row),
                pageIndex = row.pageIndex,
                rect = row.rect(),
                target = target,
                coverScale = TileCoverScale.fromString(row.coverScale)
            ).copy(id = tileId, pageId = row.pageId, artStyle = row.artStyle, createdAt = row.createdAt)
        )
        homeTileDao.replaceEpisodes(
            tileId,
            playlist.mapIndexed { index, itemId ->
                HomeTileEpisodeEntity(tileId = tileId, itemId = itemId, orderIndex = index)
            }
        )
    }

    suspend fun remove(tileId: Long) = homeTileDao.deleteTileWithEpisodes(tileId)

    private suspend fun writePlaylist(tileId: Long, playlist: List<String>) {
        if (playlist.isEmpty()) return
        homeTileDao.replaceEpisodes(
            tileId,
            playlist.mapIndexed { index, itemId ->
                HomeTileEpisodeEntity(tileId = tileId, itemId = itemId, orderIndex = index)
            }
        )
    }

    /**
     * Removes a page and closes the gap behind it. The tiles go; nothing they pointed at is
     * touched, which is why the confirmation this sits behind says the games stay on the device.
     */
    suspend fun removePage(ownerUserId: Long?, pageIndex: Int) {
        val kind = HomeGridKind.PAGED.name
        homeTileDao.deleteEpisodesForPage(ownerUserId, kind, pageIndex)
        homeTileDao.deletePage(ownerUserId, kind, pageIndex)
        homeTileDao.shiftPagesDown(ownerUserId, kind, pageIndex)
    }

    suspend fun pruneMissingGames() = homeTileDao.deleteTilesForMissingGames()

    /**
     * Places [target] on the grid [layout] describes. A paged grid takes the first free spot of its
     * last page, reading left to right then down, or the top-left of a new page when that page is
     * full. A scrolling grid takes the first free spot scanning along its scroll axis.
     */
    suspend fun append(
        ownerUserId: Long?,
        target: HomeTileTargetRef,
        layout: CustomGridLayout,
        playlist: List<String> = emptyList()
    ): Long {
        val axis = layout.scrollAxis
        if (axis != null) {
            val taken = homeTileDao.getPage(ownerUserId, HomeGridKind.SCROLL.name, 0).map { it.rect() }
            val span = minimumSpanFor(target)
            val free = firstFreeScrollRect(taken, layout.lanes, axis, span, span)
            return place(ownerUserId, HomeGridKind.SCROLL, 0, free, target, playlist)
        }
        val kind = HomeGridKind.PAGED
        val lastPage = (homeTileDao.getMaxPageIndex(ownerUserId, kind.name) ?: 0).coerceAtLeast(0)
        val taken = homeTileDao.getPage(ownerUserId, kind.name, lastPage).map { it.rect() }
        val free = firstFreeRect(taken, layout.shape, minimumSpanFor(target))
        return if (free != null) {
            place(ownerUserId, kind, lastPage, free, target, playlist)
        } else {
            place(ownerUserId, kind, lastPage + 1, TileRect(0, 0), target, playlist)
        }
    }

    private fun entityFor(
        ownerUserId: Long?,
        kind: HomeGridKind,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef,
        coverScale: TileCoverScale = TileCoverScale.CROP
    ): HomeTileEntity {
        val sized = rect.atLeast(minimumSpanFor(target))
        val base = HomeTileEntity(
            ownerUserId = ownerUserId,
            gridKind = kind.name,
            pageIndex = if (kind == HomeGridKind.SCROLL) 0 else pageIndex,
            coverScale = coverScale.name,
            columnIndex = sized.columnIndex,
            rowIndex = sized.rowIndex,
            columnSpan = sized.columnSpan,
            rowSpan = sized.rowSpan,
            targetType = target.storedType()
        )
        return when (target) {
            is HomeTileTargetRef.Game -> base.copy(gameId = target.gameId)
            is HomeTileTargetRef.Collection -> base.copy(
                collectionId = target.collectionId,
                gameId = target.focusGameId
            )
            is HomeTileTargetRef.VirtualCollection ->
                base.copy(virtualType = target.type, virtualName = target.name)
            is HomeTileTargetRef.App -> base.copy(packageName = target.packageName)
            is HomeTileTargetRef.Media -> base.copy(
                mediaItemId = target.itemId,
                mediaPlayMode = target.playMode.name,
                mediaScopeId = target.scopeId
            )
            is HomeTileTargetRef.LocalMedia -> base.copy(mediaFilePath = target.filePath)
            is HomeTileTargetRef.Feature -> base.copy(
                featureKind = target.kind.name,
                featureConfig = encodeFeatureConfig(target)
            )
            HomeTileTargetRef.Unresolvable -> base
        }
    }

    suspend fun updateFeaturePick(tileId: Long, gameId: Long?) {
        val row = homeTileDao.getById(tileId) ?: return
        val target = row.resolveTarget() as? HomeTileTargetRef.Feature ?: return
        homeTileDao.update(
            row.copy(featureConfig = encodeFeatureConfig(target.copy(pickedGameId = gameId)))
        )
    }

    companion object {
        const val DEFAULT_PAGE_COUNT = 2
    }
}

private fun firstFreeRect(taken: List<TileRect>, shape: CustomGridShape, span: Int): TileRect? {
    for (row in 0 until shape.rows) {
        for (column in 0 until shape.columns) {
            val candidate = TileRect(column, row).atLeast(span)
            if (candidate.withinBounds(shape.columns, shape.rows) && taken.none { it.overlaps(candidate) }) {
                return candidate
            }
        }
    }
    return null
}

private fun HomeTileTargetRef.storedType(): String = when (this) {
    is HomeTileTargetRef.Game -> HomeTileTarget.GAME.name
    is HomeTileTargetRef.Collection -> HomeTileTarget.COLLECTION.name
    is HomeTileTargetRef.VirtualCollection -> HomeTileTarget.VIRTUAL_COLLECTION.name
    is HomeTileTargetRef.App -> HomeTileTarget.APP.name
    is HomeTileTargetRef.Media -> HomeTileTarget.MEDIA.name
    is HomeTileTargetRef.LocalMedia -> HomeTileTarget.MEDIA.name
    is HomeTileTargetRef.Feature -> HomeTileTarget.FEATURE.name
    HomeTileTargetRef.Unresolvable -> ""
}

private const val KEY_DOWNLOADED_ONLY = "downloadedOnly"
private const val KEY_NEVER_PLAYED = "neverPlayed"
private const val KEY_PLATFORM_IDS = "platformIds"
private const val KEY_GENRES = "genres"
private const val KEY_PICKED_GAME_ID = "pickedGameId"
private const val KEY_SERIES = "series"
private const val KEY_SOURCE = "source"
private const val KEY_PLAYERS = "players"
private const val KEY_SORT = "sort"
private const val KEY_SORT_DESCENDING = "sortDescending"

private fun encodeFeatureConfig(target: HomeTileTargetRef.Feature): String =
    if (target.kind == FeatureTileKind.LIBRARY_LINK) {
        encodeLibraryLink(target.libraryLink ?: LibraryLinkFilters())
    } else {
        JSONObject().apply {
            put(KEY_DOWNLOADED_ONLY, target.filters.downloadedOnly)
            put(KEY_NEVER_PLAYED, target.filters.neverPlayed)
            put(KEY_PLATFORM_IDS, JSONArray(target.filters.platformIds.toList()))
            put(KEY_GENRES, JSONArray(target.filters.genres.toList()))
            target.pickedGameId?.let { put(KEY_PICKED_GAME_ID, it) }
        }.toString()
    }

private fun encodeLibraryLink(filters: LibraryLinkFilters): String =
    JSONObject().apply {
        put(KEY_SOURCE, filters.source.name)
        put(KEY_PLATFORM_IDS, JSONArray(filters.platformIds.toList()))
        put(KEY_GENRES, JSONArray(filters.genres.toList()))
        put(KEY_SERIES, JSONArray(filters.series.toList()))
        filters.players?.let { put(KEY_PLAYERS, it.name) }
        put(KEY_SORT, filters.sort.option.name)
        put(KEY_SORT_DESCENDING, filters.sort.descending)
    }.toString()

private fun decodeLibraryLink(json: JSONObject): LibraryLinkFilters {
    val sortOption = SortOption.entries.find { it.name == json.optString(KEY_SORT) }
        ?: SortOption.TITLE
    return LibraryLinkFilters(
        source = SourceFilter.fromString(json.optString(KEY_SOURCE)) ?: SourceFilter.ALL,
        platformIds = json.longSet(KEY_PLATFORM_IDS),
        genres = json.stringSet(KEY_GENRES),
        series = json.stringSet(KEY_SERIES),
        players = PlayerCountBucket.entries.find { it.name == json.optString(KEY_PLAYERS) },
        sort = ActiveSort(
            option = sortOption,
            descending = json.optBoolean(KEY_SORT_DESCENDING, sortOption.defaultDescending)
        )
    )
}

private fun JSONObject.longSet(key: String): Set<Long> =
    optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getLong(it) } }
        .orEmpty()
        .toSet()

private fun JSONObject.stringSet(key: String): Set<String> =
    optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } }
        .orEmpty()
        .toSet()

private fun decodeFeature(kind: FeatureTileKind, config: String?): HomeTileTargetRef.Feature {
    val json = config?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: return HomeTileTargetRef.Feature(
            kind,
            libraryLink = LibraryLinkFilters().takeIf { kind == FeatureTileKind.LIBRARY_LINK }
        )
    if (kind == FeatureTileKind.LIBRARY_LINK) {
        return HomeTileTargetRef.Feature(kind, libraryLink = decodeLibraryLink(json))
    }
    val platformIds = json.optJSONArray(KEY_PLATFORM_IDS)
        ?.let { array -> (0 until array.length()).map { array.getLong(it) } }
        .orEmpty()
        .toSet()
    val genres = json.optJSONArray(KEY_GENRES)
        ?.let { array -> (0 until array.length()).map { array.getString(it) } }
        .orEmpty()
        .toSet()
    return HomeTileTargetRef.Feature(
        kind = kind,
        filters = RandomTileFilters(
            downloadedOnly = json.optBoolean(KEY_DOWNLOADED_ONLY, true),
            neverPlayed = json.optBoolean(KEY_NEVER_PLAYED, false),
            platformIds = platformIds,
            genres = genres
        ),
        pickedGameId = if (json.has(KEY_PICKED_GAME_ID)) json.getLong(KEY_PICKED_GAME_ID) else null
    )
}

private fun HomeTileEntity.rect(): TileRect = TileRect(columnIndex, rowIndex, columnSpan, rowSpan)

private fun HomeTileEntity.placedAt(pageIndex: Int, rect: TileRect): HomeTileEntity = copy(
    pageIndex = if (gridKindOf(this) == HomeGridKind.SCROLL) 0 else pageIndex,
    columnIndex = rect.columnIndex,
    rowIndex = rect.rowIndex,
    columnSpan = rect.columnSpan,
    rowSpan = rect.rowSpan
)

private fun gridKindOf(row: HomeTileEntity): HomeGridKind =
    HomeGridKind.entries.firstOrNull { it.name == row.gridKind } ?: HomeGridKind.PAGED

private fun HomeTileEntity.toDomain(playlist: List<String>): HomeTile = HomeTile(
    id = id,
    pageIndex = pageIndex,
    rect = rect(),
    target = resolveTarget(),
    playlist = playlist,
    coverScale = TileCoverScale.fromString(coverScale),
    kind = gridKindOf(this)
)

/**
 * A row whose target column is missing, or whose type this build does not know, resolves to
 * unresolvable rather than throwing: a tile written by a newer build should leave a visible gap on
 * an older one, not stop the page loading.
 *
 * A media row naming a file on this device is read as that file before it is read as a library
 * title. The two share a stored type because both are media and only one of the two columns is ever
 * filled, so the path is what distinguishes them rather than a type an older build could not name.
 *
 * A play mode this build cannot read falls back to the single-title reading rather than making the
 * whole tile unresolvable: the item it points at is still known, and playing that is a better answer
 * than a gap on the page.
 */
private fun HomeTileEntity.resolveTarget(): HomeTileTargetRef =
    when (runCatching { HomeTileTarget.valueOf(targetType) }.getOrNull()) {
        HomeTileTarget.GAME -> gameId?.let { HomeTileTargetRef.Game(it) }
        HomeTileTarget.COLLECTION -> collectionId?.let {
            HomeTileTargetRef.Collection(it, focusGameId = gameId)
        }
        HomeTileTarget.VIRTUAL_COLLECTION -> virtualType?.let { type ->
            HomeTileTargetRef.VirtualCollection(type, virtualName.orEmpty())
        }
        HomeTileTarget.APP -> packageName?.let { HomeTileTargetRef.App(it) }
        HomeTileTarget.MEDIA -> mediaFilePath?.takeIf { it.isNotBlank() }
            ?.let { HomeTileTargetRef.LocalMedia(it) }
            ?: mediaItemId?.let {
                HomeTileTargetRef.Media(
                    itemId = it,
                    playMode = MediaTilePlayMode.fromStored(mediaPlayMode)
                        ?: MediaTilePlayMode.SINGLE,
                    scopeId = mediaScopeId
                )
            }
        HomeTileTarget.FEATURE -> FeatureTileKind.fromStored(featureKind)?.let { kind ->
            decodeFeature(kind, featureConfig)
        }
        null -> null
    } ?: HomeTileTargetRef.Unresolvable
