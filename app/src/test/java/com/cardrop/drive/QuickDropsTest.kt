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
    fun nothingSavedIncludesTheDrivingRecordSlot() {
        assertEquals(listOf(REST, FUEL, SCORE), QuickDrops(MemoryStore()).chosen.value)
    }

    @Test
    fun aListTheDriverEmptiedStaysEmpty() {
        val store = MemoryStore()
        QuickDrops(store).save(emptyList())
        assertEquals("", store.saved)
        // The next start doesn't bring 휴게소 and 주유 back.
        assertEquals(emptyList<QuickDrop>(), QuickDrops(store).chosen.value)
    }

    @Test
    fun savedDropsComeBackInTheirOrder() {
        assertEquals(listOf(FUEL, REST, INBOX), QuickDrops(MemoryStore("fuel,rest,inbox")).chosen.value)
    }

    @Test
    fun unknownRepeatedOrCutDropsAreLeftOut() {
        // 같이 켜기 is cut, 카페 doesn't exist, 휴게소 is there twice: the saved available drops keep their order.
        assertEquals(
            listOf(REST, SONG, SCORE, FOOD, SHOUT, GAME, INBOX),
            QuickDropRules.decode("rest,song,score,together,cafe,rest,,food,shout,game,inbox"),
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
    fun aFifthPickGoesToTheSecondRowAndUntickingStillWorks() {
        val four = listOf(REST, FOOD, FUEL, SHOUT)
        val five = four + INBOX
        assertEquals(Pick.Changed(five), QuickDropRules.pick(four, INBOX))
        assertEquals(Pick.Changed(listOf(REST, FOOD, SHOUT)), QuickDropRules.pick(four, FUEL))
        // Board 04: the first four in the slots, the fifth under them.
        assertEquals(emptyList<QuickDrop>(), QuickDropRules.secondRow(four))
        assertEquals(listOf(INBOX), QuickDropRules.secondRow(five))
    }

    @Test
    fun aPickBeyondTheLastSlotIsRefused() {
        val two = listOf(REST, FOOD)
        assertEquals(Pick.Full, QuickDropRules.pick(two, FUEL, max = 2))
        assertEquals(Pick.Changed(listOf(FOOD)), QuickDropRules.pick(two, REST, max = 2)) // unticking still works
    }

    @Test
    fun theAddSlotShowsWhileThereIsADropLeftToPick() {
        // Song drops and driving records both remain available; only 같이 켜기 is cut.
        val all = QuickDrop.entries.filter { it.available }
        assertEquals(listOf(REST, FOOD, FUEL, SHOUT, SONG, GAME, INBOX, SCORE), all)
        assertEquals(all.size, QuickDropRules.pickable)
        assertEquals(true, QuickDropRules.showsAddSlot(emptyList()))
        assertEquals(true, QuickDropRules.showsAddSlot(all.take(4)))
        assertEquals(true, QuickDropRules.showsAddSlot(all.take(6)))
        assertEquals(false, QuickDropRules.showsAddSlot(all)) // nothing left to add
    }

    @Test
    fun cutDropsCantBePicked() {
        listOf(TOGETHER).forEach { cut ->
            assertEquals(Pick.Changed(listOf(REST)), QuickDropRules.pick(listOf(REST), cut))
        }
    }

    @Test
    fun eachTileOpensWhatItNames() {
        // 휴게소 / 식사 / 주유: board 10 as a pop-up over 04, not a screen of their own.
        assertEquals(DropTopic.REST, REST.popupTopic())
        assertEquals(DropTopic.FOOD, FOOD.popupTopic())
        assertEquals(DropTopic.FUEL, FUEL.popupTopic())
        listOf(REST, FOOD, FUEL).forEach { assertNull(it.screen()) }
        assertEquals(Screen.Shout(), SHOUT.screen())
        assertEquals(Screen.Games, GAME.screen())
        assertEquals(Screen.Inbox, INBOX.screen())
        assertEquals(Screen.SongSend(), SONG.screen())
        assertEquals(Screen.Safety(), SCORE.screen())
        assertEquals(Pick.Changed(listOf(REST, SONG)), QuickDropRules.pick(listOf(REST), SONG))
        assertEquals(Pick.Changed(listOf(REST, SCORE)), QuickDropRules.pick(listOf(REST), SCORE))
        listOf(SHOUT, SONG, GAME, INBOX, SCORE).forEach { assertNull(it.popupTopic()) }
        listOf(TOGETHER).forEach {
            assertNull(it.screen())
            assertNull(it.popupTopic())
        }
    }

    @Test
    fun everyDropHasItsOwnSavedName() {
        assertEquals(QuickDrop.entries.size, QuickDrop.entries.map { it.key }.distinct().size)
        // The editor's grid is three to a row, as on the canvas.
        assertEquals(9, QuickDrop.entries.size)
    }
}
