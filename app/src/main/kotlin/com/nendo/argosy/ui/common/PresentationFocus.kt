package com.nendo.argosy.ui.common

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.dualscreen.SlotOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface PresentationTarget {
    data object Nothing : PresentationTarget
    data class Game(val gameId: Long) : PresentationTarget
    data class GameSet(val key: String, val name: String, val gameIds: suspend () -> List<Long>) : PresentationTarget {
        override fun equals(other: Any?): Boolean = other is GameSet && other.key == key && other.name == name
        override fun hashCode(): Int = 31 * key.hashCode() + name.hashCode()
    }
}

/**
 * Describes one screen's focus on the presentation screen: a focused game as its showcase, a
 * focused set of games as a cover mosaic. The screen calls [startDescribing] when it is shown or
 * resumed, [stopDescribing] when it leaves, and [show] whenever its focus changes.
 */
class PresentationFocus(
    name: String,
    holder: Any,
    private val source: PresentationShowcaseSource,
    scope: CoroutineScope
) {
    private val owner = SlotOwner.of(name, holder)
    private val target = MutableStateFlow<PresentationTarget>(PresentationTarget.Nothing)
    private val generation = MutableStateFlow(0)

    init {
        scope.launch {
            combine(target, generation) { focus, gen -> focus to gen }
                .collectLatest { (focus, gen) -> if (gen > 0) render(focus) }
        }
    }

    fun show(focus: PresentationTarget) {
        target.value = focus
    }

    fun startDescribing() {
        generation.value = generation.value.coerceAtLeast(0) + 1
    }

    fun stopDescribing() {
        generation.value = 0
        DualScreenManagerHolder.instance?.let {
            it.setCompanionDetail(owner, null)
            it.releaseSlot(owner)
        }
    }

    private suspend fun render(focus: PresentationTarget) {
        val dsm = DualScreenManagerHolder.instance ?: return
        when (focus) {
            PresentationTarget.Nothing -> {
                dsm.releaseSlot(owner)
                dsm.setCompanionDetail(owner, null)
            }
            is PresentationTarget.Game -> {
                val detail = source.gameDetail(focus.gameId)
                if (!isDescribing) return
                dsm.releaseSlot(owner)
                dsm.setCompanionDetail(owner, detail)
                if (source.backfillLogo(focus.gameId)) {
                    val withLogo = source.gameDetail(focus.gameId)
                    if (isDescribing) dsm.setCompanionDetail(owner, withLogo)
                }
            }
            is PresentationTarget.GameSet -> {
                val showcase = source.collectionShowcase(focus.name, focus.gameIds())
                if (!isDescribing) return
                dsm.setCompanionDetail(owner, null)
                dsm.presentSlot(owner, showcase)
            }
        }
    }

    private val isDescribing: Boolean get() = generation.value > 0
}
