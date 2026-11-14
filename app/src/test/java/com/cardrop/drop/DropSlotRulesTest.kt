package com.cardrop.drop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 08's own tiles: the "+" slot, what it offers, and what stays saved. */
class DropSlotRulesTest {
    @Test
    fun startsWithOnlyThePlusSlot() {
        val none = DropSlotRules.decode(null)
        assertTrue(none.isEmpty())
        assertTrue(DropSlotRules.showsAdd(none))
        assertEquals(PlaceKind.entries.toList(), DropSlotRules.choices(none))
    }

    @Test
    fun plusOffersOnlyTheKindsNotPickedYet() {
        val one = DropSlotRules.add(emptyList(), PlaceKind.DROWSY)
        assertEquals(listOf(PlaceKind.STORE, PlaceKind.EV), DropSlotRules.choices(one))
        assertEquals(one, DropSlotRules.add(one, PlaceKind.DROWSY)) // not twice
    }

    @Test
    fun threeTilesFillThePageAndHideThePlus() {
        val all = PlaceKind.entries.fold(emptyList<PlaceKind>()) { saved, kind -> DropSlotRules.add(saved, kind) }
        assertEquals(3, all.size)
        assertFalse(DropSlotRules.showsAdd(all))
        assertTrue(DropSlotRules.showsAdd(DropSlotRules.remove(all, PlaceKind.STORE)))
    }

    @Test
    fun savedTilesComeBackInOrder() {
        val saved = listOf(PlaceKind.EV, PlaceKind.DROWSY)
        assertEquals(saved, DropSlotRules.decode(DropSlotRules.encode(saved)))
        assertEquals(listOf(PlaceKind.STORE), DropSlotRules.decode("store,unknown,store"))
    }
}
