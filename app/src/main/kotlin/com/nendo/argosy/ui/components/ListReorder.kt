package com.nendo.argosy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R

/**
 * Lift/move/drop/cancel session over a flat list. [heldIndex] follows the lifted item as it
 * moves, [originIndex] is where it was lifted from, and [backup] is the order at lift time.
 * Moves clamp at the list ends; a drop persists only when [changedOrder] is non-null; a cancel
 * restores [backup] and returns focus to [originIndex].
 */
@Immutable
data class ListReorder<T>(
    val heldIndex: Int,
    val originIndex: Int,
    val backup: List<T>
) {
    fun moveBy(items: List<T>, delta: Int): ReorderStep<T> = moveTo(items, heldIndex + delta)

    fun moveTo(items: List<T>, targetIndex: Int): ReorderStep<T> {
        if (heldIndex !in items.indices) return ReorderStep(items, this, moved = false)
        val to = targetIndex.coerceIn(0, items.lastIndex)
        if (to == heldIndex) return ReorderStep(items, this, moved = false)
        val reordered = items.toMutableList().apply { add(to, removeAt(heldIndex)) }.toList()
        return ReorderStep(reordered, copy(heldIndex = to), moved = true)
    }

    private fun regrab(index: Int): ListReorder<T> = copy(heldIndex = index)

    fun changedOrder(items: List<T>): List<T>? = items.takeIf { it != backup }

    companion object {
        fun <T> lift(items: List<T>, index: Int): ListReorder<T>? =
            if (index in items.indices) ListReorder(index, index, items) else null

        fun <T> liftOrRegrab(current: ListReorder<T>?, items: List<T>, index: Int): ListReorder<T>? = when {
            index !in items.indices -> current
            current != null -> current.regrab(index)
            else -> ListReorder(index, index, items)
        }
    }
}

@Immutable
data class ReorderStep<T>(
    val items: List<T>,
    val reorder: ListReorder<T>,
    val moved: Boolean
)

@Composable
fun liftedReorderHints(
    move: String,
    cancel: String,
    moveButton: InputButton = InputButton.DPAD_VERTICAL
): List<Pair<InputButton, String>> = listOf(
    moveButton to move,
    InputButton.A to stringResource(R.string.reorder_hint_done),
    InputButton.B to cancel
)
