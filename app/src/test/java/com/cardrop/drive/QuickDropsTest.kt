package com.cardrop.drive

import com.cardrop.core.model.DropTopic
import com.cardrop.core.navigation.Screen
import com.cardrop.drive.QuickDrop.FOOD
import com.cardrop.drive.QuickDrop.FUEL
import com.cardrop.drive.QuickDrop.GAME
import com.cardrop.drive.QuickDrop.INBOX
import com.cardrop.drive.QuickDrop.REST
import com.cardrop.drive.QuickDrop.SCORE
import com.cardrop.drive.QuickDrop.SHOUT
import com.cardrop.drive.QuickDrop.SONG
import com.cardrop.drive.QuickDrop.TOGETHER
import com.cardrop.drive.QuickDropRules.Pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 04's quick-drop slots and the 퀵 드랍 편집 rules. */
class QuickDropsTest {

    /** Keeps the list in memory, like the device's SharedPreferences. */
    private class MemoryStore(var saved: String? = null) : QuickDropStore {
        override fun load(): List<QuickDrop> = QuickDropRules.decode(saved)
        override fun save(drops: List<QuickDrop>) {
            saved = QuickDropRules.encode(drops)
        }
    }

    @Test
    fun nothingSavedMeansFourEmptySlots() {
        assertEquals(emptyList<QuickDrop>(), QuickDrops(MemoryStore()).chosen.value)
        assertEquals(emptyList<QuickDrop>(), QuickDropRules.decode(""))
    }

    @Test
    fun savedDropsComeBackInTheirOrder() {
        assertEquals(listOf(FUEL, REST, INBOX), QuickDrops(MemoryStore("fuel,rest,inbox")).chosen.value)
    }

    @Test
    fun unknownRepeatedOrCutDropsAreLeftOutAndAtMostFourStay() {
        assertEquals(
            listOf(REST, FOOD, SHOUT, GAME),
            QuickDropRules.decode("rest,song,cafe,rest,,food,shout,game,inbox"),
        )
    }

    @Test
    fun savingShowsTheNewSlotsAndKeepsThemOnTheDevice() {
        val store = MemoryStore("rest")
        val quickDrops = QuickDrops(store)
        quickDrops.save(listOf(GAME, SHOUT))
        assertEquals(listOf(GAME, SHOUT), quickDrops.chosen.value)
        assertEquals("game,shout", store.saved)
        // The next start reads the same list.
        assertEquals(listOf(GAME, SHOUT), QuickDrops(store).chosen.value)
    }

    @Test
    fun aNewPickTakesTheNextSlotAndUntickingMovesTheRestUp() {
        assertEquals(Pick.Changed(listOf(REST)), QuickDropRules.pick(emptyList(), REST))
        assertEquals(Pick.Changed(listOf(REST, FUEL, SHOUT)), QuickDropRules.pick(listOf(REST, FUEL), SHOUT))
        assertEquals(Pick.Changed(listOf(REST, SHOUT)), QuickDropRules.pick(listOf(REST, FUEL, SHOUT), FUEL))
    }

    @Test
    fun aFifthPickIsRefusedButUntickingStillWorks() {
        val four = listOf(REST, FOOD, FUEL, SHOUT)
        assertEquals(Pick.Full, QuickDropRules.pick(four, INBOX))
        assertEquals(Pick.Changed(listOf(REST, FOOD, SHOUT)), QuickDropRules.pick(four, FUEL))
    }

    @Test
    fun cutDropsCantBePicked() {
        listOf(SONG, TOGETHER, SCORE).forEach { cut ->
            assertEquals(Pick.Changed(listOf(REST)), QuickDropRules.pick(listOf(REST), cut))
        }
    }

    @Test
    fun eachTileOpensWhatItNames() {
        assertEquals(Screen.AiRecommend(DropTopic.REST), REST.screen())
        assertEquals(Screen.AiRecommend(DropTopic.FOOD), FOOD.screen())
        assertEquals(Screen.AiRecommend(DropTopic.FUEL), FUEL.screen())
        assertEquals(Screen.Shout(), SHOUT.screen())
        assertEquals(Screen.Games, GAME.screen())
        assertEquals(Screen.Inbox, INBOX.screen())
        listOf(SONG, TOGETHER, SCORE).forEach { assertNull(it.screen()) }
    }

    @Test
    fun everyDropHasItsOwnSavedName() {
        assertEquals(QuickDrop.entries.size, QuickDrop.entries.map { it.key }.distinct().size)
        // The editor's grid is three to a row, as on the canvas.
        assertEquals(9, QuickDrop.entries.size)
    }
}
