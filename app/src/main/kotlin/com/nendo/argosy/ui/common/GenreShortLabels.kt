package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R

/**
 * Compact label for a genre name as stored on a game, matched on the exact stored token. Covers
 * IGDB's names and the spellings other metadata providers store for the same genres. Null for
 * genres that have no shorter form.
 */
@StringRes
fun genreShortLabelRes(genre: String): Int? = when (genre) {
    "Hack and slash/Beat 'em up" -> R.string.gamelist_row_genre_beat_em_up
    "Role-playing (RPG)", "Role-Playing", "Role Playing Game" -> R.string.gamelist_row_genre_rpg
    "Real Time Strategy (RTS)" -> R.string.gamelist_row_genre_rts
    "Turn-based strategy (TBS)" -> R.string.gamelist_row_genre_tbs
    "Point-and-click" -> R.string.gamelist_row_genre_point_and_click
    "Card & Board Game" -> R.string.gamelist_row_genre_board_game
    "Quiz/Trivia" -> R.string.gamelist_row_genre_quiz
    "Simulator", "Construction and Management Simulation" -> R.string.gamelist_row_genre_sim
    else -> null
}
