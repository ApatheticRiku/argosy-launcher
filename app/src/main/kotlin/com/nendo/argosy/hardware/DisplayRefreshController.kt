package com.nendo.argosy.hardware

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayRefreshController @Inject constructor() {

    fun currentRateHz(): Int? = null

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun setRateHz(hz: Int): Boolean = false
}
