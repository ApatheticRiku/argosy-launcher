package com.nendo.argosy.data.repository

import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.ResolvedGridShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The custom grid shape the home screen last measured, for placements and settings that run with no
 * grid on screen. Null until a grid has been measured in this process.
 */
@Singleton
class CustomGridShapeStore @Inject constructor() {
    private val _shape = MutableStateFlow<ResolvedGridShape?>(null)
    val shape: StateFlow<ResolvedGridShape?> = _shape.asStateFlow()

    fun report(resolved: ResolvedGridShape) {
        _shape.value = resolved
    }

    fun shapeFor(config: CustomGridConfig): CustomGridShape = config.shapeFor(_shape.value)

    fun layoutFor(config: CustomGridConfig): CustomGridLayout = config.layoutFor(_shape.value)
}
