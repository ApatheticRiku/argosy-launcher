package com.nendo.argosy.data.storage

import android.content.Context
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives the folders holding saves inside other apps' Android/data on internal storage the group
 * read/write bits the system's own files there carry, through the vendor root daemon. Owner and
 * group are left as they are; only an emulator that writes its saves mode 600 changes, so Argosy
 * and the media provider, both in that folder's group, can back the save up and restore it.
 */
@Singleton
class RootFileAccessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isAvailable: Boolean
        get() = RootShell.isAvailable

    fun grantGroupAccess(paths: Collection<String>): Boolean {
        val targets = paths.mapNotNull(::lowerPath).distinct()
        if (targets.isEmpty()) return false
        val script = targets.joinToString("\n") { target ->
            "p=${RootShell.quote(target)}\n" +
                "[ -d \"${'$'}p\" ] || p=\"${'$'}(dirname \"${'$'}p\")\"\n" +
                "case \"${'$'}p\" in /data/media/*/Android/data/*/*|/data/media/*/Android/obb/*/*) " +
                "[ -d \"${'$'}p\" ] && chmod -R g+rwX \"${'$'}p\" ;; esac"
        } + "\nexit 0"
        val result = RootShell.run(context, script)
        val ok = result?.succeeded == true
        if (ok) {
            Logger.info(TAG, "granted group access to the save folders via root | saves=$targets")
        } else {
            Logger.warn(TAG, "group access via root failed | saves=$targets output=${result?.output}")
        }
        return ok
    }

    companion object {
        private const val TAG = "RootFileAccessor"
        private val LEFT_TO_RIGHT_ISOLATE = Char(0x2066).toString()
        private val EMULATED = Regex("^/storage/emulated/(\\d+)/")
        private val INSIDE_PACKAGE = Regex("^/data/media/\\d+/Android/(data|obb)/[^/]+/.+")

        internal fun lowerPath(path: String): String? {
            val clean = path.replace(LEFT_TO_RIGHT_ISOLATE, "")
            val lower = EMULATED.find(clean)?.let { match ->
                "/data/media/${match.groupValues[1]}/" + clean.substring(match.range.last + 1)
            } ?: clean.takeIf { it.startsWith("/sdcard/") }?.let { "/data/media/0/" + it.removePrefix("/sdcard/") }
            return lower?.trimEnd('/')?.takeIf { INSIDE_PACKAGE.matches(it) && ".." !in it }
        }
    }
}
