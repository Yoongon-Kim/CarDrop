package com.cardrop.core.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** ScreenColumn's `bottom` slot: the last item goes to the bottom when there is room (CSS `margin-top: auto`). */
class LastItemAtBottomTest {

    /** 1 dp = 1 px, so the numbers below read as dp. */
    private val density = Density(1f)

    private fun positions(totalSize: Int, vararg sizes: Int): IntArray {
        val out = IntArray(sizes.size)
        with(LastItemAtBottom(14.dp)) { density.arrange(totalSize, sizes, out) }
        return out
    }

    @Test
    fun shortContentPutsTheLastItemAtTheBottom() {
        // A 852-high screen (900 minus 2 x 24 padding), a header (40), a card (200) and a 72-high button.
        assertArrayEquals(intArrayOf(0, 54, 780), positions(852, 40, 200, 72))
    }

    @Test
    fun longContentKeepsTheLastItemOneGapBelowTheContent() {
        // The content is taller than the screen, so the column grows and the button follows the card.
        val total = 40 + 14 + 900 + 14 + 72
        assertArrayEquals(intArrayOf(0, 54, 968), positions(total, 40, 900, 72))
    }

    @Test
    fun onlyTheBottomPartSitsAtTheBottom() {
        assertArrayEquals(intArrayOf(780), positions(852, 72))
    }
}
