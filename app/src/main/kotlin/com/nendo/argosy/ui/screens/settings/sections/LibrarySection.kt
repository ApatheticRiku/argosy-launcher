package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.MultiSelectPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal const val LIBRARY_SOURCE_ALL = "ALL"

internal data class LibraryPlatformOption(val id: Long, val name: String)

internal data class LibraryLayoutState(
    val platforms: List<LibraryPlatformOption>,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID
) {
    companion object {
        fun from(state: SettingsUiState) = LibraryLayoutState(
            platforms = state.emulators.platforms
                .filter { it.platform.syncEnabled }
                .map { LibraryPlatformOption(it.platform.id, it.platform.getDisplayName()) }
                .sortedBy { it.name },
            libraryLayout = state.display.libraryLayout
        )
    }
}

internal sealed class LibraryItem(
    val key: String,
    val section: String,
    val visibleWhen: (LibraryLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val titleRes: Int) : LibraryItem(key, section)

    data object LayoutItem : LibraryItem("libraryLayout", "layout")
    data object GridDensityItem : LibraryItem(
        key = "libraryGridDensity",
        section = "layout",
        visibleWhen = { it.libraryLayout == LibraryLayout.GRID }
    )
    data object DefaultSort : LibraryItem("libraryDefaultSort", "defaults")
    data object InstalledFirst : LibraryItem("sortInstalledFirst", "defaults")
    data object FavoritesFirst : LibraryItem("sortFavoritesFirst", "defaults")
    data object DefaultPlatform : LibraryItem("libraryDefaultPlatform", "defaults")
    data object DefaultSource : LibraryItem("libraryDefaultSource", "defaults")
    data object DefaultRegion : LibraryItem("libraryDefaultRegion", "defaults")
    data object DefaultPlayers : LibraryItem("libraryDefaultPlayers", "defaults")

    companion object {
        val ALL: List<LibraryItem>
            get() = listOf(
                Header("libraryLayoutHeader", "layout", R.string.settings_library_section_layout),
                LayoutItem,
                GridDensityItem,
                Header("libraryDefaultsHeader", "defaults", R.string.settings_library_section_defaults),
                DefaultSort, InstalledFirst, FavoritesFirst, DefaultPlatform, DefaultSource,
                DefaultRegion, DefaultPlayers
            )
    }
}

private val libraryLayout = SettingsLayout<LibraryItem, LibraryLayoutState>(
    allItems = LibraryItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "layout" -> R.string.settings_library_section_layout
            "defaults" -> R.string.settings_library_section_defaults
            else -> null
        }
    }
)

internal fun libraryMaxFocusIndex(state: LibraryLayoutState): Int = libraryLayout.maxFocusIndex(state)

internal fun libraryItemAtFocusIndex(index: Int, state: LibraryLayoutState): LibraryItem? =
    libraryLayout.itemAtFocusIndex(index, state)

internal fun librarySections(state: LibraryLayoutState) = libraryLayout.buildSections(state)

internal fun librarySortLabel(
    context: android.content.Context,
    option: SortOption,
    descending: Boolean
): String = context.getString(
    if (descending) {
        R.string.settings_library_default_sort_value_descending
    } else {
        R.string.settings_library_default_sort_value_ascending
    },
    context.getString(option.labelRes)
)

internal fun librarySortOptions(context: android.content.Context): List<String> =
    SortOption.entries.flatMap { option ->
        listOf(librarySortLabel(context, option, false), librarySortLabel(context, option, true))
    }

internal fun libraryPlatformTokens(state: LibraryLayoutState): List<Long?> =
    listOf(null) + state.platforms.map { it.id }

private fun libraryPlatformLabels(context: android.content.Context, state: LibraryLayoutState): List<String> =
    listOf(context.getString(R.string.settings_library_default_platform_all)) + state.platforms.map { it.name }

internal fun libraryPlayerTokens(): List<PlayerCountBucket?> = listOf(null) + PlayerCountBucket.entries

private fun libraryPlayersLabelRes(bucket: PlayerCountBucket?): Int = when (bucket) {
    null -> R.string.settings_library_default_players_any
    PlayerCountBucket.ONE -> R.string.settings_library_default_players_one
    PlayerCountBucket.TWO -> R.string.settings_library_default_players_two
    PlayerCountBucket.THREE -> R.string.settings_library_default_players_three
    PlayerCountBucket.FOUR_PLUS -> R.string.settings_library_default_players_four_plus
}

internal fun librarySourceOptions(context: android.content.Context): List<String> = listOf(
    context.getString(R.string.source_filter_all),
    context.getString(R.string.source_filter_playable),
    context.getString(R.string.source_filter_favorites)
)

private fun libraryLayoutLabelRes(layout: LibraryLayout): Int = when (layout) {
    LibraryLayout.GRID -> R.string.settings_library_layout_grid
    LibraryLayout.LIST -> R.string.settings_library_layout_list
}

private fun gridDensityLabelRes(density: GridDensity): Int = when (density) {
    GridDensity.COMPACT -> R.string.settings_library_grid_density_compact
    GridDensity.NORMAL -> R.string.settings_library_grid_density_normal
    GridDensity.SPACIOUS -> R.string.settings_library_grid_density_spacious
}

internal fun librarySourceKeys(): List<String> = listOf(LIBRARY_SOURCE_ALL, "PLAYABLE", "FAVORITES")

@Composable
private fun regionSummary(selected: Set<String>): String = when (selected.size) {
    0 -> stringResource(R.string.settings_library_default_region_any)
    1 -> selected.first()
    else -> pluralStringResource(R.plurals.settings_library_default_region_count, selected.size, selected.size)
}

