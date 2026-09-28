package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.domain.model.SiblingMemberKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiblingMemberUiTest {

    private fun member(fileName: String?, regions: List<String> = listOf("USA")) = SiblingGroupMember(
        gameId = 1,
        title = "Game",
        fileName = fileName,
        regions = regions,
        kind = SiblingMemberKind.RELEASE,
        isDownloaded = false,
        isPicked = false,
        isShown = false
    )

    @Test
    fun `a member is told apart by its filename's region, revision and tags`() {
        assertEquals(
            listOf("USA", "Europe", "Rev 1", "Hack"),
            member("Game (USA, Europe) (Rev 1) [Hack].sfc").detailTokens
        )
    }

    @Test
    fun `a filename with no tags falls back to the stored regions`() {
        assertEquals(listOf("Japan"), member("Game.sfc", regions = listOf("Japan")).detailTokens)
        assertEquals(listOf("Japan"), member(null, regions = listOf("Japan")).detailTokens)
    }

    @Test
    fun `only non-release kinds carry a marker label`() {
        assertNull(SiblingMemberKind.RELEASE.labelRes)
        assertEquals(R.string.ui_sibling_member_kind_hack, SiblingMemberKind.HACK.labelRes)
        assertEquals(R.string.ui_sibling_member_kind_translation, SiblingMemberKind.TRANSLATION.labelRes)
        assertEquals(R.string.ui_sibling_member_kind_pre_release, SiblingMemberKind.PRE_RELEASE.labelRes)
    }
}
