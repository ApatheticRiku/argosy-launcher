package com.nendo.argosy.domain.usecase.platform

import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.repository.PlatformRepository
import javax.inject.Inject

class ReorderPlatformsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {
    suspend operator fun invoke(orderedPlatformIds: List<Long>): Boolean {
        if (!platformRepository.applyPlatformOrder(orderedPlatformIds)) return false
        appPreferencesRepository.setPlatformOrderCustomised()
        return true
    }
}
