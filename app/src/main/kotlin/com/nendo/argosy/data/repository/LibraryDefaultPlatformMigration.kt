package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryDefaultPlatformMigration @Inject constructor(
    private val displayPreferencesRepository: DisplayPreferencesRepository,
    private val platformRepository: PlatformRepository
) {
    suspend fun run() {
        val platforms = platformRepository.getAllPlatforms()
        displayPreferencesRepository.migrateLegacyDefaultPlatform { name ->
            platforms.firstOrNull { it.getDisplayName() == name }?.id
        }
    }
}
