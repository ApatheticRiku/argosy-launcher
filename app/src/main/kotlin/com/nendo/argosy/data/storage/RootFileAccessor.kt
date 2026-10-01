package com.nendo.argosy.data.storage

import android.content.Context
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives a save file or folder inside another app's Android/data on internal storage the group
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

    fun grantGroupAccess(path: String): Boolean {
        val target = lowerPath(path) ?: return false
        val result = RootShell.run(
            context,
            """
            p=${RootShell.quote(target)}
            [ -e "${'$'}p" ] || exit 0
            if [ -d "${'$'}p" ]; then chmod -R g+rwX "${'$'}p"; else chmod g+rw "${'$'}p"; fi
            """.trimIndent()
        )
        val ok = result?.succeeded == true
        if (ok) {
            Logger.info(TAG, "granted group access via root | path=$path")
        } else {
            Logger.warn(TAG, "group access via root failed | path=$path output=${result?.output}")
        }
        return ok
    }

    companion object {
        private const val TAG = "RootFileAccessor"
        private val EMULATED = Regex("^/storage/emulated/(\\d+)/")
        private val INSIDE_PACKAGE = Regex("^/data/media/\\d+/Android/(data|obb)/[^/]+/.+")

        internal fun lowerPath(path: String): String? {
            val clean = path.replace("⁦", "")
            val lower = EMULATED.find(clean)?.let { match ->
                "/data/media/${match.groupValues[1]}/" + clean.substring(match.range.last + 1)
            } ?: clean.takeIf { it.startsWith("/sdcard/") }?.let { "/data/media/0/" + it.removePrefix("/sdcard/") }
            return lower?.trimEnd('/')?.takeIf { INSIDE_PACKAGE.matches(it) && ".." !in it }
        }
    }
}
