package com.nendo.argosy.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root commands on Ayaneo devices that ship the vendor `xsu` binary, the same route Ayaneo's own
 * settings app uses for its root script runner. `xsu` joins its arguments into one shell command and
 * always exits 0, so callers pass a single command and read results from its output.
 */
object XsuExecutor {

    private const val XSU_PATH = "/product/bin/xsu"
    private const val TIMEOUT_SECONDS = 30L
    private const val ROOT_UID_MARKER = "uid=0("

    val isAvailable: Boolean by lazy {
        File(XSU_PATH).canExecute() && execute("id").getOrNull()?.contains(ROOT_UID_MARKER) == true
    }

    fun execute(command: String): Result<String> = runCatching {
        val process = ProcessBuilder(XSU_PATH, command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("xsu timed out")
        }
        output
    }
}
