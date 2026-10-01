package com.nendo.argosy.util

import android.content.Context
import java.io.File

/**
 * Runs shell scripts as root through the vendor root daemon ([PServerExecutor]). The daemon returns
 * only the first line of output and mangles its own argument quoting, so every script is written to
 * a file, run through a wrapper that captures its full output, and reported as an exit code plus
 * output lines.
 */
object RootShell {

    data class Result(val exitCode: Int?, val output: List<String>) {
        val succeeded: Boolean get() = exitCode == 0
    }

    private const val WORK_DIR = "root-shell"
    private const val SCRIPT_NAME = "root-op.sh"
    private const val WRAPPER_NAME = "root-run.sh"
    private const val OUTPUT_NAME = "run-output.txt"
    private const val TAG = "RootShell"

    private val lock = Any()

    val isAvailable: Boolean
        get() = PServerExecutor.isAvailable

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun run(context: Context, body: String): Result? = synchronized(lock) {
        if (!isAvailable) return null
        try {
            val dir = File(context.cacheDir, WORK_DIR).apply { mkdirs() }
            dir.setExecutable(true, false)
            val script = File(dir, SCRIPT_NAME).apply { writeText(body + "\n") }
            val output = File(dir, OUTPUT_NAME).apply { delete() }
            val wrapper = File(dir, WRAPPER_NAME).apply {
                writeText(
                    "sh ${script.absolutePath} > ${output.absolutePath} 2>&1\n" +
                        "rc=\$?\nchmod 644 ${output.absolutePath}\necho \$rc\n"
                )
            }
            listOf(script, wrapper).forEach { it.setReadable(true, false) }
            val exit = PServerExecutor.execute("sh ${wrapper.absolutePath}").getOrElse { error ->
                Logger.warn(TAG, "root daemon refused the call: ${error.message}")
                return null
            }?.trim()?.toIntOrNull()
            val lines = if (output.canRead()) output.readLines() else emptyList()
            Result(exit, lines)
        } catch (e: Exception) {
            Logger.warn(TAG, "root shell failed: ${e.message}")
            null
        }
    }
}
