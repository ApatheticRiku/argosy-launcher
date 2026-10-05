package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotConflict
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class PushOutcome {
    data class Written(val snapshot: RomMSnapshot) : PushOutcome()
    data class Conflict(val currentId: Long) : PushOutcome()
    data object HardcoreDowngrade : PushOutcome()
    data class Failed(val reason: String) : PushOutcome()
}

/**
 * Sends one `POST /api/snapshots`: the manifest, plus the save's bytes when [save] is given. A
 * manifest that only names a parent or a `copy_of` carries no file part.
 */
@Singleton
class SnapshotPusher @Inject constructor(moshi: Moshi) {
    private val snapshotAdapter = moshi.adapter(RomMSnapshot::class.java)
    private val conflictAdapter = moshi.adapter(RomMSnapshotConflict::class.java)

    suspend fun push(api: RomMApi, deviceId: String, manifest: JSONObject, save: SnapshotUnit? = null): PushOutcome {
        val parts = buildList {
            add(MultipartBody.Part.createFormData(MANIFEST_PART, null, manifest.toString().toRequestBody(JSON)))
            if (save != null) {
                add(MultipartBody.Part.createFormData(SAVE_PART, save.name, save.data.toRequestBody(OCTET_STREAM)))
            }
        }
        val response = runCatching { api.pushSnapshot(deviceId, parts) }.getOrElse {
            return PushOutcome.Failed("push failed: ${it.message}")
        }
        val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        return when (response.code()) {
            200, 201 -> body?.let { runCatching { snapshotAdapter.fromJson(it) }.getOrNull() }
                ?.let { PushOutcome.Written(it) }
                ?: PushOutcome.Failed("push answered ${response.code()} without a snapshot")
            409 -> {
                val conflict = body?.let { runCatching { conflictAdapter.fromJson(it) }.getOrNull() }
                when {
                    conflict?.hardcoreDowngrade == true -> PushOutcome.HardcoreDowngrade
                    conflict?.current != null -> PushOutcome.Conflict(conflict.current.id)
                    else -> PushOutcome.Failed("push refused: 409 $body")
                }
            }
            else -> PushOutcome.Failed("push refused: ${response.code()} $body")
        }
    }

    private companion object {
        const val MANIFEST_PART = "manifest"
        const val SAVE_PART = "save"
        val JSON = "application/json".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
