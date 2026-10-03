package com.nendo.argosy.domain.model

data class LaunchProgress(
    val gameTitle: String?,
    val step: LaunchStep
)

sealed interface LaunchStep {
    data object FinishingPrevious : LaunchStep
    data class Save(val progress: SyncProgress) : LaunchStep
    data class Prompt(val conflict: SyncProgress, val options: List<LaunchPromptOption>) : LaunchStep
    data class DownloadingCore(val fraction: Float?) : LaunchStep
    data object PreparingSystemFiles : LaunchStep
    data object PreparingUi : LaunchStep
    data object Launching : LaunchStep
}

enum class LaunchPromptOption {
    KEEP_HARDCORE,
    DOWNGRADE_TO_CASUAL,
    SKIP_HARDCORE_SAVE,
    APPLY_LOCAL,
    RESTORE_SERVER,
    LAUNCH_WITHOUT_SYNC
}
