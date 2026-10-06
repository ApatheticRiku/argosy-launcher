package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotConflict
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StatePart(
    val core: String,
    val slot: String,
    val file: File,
    val hash: String,
    val screenshot: File?,
    val serverHasIt: Boolean
)

sealed class PushOutcome {
    data class Written(val snapshot: RomMSnapshot) : PushOutcome()
    data class Conflict(val currentId: Long) : PushOutcome()
    data object HardcoreDowngrade : PushOutcome()
    data class Failed(val reason: String, val failure: SnapshotFailure) : PushOutcome()
}

/**
 * Sends one `POST /api/snapshots`: the manifest, plus the save's bytes when [save] is given. A
 * manifest that only names a parent or a `copy_of` carries no file part.
 */
@Singleton
class SnapshotPusher @Inject constructor(moshi: Moshi) {
    private val snapshotAdapter = moshi.adapter(RomMSnapshot::class.java)
    private val conflictAdapter = moshi.adapter(RomMSnapshotConflict::class.java)

    suspend fun push(
        api: RomMApi,
        deviceId: String,
        manifest: JSONObject,
        save: SnapshotUnit? = null,
        screenshot: File? = null,
        states: List<StatePart> = emptyList()
    ): PushOutcome {
        val parts = buildList {
            add(MultipartBody.Part.createFormData(MANIFEST_PART, null, manifest.toString().toRequestBody(JSON)))
            if (save != null) {
                add(MultipartBody.Part.createFormData(SAVE_PART, save.name, save.data.toRequestBody(OCTET_STREAM)))
                screenshot?.takeIf { it.isFile }?.let {
                    add(MultipartBody.Part.createFormData(SAVE_SCREENSHOT_PART, it.name, it.asRequestBody(JPEG)))
                }
            }
            states.filterNot { it.serverHasIt }.forEach { state ->
                val name = "$STATE_PART_PREFIX${state.core}:${state.slot}"
                add(MultipartBody.Part.createFormData(name, state.file.name, state.file.asRequestBody(OCTET_STREAM)))
                state.screenshot?.takeIf { it.isFile }?.let {
                    add(MultipartBody.Part.createFormData("$name$SCREENSHOT_SUFFIX", it.name, it.asRequestBody(PNG)))
                }
            }
        }
        val response = runCatching { api.pushSnapshot(deviceId, parts) }.getOrElse {
            return PushOutcome.Failed("push failed: ${it.message}", SnapshotFailure.OFFLINE)
        }
        val body = if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()
        return when (val code = response.code()) {
            200, 201 -> body?.let { runCatching { snapshotAdapter.fromJson(it) }.getOrNull() }
                ?.let { PushOutcome.Written(it) }
                ?: PushOutcome.Failed("push answered $code without a snapshot", SnapshotFailure.UNKNOWN)
            409 -> {
                val conflict = body?.let { runCatching { conflictAdapter.fromJson(it) }.getOrNull() }
                when {
                    conflict?.hardcoreDowngrade == true -> PushOutcome.HardcoreDowngrade
                    conflict?.current != null -> PushOutcome.Conflict(conflict.current.id)
                    else -> PushOutcome.Failed("push refused: 409 $body", SnapshotFailure.CONFLICT)
                }
            }
            else -> PushOutcome.Failed("push refused: $code $body", SnapshotFailure.ofStatus(code))
        }
    }

    private companion object {
        const val MANIFEST_PART = "manifest"
        const val SAVE_PART = "save"
        const val SAVE_SCREENSHOT_PART = "save_screenshot"
        const val STATE_PART_PREFIX = "state:"
        const val SCREENSHOT_SUFFIX = ":screenshot"
        val PNG = "image/png".toMediaType()
        val JSON = "application/json".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
