package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last snapshot library fetched for each account and game, kept in memory and on disk so the
 * save manager can draw it before the server answers.
 */
@Singleton
class SnapshotLibraryCache @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(SnapshotLibrary::class.java)
    private val dir = File(context.filesDir, DIR)
    private val memory = ConcurrentHashMap<String, SnapshotLibrary>()

    suspend fun get(ownerUserId: Long, gameId: Long): SnapshotLibrary? = withContext(Dispatchers.IO) {
        val key = key(ownerUserId, gameId)
        memory[key] ?: runCatching { File(dir, "$key.json").takeIf { it.isFile }?.readText()?.let(adapter::fromJson) }
            .onFailure { Logger.warn(TAG, "unreadable cached library $key: ${it.message}") }
            .getOrNull()
            ?.also { memory[key] = it }
    }

    suspend fun put(ownerUserId: Long, gameId: Long, library: SnapshotLibrary) = withContext(Dispatchers.IO) {
        val key = key(ownerUserId, gameId)
        memory[key] = library
        runCatching {
            dir.mkdirs()
            val target = File(dir, "$key.json")
            val temp = File(dir, "$key.json.tmp")
            temp.writeText(adapter.toJson(library))
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }.onFailure { Logger.warn(TAG, "could not store library $key: ${it.message}") }
    }

    private fun key(ownerUserId: Long, gameId: Long) = "${ownerUserId}_$gameId"

    private companion object {
        const val TAG = "SnapshotLibraryCache"
        const val DIR = "snapshot_library"
    }
}
