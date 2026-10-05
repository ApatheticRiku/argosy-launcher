package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.sigil.SigilFileEntry
import com.nendo.sigil.SigilFileAccess
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sigil's file access over Argosy's [FileAccessLayer], so Sigil reaches Android/data through the
 * same tiers every other save operation uses. Callers still bracket a Sigil call with
 * [FileAccessLayer.prepareSaveAccess] and [FileAccessLayer.commitSaveAccess] on the save root.
 */
@Singleton
class FalSigilFileAccess @Inject constructor(private val fal: FileAccessLayer) : SigilFileAccess {

    override fun list(root: String, path: String): List<SigilFileEntry>? {
        val dir = resolve(root, path)
        if (!fal.isDirectory(dir)) return null
        return fal.listFilesUnion(dir).mapNotNull { info ->
            when {
                info.isDirectory -> SigilFileEntry(info.name, true)
                info.isFile -> SigilFileEntry(info.name, false)
                else -> null
            }
        }
    }

    override fun read(root: String, path: String): ByteArray? = fal.readBytes(resolve(root, path))

    override fun write(root: String, path: String, data: ByteArray): Boolean {
        val target = resolve(root, path)
        val parent = target.substringBeforeLast('/', "")
        if (parent.isNotEmpty() && !fal.isDirectory(parent) && !fal.mkdirs(parent)) return false
        return fal.writeBytes(target, data)
    }

    override fun remove(root: String, path: String): Boolean {
        val target = resolve(root, path.trimEnd('/'))
        return if (path.endsWith('/')) fal.deleteRecursively(target) else fal.delete(target)
    }

    private fun resolve(root: String, path: String): String =
        if (path.isEmpty()) root.trimEnd('/') else "${root.trimEnd('/')}/$path"
}
