package com.cardrop.voice

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpokenLineTest {

    @Test
    fun firstLineIsNumberOne() {
        assertEquals(SpokenLine("투표가 왔어요", 1), null.next("투표가 왔어요"))
    }

    @Test
    fun sameWordsTwiceAreTwoDifferentLines() {
        val first = null.next("투표가 왔어요")
        val second = first.next("투표가 왔어요")
        assertEquals(first.text, second.text)
        assertNotEquals(first, second)
    }

    /** The bug this fixes: a StateFlow ignores a value equal to the one it holds. */
    @Test
    fun aStateFlowSeesTheRepeatedSentence() {
        val flow = MutableStateFlow<SpokenLine?>(null)
        flow.value = flow.value.next("투표가 왔어요")
        val before = flow.value
        flow.value = flow.value.next("투표가 왔어요")
        assertNotEquals(before, flow.value)
        assertEquals(2, flow.value?.count)
    }
}