@Composable
fun LibrarySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current
    val layoutState = remember(uiState.emulators.platforms, display.libraryLayout) {
        LibraryLayoutState.from(uiState)
    }

    val visibleItems = remember(layoutState) { libraryLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { libraryLayout.buildSections(layoutState, context) }

    fun isFocused(item: LibraryItem): Boolean =
        uiState.focusedIndex == libraryLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: LibraryItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    val sortOption = SortOption.entries.firstOrNull { it.name == display.libraryDefaultSort }
        ?: SortOption.TITLE
    val sortDescending = display.libraryDefaultSortDescending ?: sortOption.defaultDescending
    val platformTokens = remember(layoutState) { libraryPlatformTokens(layoutState) }
    val platformLabels = remember(layoutState, context) { libraryPlatformLabels(context, layoutState) }
    val platformIndex = platformTokens.indexOf(display.libraryDefaultPlatformId).coerceAtLeast(0)
    val sourceIndex = librarySourceKeys().indexOf(display.libraryDefaultSource).coerceAtLeast(0)
    val regionOptions = display.libraryRegionOptions
    val playerTokens = libraryPlayerTokens()

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { libraryLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is LibraryItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is LibraryItem.Header ->
                com.nendo.argosy.ui.screens.settings.components.SectionHeader(
                    stringResource(item.titleRes)
                )

            LibraryItem.LayoutItem -> CyclePreference(
                title = stringResource(R.string.settings_library_layout_title),
                value = stringResource(libraryLayoutLabelRes(display.libraryLayout)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryLayout(1) },
                onPrev = { viewModel.cycleLibraryLayout(-1) },
                options = remember(context) {
                    LibraryLayout.entries.map { l -> context.getString(libraryLayoutLabelRes(l)) }
                },
                onSelect = { viewModel.setLibraryLayout(LibraryLayout.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.GridDensityItem -> CyclePreference(
                title = stringResource(R.string.settings_library_grid_density_title),
                value = stringResource(gridDensityLabelRes(display.gridDensity)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleGridDensity(1) },
                onPrev = { viewModel.cycleGridDensity(-1) },
                options = remember(context) {
                    GridDensity.entries.map { d -> context.getString(gridDensityLabelRes(d)) }
                },
                onSelect = { viewModel.setGridDensity(GridDensity.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSort -> CyclePreference(
                title = stringResource(R.string.settings_library_default_sort_title),
                value = librarySortLabel(context, sortOption, sortDescending),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSort(1) },
                onPrev = { viewModel.cycleLibraryDefaultSort(-1) },
                options = librarySortOptions(context),
                onSelect = { viewModel.setLibraryDefaultSortIndex(it) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.InstalledFirst -> SwitchPreference(
                title = stringResource(R.string.settings_library_installed_first_title),
                subtitle = stringResource(R.string.settings_library_installed_first_subtitle),
                isEnabled = display.sortInstalledFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortInstalledFirst(!display.sortInstalledFirst) }
            )

            LibraryItem.FavoritesFirst -> SwitchPreference(
                title = stringResource(R.string.settings_library_favorites_first_title),
                subtitle = stringResource(R.string.settings_library_favorites_first_subtitle),
                isEnabled = display.sortFavoritesFirst,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSortFavoritesFirst(!display.sortFavoritesFirst) }
            )

            LibraryItem.DefaultPlatform -> CyclePreference(
                title = stringResource(R.string.settings_library_default_platform_title),
                value = platformLabels[platformIndex],
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlatform(1, platformTokens) },
                onPrev = { viewModel.cycleLibraryDefaultPlatform(-1, platformTokens) },
                options = platformLabels,
                onSelect = { viewModel.setLibraryDefaultPlatform(platformTokens[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultSource -> CyclePreference(
                title = stringResource(R.string.settings_library_default_source_title),
                value = librarySourceOptions(context).getOrElse(sourceIndex) {
                    stringResource(R.string.source_filter_all)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultSource(1) },
                onPrev = { viewModel.cycleLibraryDefaultSource(-1) },
                options = remember(context) { librarySourceOptions(context) },
                onSelect = { viewModel.setLibraryDefaultSource(librarySourceKeys()[it]) },
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultRegion -> MultiSelectPreference(
                title = stringResource(R.string.settings_library_default_region_title),
                value = regionSummary(display.libraryDefaultRegions),
                isFocused = isFocused(item),
                options = regionOptions,
                selected = remember(regionOptions, display.libraryDefaultRegions) {
                    regionOptions.indices.filter { regionOptions[it] in display.libraryDefaultRegions }.toSet()
                },
                onToggle = { viewModel.toggleLibraryDefaultRegion(regionOptions[it]) },
                emptyText = stringResource(R.string.settings_library_default_region_none_available),
                pickerRequestToken = pickerToken(item)
            )

            LibraryItem.DefaultPlayers -> CyclePreference(
                title = stringResource(R.string.settings_library_default_players_title),
                value = stringResource(libraryPlayersLabelRes(display.libraryDefaultPlayers)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleLibraryDefaultPlayers(1) },
                onPrev = { viewModel.cycleLibraryDefaultPlayers(-1) },
                options = remember(context) {
                    playerTokens.map { context.getString(libraryPlayersLabelRes(it)) }
                },
                onSelect = { viewModel.setLibraryDefaultPlayers(playerTokens[it]) },
                pickerRequestToken = pickerToken(item)
            )
        }
    }
}
