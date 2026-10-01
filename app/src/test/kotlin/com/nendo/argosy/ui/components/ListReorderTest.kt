package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListReorderTest {

    private val items = listOf("a", "b", "c", "d")

    @Test
    fun `lift outside the list yields no session`() {
        assertNull(ListReorder.lift(items, -1))
        assertNull(ListReorder.lift(items, items.size))
        assertNull(ListReorder.lift(emptyList<String>(), 0))
    }

    @Test
    fun `moving down carries the held item and its index`() {
        val reorder = ListReorder.lift(items, 1)!!

        val step = reorder.moveBy(items, 1)

        assertTrue(step.moved)
        assertEquals(listOf("a", "c", "b", "d"), step.items)
        assertEquals(2, step.reorder.heldIndex)
        assertEquals(1, step.reorder.originIndex)
    }

    @Test
    fun `moving up carries the held item and its index`() {
        val reorder = ListReorder.lift(items, 2)!!

        val step = reorder.moveBy(items, -1)

        assertTrue(step.moved)
        assertEquals(listOf("a", "c", "b", "d"), step.items)
        assertEquals(1, step.reorder.heldIndex)
    }

    @Test
    fun `moves clamp at both ends`() {
        val first = ListReorder.lift(items, 0)!!.moveBy(items, -1)
        val last = ListReorder.lift(items, items.lastIndex)!!.moveBy(items, 1)

        assertFalse(first.moved)
        assertEquals(items, first.items)
        assertFalse(last.moved)
        assertEquals(items, last.items)
    }

    @Test
    fun `moveTo clamps an out of range target to the last slot`() {
        val step = ListReorder.lift(items, 0)!!.moveTo(items, 99)

        assertEquals(listOf("b", "c", "d", "a"), step.items)
        assertEquals(items.lastIndex, step.reorder.heldIndex)
    }

    @Test
    fun `changedOrder is null until the order differs from the backup`() {
        val reorder = ListReorder.lift(items, 1)!!
        val down = reorder.moveBy(items, 1)
        val back = down.reorder.moveBy(down.items, -1)

        assertNull(reorder.changedOrder(items))
        assertEquals(down.items, down.reorder.changedOrder(down.items))
        assertNull(back.reorder.changedOrder(back.items))
    }

    @Test
    fun `backup and origin survive every move`() {
        val reorder = ListReorder.lift(items, 1)!!
        val step = reorder.moveBy(items, 2).let { it.reorder.moveBy(it.items, -3) }

        assertEquals(items, step.reorder.backup)
        assertEquals(1, step.reorder.originIndex)
    }

    @Test
    fun `liftOrRegrab keeps the first backup when a session is already open`() {
        val first = ListReorder.lift(items, 0)!!.moveBy(items, 2)

        val regrabbed = ListReorder.liftOrRegrab(first.reorder, first.items, 3)!!

        assertEquals(items, regrabbed.backup)
        assertEquals(3, regrabbed.heldIndex)
    }

    @Test
    fun `liftOrRegrab ignores an index outside the list`() {
        assertNull(ListReorder.liftOrRegrab(null, items, -1))
        val open = ListReorder.lift(items, 1)
        assertEquals(open, ListReorder.liftOrRegrab(open, items, -1))
    }

    @Test
    fun `duplicate values move by position, not by equality`() {
        val dupes = listOf("x", "y", "x")
        val step = ListReorder.lift(dupes, 2)!!.moveBy(dupes, -2)

        assertEquals(listOf("x", "x", "y"), step.items)
        assertEquals(0, step.reorder.heldIndex)
    }
}
