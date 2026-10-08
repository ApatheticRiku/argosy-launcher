package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType

fun <T> stepOption(options: List<T>, current: T, delta: Int, apply: (T) -> Unit): InputResult {
    val index = options.indexOf(current).coerceAtLeast(0)
    val next = index + delta
    if (next !in options.indices) return InputResult.handled(SoundType.BOUNDARY)
    apply(options[next])
    return InputResult.HANDLED
}
