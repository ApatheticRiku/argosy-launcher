package com.nendo.argosy.data.sync.platform

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object Ps2FolderCardSuperblock {

    const val SIZE = 0x2000

    private const val MAGIC = "Sony PS2 Memory Card Format "
    private const val VERSION = "1.2.0.0"
    private const val MAGIC_LENGTH = 28
    private const val VERSION_LENGTH = 12
    private const val FORMATTED_MARKER_OFFSET = 0x16
    private const val FORMATTED_MARKER: Byte = 0x6F

    private const val PAGE_LEN: Short = 512
    private const val PAGES_PER_CLUSTER: Short = 2
    private const val PAGES_PER_BLOCK: Short = 16
    private const val UNUSED_HALF: Short = 0xFF00.toShort()
    private const val CLUSTERS_PER_CARD = 8192
    private const val ALLOC_OFFSET = CLUSTERS_PER_CARD / 0x100 + 9
    private const val ALLOC_END = CLUSTERS_PER_CARD - 0x10 - ALLOC_OFFSET
    private const val ROOTDIR_CLUSTER = 0
    private const val BACKUP_BLOCK1 = CLUSTERS_PER_CARD / 8 - 1
    private const val BACKUP_BLOCK2 = CLUSTERS_PER_CARD / 8 - 2
    private const val FIRST_IFC_CLUSTER = 8
    private const val LIST_ENTRIES = 32
    private const val NO_BAD_BLOCK = -1
    private const val CARD_TYPE_PS2: Byte = 2
    private const val CARD_FLAGS: Byte = 0x52

    fun isFormatted(bytes: ByteArray?): Boolean =
        bytes != null && bytes.size >= SIZE && bytes[FORMATTED_MARKER_OFFSET] == FORMATTED_MARKER

    fun formatted8Mb(): ByteArray {
        val bytes = ByteArray(SIZE) { 0xFF.toByte() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII).copyOf(MAGIC_LENGTH))
        buffer.put(VERSION.toByteArray(Charsets.US_ASCII).copyOf(VERSION_LENGTH))
        buffer.putShort(PAGE_LEN)
        buffer.putShort(PAGES_PER_CLUSTER)
        buffer.putShort(PAGES_PER_BLOCK)
        buffer.putShort(UNUSED_HALF)
        buffer.putInt(CLUSTERS_PER_CARD)
        buffer.putInt(ALLOC_OFFSET)
        buffer.putInt(ALLOC_END)
        buffer.putInt(ROOTDIR_CLUSTER)
        buffer.putInt(BACKUP_BLOCK1)
        buffer.putInt(BACKUP_BLOCK2)
        buffer.putLong(0L)
        buffer.putInt(FIRST_IFC_CLUSTER)
        repeat(LIST_ENTRIES - 1) { buffer.putInt(0) }
        repeat(LIST_ENTRIES) { buffer.putInt(NO_BAD_BLOCK) }
        buffer.put(CARD_TYPE_PS2)
        buffer.put(CARD_FLAGS)
        return bytes
    }
}
