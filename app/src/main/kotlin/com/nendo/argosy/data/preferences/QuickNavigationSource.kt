package com.nendo.argosy.data.preferences

import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickNavigationSource @Inject constructor(
    controlsPreferences: ControlsPreferencesRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "QuickNavigation")

    val enabled: StateFlow<Boolean> = controlsPreferences.preferences
        .map { it.quickNavigation }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)
}
