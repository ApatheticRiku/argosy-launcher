package com.nendo.argosy.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPlatformScopeTest {

    private val wii = PlatformContext(9, "Wii", "wii")
    private val gc = PlatformContext(4, "Nintendo GameCube", "ngc")

    @Test
    fun `built-in settings on the global context name no platform`() {
        val state = SettingsUiState(
            currentSection = SettingsSection.BUILTIN_CONTROLS,
            builtinVideo = BuiltinVideoState(availablePlatforms = listOf(gc, wii), platformContextIndex = 0)
        )
        assertNull(settingsPlatformScope(state))
    }

    @Test
    fun `built-in settings on a platform scope to that platform`() {
        val state = SettingsUiState(
            currentSection = SettingsSection.BUILTIN_VIDEO,
            builtinVideo = BuiltinVideoState(availablePlatforms = listOf(gc, wii), platformContextIndex = 2)
        )
        assertEquals(SettingsPlatformScope(9, "Wii"), settingsPlatformScope(state))
    }

    @Test
    fun `core options always scope to the selected platform`() {
        val state = SettingsUiState(
            currentSection = SettingsSection.CORE_OPTIONS,
            coreOptions = CoreOptionsState(platformContextIndex = 0, availablePlatforms = listOf(gc, wii))
        )
        assertEquals(SettingsPlatformScope(4, "Nintendo GameCube"), settingsPlatformScope(state))
    }

    @Test
    fun `a platform's storage games scope to it, and none selected scopes to nothing`() {
        val selected = SettingsUiState(
            currentSection = SettingsSection.STORAGE_PLATFORM_GAMES,
            storagePlatformGames = StoragePlatformGamesState(selectedPlatformId = 9, platformName = "Wii")
        )
        val none = selected.copy(storagePlatformGames = StoragePlatformGamesState())
        assertEquals(SettingsPlatformScope(9, "Wii"), settingsPlatformScope(selected))
        assertNull(settingsPlatformScope(none))
    }

    @Test
    fun `sections about the whole app scope to nothing`() {
        assertNull(settingsPlatformScope(SettingsUiState(currentSection = SettingsSection.THEME)))
    }
}
