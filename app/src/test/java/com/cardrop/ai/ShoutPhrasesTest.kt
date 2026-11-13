package com.cardrop.ai

import com.cardrop.ai.ShoutPhrases.SaveResult.Problem
import com.cardrop.ai.ShoutPhrases.SaveResult.Saved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Board 09's phrase rules (design: 자주 쓰는 한마디). */
class ShoutPhrasesTest {

    @Test
    fun nothingSavedMeansTheDesignsEightPhrases() {
        assertEquals(ShoutPhrases.DEFAULTS, ShoutPhrases.fromSaved(null))
        assertEquals(6, ShoutPhrases.DEFAULTS.size)
    }

    @Test
    fun aListTheDriverEmptiedStaysEmpty() {
        assertEquals(emptyList<String>(), ShoutPhrases.fromSaved(emptyList()))
    }

    @Test
    fun aBrokenSavedListFallsBackToTheDefaults() {
        assertEquals(ShoutPhrases.DEFAULTS, ShoutPhrases.fromSaved(listOf("ok", " ")))
        assertEquals(ShoutPhrases.DEFAULTS, ShoutPhrases.fromSaved(listOf("x".repeat(81))))
    }

    @Test
    fun aNewPhraseGoesLastAndIsChosen() {
        val saved = ShoutPhrases.save(listOf("a", "b"), editing = null, text = "  c  ") as Saved
        assertEquals(listOf("a", "b", "c"), saved.phrases)
        assertEquals(2, saved.selected)
    }

    @Test
    fun editingReplacesThePhraseInPlace() {
        val saved = ShoutPhrases.save(listOf("a", "b"), editing = 0, text = "z") as Saved
        assertEquals(listOf("z", "b"), saved.phrases)
        assertEquals(0, saved.selected)
    }

    @Test
    fun emptyOrRepeatedTextIsRefused() {
        assertEquals(Problem("문구를 입력해주세요."), ShoutPhrases.save(listOf("a"), editing = null, text = "   "))
        assertEquals(Problem("이미 있는 문구예요."), ShoutPhrases.save(listOf("a", "b"), editing = null, text = "b"))
        assertEquals(Problem("이미 있는 문구예요."), ShoutPhrases.save(listOf("a", "b"), editing = 0, text = "b"))
        // Saving a phrase unchanged is fine.
        assertEquals(Saved(listOf("a", "b"), 1), ShoutPhrases.save(listOf("a", "b"), editing = 1, text = "b"))
    }

    @Test
    fun textLongerThan80CharactersIsCut() {
        val saved = ShoutPhrases.save(emptyList(), editing = null, text = "x".repeat(100)) as Saved
        assertEquals(80, saved.phrases.single().length)
    }

    @Test
    fun deletingChoosesThePhraseThatMovesIntoItsPlace() {
        assertEquals(Saved(listOf("a", "c"), 1), ShoutPhrases.delete(listOf("a", "b", "c"), 1))
        assertEquals(Saved(listOf("a", "b"), 1), ShoutPhrases.delete(listOf("a", "b", "c"), 2))
        assertEquals(Saved(emptyList(), 0), ShoutPhrases.delete(listOf("a"), 0))
    }

    @Test
    fun theSavedListReadsBackTheSame() {
        val list = listOf("곧 도착해요", "길 잘못 들었어ㅠ", "quote \" and, comma")
        assertEquals(list, ShoutPhrases.decode(ShoutPhrases.encode(list)))
        assertNull(ShoutPhrases.decode("not json"))
        assertNull(ShoutPhrases.decode(null))
    }
}
