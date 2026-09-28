package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenreShortLabelsTest {

    @Test
    fun `every long IGDB genre maps to its short label`() {
        val expected = mapOf(
            "Hack and slash/Beat 'em up" to R.string.gamelist_row_genre_beat_em_up,
            "Role-playing (RPG)" to R.string.gamelist_row_genre_rpg,
            "Real Time Strategy (RTS)" to R.string.gamelist_row_genre_rts,
            "Turn-based strategy (TBS)" to R.string.gamelist_row_genre_tbs,
            "Point-and-click" to R.string.gamelist_row_genre_point_and_click,
            "Card & Board Game" to R.string.gamelist_row_genre_board_game,
            "Quiz/Trivia" to R.string.gamelist_row_genre_quiz,
            "Simulator" to R.string.gamelist_row_genre_sim,
            "Role-Playing" to R.string.gamelist_row_genre_rpg,
            "Role Playing Game" to R.string.gamelist_row_genre_rpg,
            "Construction and Management Simulation" to R.string.gamelist_row_genre_sim
        )

        expected.forEach { (genre, labelRes) -> assertEquals(genre, labelRes, genreShortLabelRes(genre)) }
    }

    @Test
    fun `a genre without a short form keeps its stored name`() {
        listOf("Adventure", "Shooter", "Visual Novel", "Tactical", "MOBA").forEach { assertNull(genreShortLabelRes(it)) }
    }

    @Test
    fun `matching is on the exact stored token`() {
        assertNull(genreShortLabelRes("role-playing (rpg)"))
        assertNull(genreShortLabelRes("RPG"))
        assertNull(genreShortLabelRes(" Simulator"))
    }
}
