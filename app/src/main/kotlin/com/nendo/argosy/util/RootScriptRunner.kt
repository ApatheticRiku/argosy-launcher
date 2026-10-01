package com.nendo.argosy.util

import android.content.Context
import android.os.Build
import com.nendo.argosy.R
import com.nendo.argosy.data.model.RootScript
import com.nendo.argosy.data.model.RootScriptOutcome
import com.nendo.argosy.data.model.RootScriptResult
import com.nendo.argosy.data.model.VendorSteps
import com.nendo.argosy.data.storage.StoragePathUtils
import java.io.File

object RootScriptRunner {
    private const val WORK_DIR = "root-scripts"
    private const val WRAPPER_NAME = "run-root-script.sh"
    private const val OUTPUT_NAME = "run-output.txt"
    private val WRAPPER = """
        sh "${'$'}1" "${'$'}2" > "${'$'}3" 2>&1
        rc=${'$'}?
        chmod 644 "${'$'}3"
        echo "${'$'}rc"
    """.trimIndent()
    private val BOOKKEEPING = listOf("--- run", "log file:", "uid=", "storage visible:", "selinux:")

    val canRunDirectly: Boolean
        get() = PServerExecutor.isAvailable

    fun write(context: Context, script: RootScript): RootScriptResult = try {
        val target = File("${StoragePathUtils.primaryExternalRoot}/${script.fileName}")
        copyAsset(context, script, target)
        RootScriptResult.Written(script, target.absolutePath, vendorSteps(context, script))
    } catch (e: Exception) {
        RootScriptResult.Error(
            script,
            e.message ?: context.getString(R.string.settings_root_script_write_failed),
            duringRun = false
        )
    }

    fun run(context: Context, script: RootScript): RootScriptResult {
        return try {
            val dir = File(context.cacheDir, WORK_DIR).apply { mkdirs() }
            dir.setExecutable(true, false)
            val scriptFile = File(dir, script.fileName)
            copyAsset(context, script, scriptFile)
            val wrapper = File(dir, WRAPPER_NAME).apply { writeText(WRAPPER) }
            val output = File(dir, OUTPUT_NAME).apply { delete() }
            listOf(scriptFile, wrapper).forEach { it.setReadable(true, false) }

            val command = listOf(wrapper.absolutePath, scriptFile.absolutePath, context.packageName, output.absolutePath)
                .joinToString(" ", prefix = "sh ")
            val exitCode = PServerExecutor.execute(command).getOrElse { error ->
                return runError(context, script, error)
            }?.trim()?.toIntOrNull()

            val lines = if (output.canRead()) output.readLines() else emptyList()
            RootScriptResult.Ran(script, outcomeOf(exitCode, lines), visibleLines(lines))
        } catch (e: Exception) {
            runError(context, script, e)
        }
    }

    fun reboot(): Boolean = PServerExecutor.execute("svc power reboot || reboot").isSuccess

    private fun runError(context: Context, script: RootScript, error: Throwable) = RootScriptResult.Error(
        script,
        error.message ?: context.getString(R.string.settings_root_script_run_failed),
        duringRun = true
    )

    private fun copyAsset(context: Context, script: RootScript, target: File) {
        context.assets.open(script.assetPath).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun outcomeOf(exitCode: Int?, lines: List<String>): RootScriptOutcome = when {
        exitCode != 0 || lines.any { "FAILED:" in it } -> RootScriptOutcome.FAILED
        lines.any { "PARTIAL:" in it } -> RootScriptOutcome.PARTIAL
        else -> RootScriptOutcome.OK
    }

    private fun visibleLines(lines: List<String>): List<String> =
        lines.map { it.substringAfter("] ", it).trim() }
            .filter { line -> line.isNotEmpty() && BOOKKEEPING.none { line.startsWith(it) } }

    private fun vendorSteps(context: Context, script: RootScript): VendorSteps {
        val fields = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE)
            .joinToString(" ") { it.lowercase() }
        val open = when {
            "retroid" in fields || "moorechip" in fields -> R.string.settings_root_script_step_open_retroid
            "ayn" in fields || "odin" in fields || "thor" in fields -> R.string.settings_root_script_step_open_ayn
            "ayaneo" in fields || "konkr" in fields || "pocket fit" in fields -> R.string.settings_root_script_step_open_ayaneo
            else -> R.string.settings_root_script_step_open_generic
        }
        val steps = buildList {
            add(context.getString(open))
            add(context.getString(R.string.settings_root_script_step_select, script.fileName))
            add(
                context.getString(
                    if (script.needsReboot) R.string.settings_root_script_step_run_reboot
                    else R.string.settings_root_script_step_run
                )
            )
            if (script == RootScript.SYSTEMIZE) add(context.getString(R.string.settings_root_script_step_default_launcher))
        }
        return VendorSteps(Build.MODEL, steps)
    }
}
