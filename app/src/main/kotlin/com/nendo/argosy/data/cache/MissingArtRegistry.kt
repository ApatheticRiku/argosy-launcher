package com.nendo.argosy.data.cache

import java.io.File

class MissingArtRegistry(
    private val file: File,
    private val retryAfterMs: Long = SEVEN_DAYS_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var entries: MutableMap<String, Long>? = null

    @Synchronized
    fun isKnownMissing(url: String): Boolean {
        val markedAt = load()[url] ?: return false
        return clock() - markedAt < retryAfterMs
    }

    @Synchronized
    fun markMissing(url: String) {
        val map = load()
        map[url] = clock()
        val now = clock()
        map.entries.removeAll { now - it.value >= retryAfterMs }
        file.parentFile?.mkdirs()
        file.writeText(map.entries.joinToString("\n") { "${it.value}\t${it.key}" })
    }

    private fun load(): MutableMap<String, Long> = entries ?: mutableMapOf<String, Long>().also { map ->
        if (file.exists()) {
            file.readLines().forEach { line ->
                val tab = line.indexOf('\t')
                val markedAt = line.substring(0, tab.coerceAtLeast(0)).toLongOrNull()
                if (tab > 0 && markedAt != null) map[line.substring(tab + 1)] = markedAt
            }
        }
        entries = map
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
