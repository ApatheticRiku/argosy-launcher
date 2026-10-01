package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputResult

/** V2 toggle rule for d-pad on a focused toggle row: left=off, right=on, idempotent. */
internal fun toggleLeftRight(direction: Int, current: Boolean, set: (Boolean) -> Unit): InputResult {
    val target = direction > 0
    if (target == current) return InputResult.handled(SoundType.SILENT)
    set(target)
    return InputResult.toggled(target)
}
