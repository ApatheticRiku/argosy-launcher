package com.nendo.argosy.domain.model

enum class SiblingMemberKind { RELEASE, PRE_RELEASE, TRANSLATION, HACK }

/**
 * One rom of a sibling group as the group choosers show it. [fileName] and [regions] are literal
 * tokens from the rom; [isPicked] is this device's pick for the group and [isShown] marks the member
 * the library currently lists for the group.
 */
data class SiblingGroupMember(
    val gameId: Long,
    val title: String,
    val fileName: String?,
    val regions: List<String>,
    val kind: SiblingMemberKind,
    val isDownloaded: Boolean,
    val isPicked: Boolean,
    val isShown: Boolean
)

data class SiblingGroup(
    val groupKey: String,
    val platformId: Long,
    val members: List<SiblingGroupMember>
) {
    val hasChoice: Boolean get() = members.size > 1

    val hasPick: Boolean get() = members.any { it.isPicked }

    val shownMember: SiblingGroupMember? get() = members.firstOrNull { it.isShown }
}
