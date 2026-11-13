package com.cardrop.navi

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The search behind boards 02 and 10's suggestions (without the typing pause, which needs a clock). */
class PlaceSuggesterTest {

    private val gyeongpo = DemoData.destinations[0]
    private val near = GeoPoint(37.24, 127.38) // 덕평 area

    /** A search that answers [answer] and remembers what it was asked. */
    private class FakeSearch(val answer: List<PlaceSuggestion>) : PlaceSearch {
        val asked = mutableListOf<Pair<String, GeoPoint?>>()
        override suspend fun search(query: String, near: GeoPoint?): List<PlaceSuggestion> {
            asked += query to near
            return answer
        }
    }

    private fun suggester(search: PlaceSearch?) =
        PlaceSuggester(CoroutineScope(Dispatchers.Unconfined), search, DemoData.destinations, here = { near })

    @Test
    fun withASearchTheSearchAnswersNearTheCar() = runBlocking {
        val kakao = PlaceSuggestion(Place("kakao-1", "경포해수욕장", 37.80, 128.90), "해수욕장", "강원 강릉시 창해로 514", 230.0)
        val search = FakeSearch(listOf(kakao))
        val found = suggester(search).find("경포")
        assertEquals(listOf(kakao), found.places)
        assertEquals("경포", found.query)
        assertNull(found.note)
        assertEquals(listOf("경포" to near), search.asked)
    }

    @Test
    fun withoutASearchOnlyKnownPlacesMatchWithTheirDistance() = runBlocking {
        val found = suggester(null).find("경포")
        assertEquals(listOf(gyeongpo.id), found.places.map { it.place.id })
        assertEquals(gyeongpo.detail, found.places[0].address)
        assertTrue(found.places[0].distanceKm!! > 100) // 덕평 -> 강릉 is ~150 km in a straight line
    }

    @Test
    fun bestIsTheTopPlaceAndNeedsTwoLetters() = runBlocking {
        val first = PlaceSuggestion(Place("kakao-1", "경포대", 37.79, 128.89))
        val second = PlaceSuggestion(Place("kakao-2", "경포호", 37.80, 128.90))
        val suggester = suggester(FakeSearch(listOf(first, second)))
        assertEquals(first.place, suggester.best("경포 "))
        assertNull(suggester.best("경"))
        assertNull(suggester.best("ㄱ"))
    }

    @Test
    fun knownListsEveryKnownPlace() {
        assertEquals(DemoData.destinations.map { it.id }, suggester(null).known().map { it.place.id })
    }
}
