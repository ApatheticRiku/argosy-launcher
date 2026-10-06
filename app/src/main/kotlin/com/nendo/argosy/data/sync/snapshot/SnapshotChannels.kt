package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.repository.SaveSyncApiClient

/**
 * How an Argosy save channel maps onto a RomM snapshot channel label. The Argosy default channel
 * (null, "autosave") is the RomM channel labelled [DEFAULT_LABEL]; every other channel keeps its
 * name as its label.
 */
object SnapshotChannels {
    const val DEFAULT_LABEL = "default"
    const val EMULATOR = "argosy"

    fun isDefaultLabel(label: String): Boolean = label.trim().equals(DEFAULT_LABEL, ignoreCase = true)

    fun labelOf(argosyChannel: String?): String =
        SaveSyncApiClient.namedChannelOrNull(argosyChannel)?.takeUnless { isDefaultLabel(it) } ?: DEFAULT_LABEL

    fun argosyChannelOf(label: String): String? = label.takeUnless { isDefaultLabel(it) }

    fun isReservedLabel(label: String): Boolean =
        isDefaultLabel(label) || SaveSyncApiClient.isAutosaveChannel(label.trim())
}
