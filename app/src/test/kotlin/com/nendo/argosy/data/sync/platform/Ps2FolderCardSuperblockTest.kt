package com.nendo.argosy.data.sync.platform

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Field values follow pcsx2/Reference/PS2-MemoryCardFileSystem.htm for a standard 8 MB card.
 * PCSX2's FolderMemoryCard reads the whole 0x2000-byte block and treats the card as formatted
 * only when byte 0x16 is 0x6F.
 */
class Ps2FolderCardSuperblockTest {

    private val block = Ps2FolderCardSuperblock.formatted8Mb()
    private val le = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `the block is the size PCSX2 reads`() {
        assertEquals(0x2000, block.size)
    }

    @Test
    fun `magic and version match a formatted card`() {
        assertArrayEquals("Sony PS2 Memory Card Format ".toByteArray(), block.copyOfRange(0x00, 0x1C))
        assertArrayEquals("1.2.0.0".toByteArray() + ByteArray(5), block.copyOfRange(0x1C, 0x28))
        assertEquals(0x6F.toByte(), block[0x16])
    }

    @Test
    fun `geometry matches the standard 8 MB card`() {
        assertEquals(512, le.getShort(0x28).toInt())
        assertEquals(2, le.getShort(0x2A).toInt())
        assertEquals(16, le.getShort(0x2C).toInt())
        assertEquals(0xFF00, le.getShort(0x2E).toInt() and 0xFFFF)
        assertEquals(8192, le.getInt(0x30))
        assertEquals(41, le.getInt(0x34))
        assertEquals(8135, le.getInt(0x38))
        assertEquals(0, le.getInt(0x3C))
        assertEquals(1023, le.getInt(0x40))
        assertEquals(1022, le.getInt(0x44))
        assertEquals(0L, le.getLong(0x48))
    }

    @Test
    fun `one indirect FAT cluster and no bad blocks`() {
        assertEquals(8, le.getInt(0x50))
        (1 until 32).forEach { assertEquals(0, le.getInt(0x50 + it * 4)) }
        (0 until 32).forEach { assertEquals(-1, le.getInt(0xD0 + it * 4)) }
    }

    @Test
    fun `card type and flags`() {
        assertEquals(2.toByte(), block[0x150])
        assertEquals(0x52.toByte(), block[0x151])
        assertEquals(0xFF.toByte(), block[0x152])
    }

    @Test
    fun `an empty, short or unformatted block is not formatted`() {
        assertFalse(Ps2FolderCardSuperblock.isFormatted(null))
        assertFalse(Ps2FolderCardSuperblock.isFormatted(ByteArray(0)))
        assertFalse(Ps2FolderCardSuperblock.isFormatted(block.copyOf(0x1FFF)))
        assertFalse(Ps2FolderCardSuperblock.isFormatted(ByteArray(0x2000) { 0xFF.toByte() }))
        assertTrue(Ps2FolderCardSuperblock.isFormatted(block))
    }
}
