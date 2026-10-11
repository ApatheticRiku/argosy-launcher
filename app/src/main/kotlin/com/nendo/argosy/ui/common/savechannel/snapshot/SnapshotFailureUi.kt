package com.nendo.argosy.ui.common.savechannel.snapshot

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.sync.snapshot.SnapshotFailure

@get:StringRes
val SnapshotFailure.messageRes: Int
    get() = when (this) {
        SnapshotFailure.OFFLINE -> R.string.save_channels_notice_failed_offline
        SnapshotFailure.REFUSED -> R.string.save_channels_notice_failed_refused
        SnapshotFailure.NOT_FOUND -> R.string.save_channels_notice_failed_not_found
        SnapshotFailure.CONFLICT -> R.string.save_channels_notice_failed_conflict
        SnapshotFailure.UNKNOWN -> R.string.save_channels_notice_failed_unknown
    }
