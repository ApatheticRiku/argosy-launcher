package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.SiblingGroupMember
import com.nendo.argosy.domain.model.SiblingMemberKind

@get:StringRes
val SiblingMemberKind.labelRes: Int?
    get() = when (this) {
        SiblingMemberKind.RELEASE -> null
        SiblingMemberKind.PRE_RELEASE -> R.string.ui_sibling_member_kind_pre_release
        SiblingMemberKind.TRANSLATION -> R.string.ui_sibling_member_kind_translation
        SiblingMemberKind.HACK -> R.string.ui_sibling_member_kind_hack
    }

/**
 * The literal tokens that tell group members apart: the filename's region, revision and other tags,
 * or the rom's stored regions when the filename carries none. Never translated.
 */
val SiblingGroupMember.detailTokens: List<String>
    get() = fileName?.let { parseRomFileName(it).tags }.orEmpty().ifEmpty { regions }
