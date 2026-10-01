package com.nendo.argosy.data.model

enum class RootScript(val assetPath: String, val fileName: String, val needsReboot: Boolean) {
    SYSTEMIZE("root-scripts/systemize-argosy.sh", "argosy-systemize.sh", needsReboot = true),
    ANDROID_DATA("root-scripts/argosy-android-data.sh", "argosy-android-data.sh", needsReboot = false)
}

data class VendorSteps(val deviceLabel: String, val steps: List<String>)

enum class RootScriptOutcome { OK, PARTIAL, FAILED }

sealed interface RootScriptResult {
    val script: RootScript

    data class Written(
        override val script: RootScript,
        val scriptPath: String,
        val vendor: VendorSteps
    ) : RootScriptResult

    data class Ran(
        override val script: RootScript,
        val outcome: RootScriptOutcome,
        val output: List<String>
    ) : RootScriptResult

    data class Error(
        override val script: RootScript,
        val message: String,
        val duringRun: Boolean
    ) : RootScriptResult
}
