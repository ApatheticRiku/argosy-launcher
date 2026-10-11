package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.SnapshotConflictChoice

@get:StringRes
val SnapshotConflictChoice.labelRes: Int
    get() = when (this) {
        SnapshotConflictChoice.MINE -> R.string.ui_sync_overlay_snapshot_conflict_mine
        SnapshotConflictChoice.THEIRS -> R.string.ui_sync_overlay_snapshot_conflict_theirs
        SnapshotConflictChoice.BRANCH -> R.string.ui_sync_overlay_snapshot_conflict_branch
        SnapshotConflictChoice.REVERT -> R.string.ui_sync_overlay_snapshot_conflict_revert
    }

@get:StringRes
val SnapshotConflictChoice.subtitleRes: Int
    get() = when (this) {
        SnapshotConflictChoice.MINE -> R.string.ui_sync_overlay_snapshot_conflict_mine_subtitle
        SnapshotConflictChoice.THEIRS -> R.string.ui_sync_overlay_snapshot_conflict_theirs_subtitle
        SnapshotConflictChoice.BRANCH -> R.string.ui_sync_overlay_snapshot_conflict_branch_subtitle
        SnapshotConflictChoice.REVERT -> R.string.ui_sync_overlay_snapshot_conflict_revert_subtitle
    }
