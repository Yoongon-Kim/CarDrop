package com.cardrop.navi

import android.util.Log
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.haversineKm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owner: lead. The suggestion list while a driver types a place name, like Kakao Map's: board 02's
 * destination and board 10's "다른 곳을 직접 입력". A ViewModel makes one with its `viewModelScope`,
 * calls [onTyped] on every key, and the screen shows [state] (`PlaceSuggestionList`).
 *
 * [search] is Kakao's place search, or null without a Kakao key: then, and when the search fails
 * (no internet), only [knownPlaces] match. [here] is this car's position, so nearby places come
 * first and each line shows how far away it is.
 */
class PlaceSuggester(
    private val scope: CoroutineScope,
    private val search: PlaceSearch?,
    private val knownPlaces: List<Place>,
    private val here: () -> GeoPoint?,
) {
    private val _state = MutableStateFlow(PlaceSuggestions())
    val state: StateFlow<PlaceSuggestions> = _state

    /** False without a Kakao key: only [knownPlaces] can be found. */
    val canSearch: Boolean get() = search != null

    private var job: Job? = null
    /** The query of the running or last finished search, so "덕평" -> "덕평ㅈ" doesn't search again. */
    private var searchedQuery = ""

    /**
     * The driver typed [text]. After a short pause, search for it. Fewer than 2 letters: no search
     * (one syllable finds mostly unrelated places) and no suggestions.
     */
    fun onTyped(text: String) {
        val query = placeQuery(text)
        if (query == searchedQuery) return
        job?.cancel()
        searchedQuery = query
        if (query.length < MIN_QUERY) {
            _state.value = PlaceSuggestions()
            return
        }
        // Show "찾는 중" at once; the last results stay until the new ones arrive (no flicker).
        _state.value = _state.value.copy(query = query, searching = true)
        job = scope.launch {
            delay(TYPING_PAUSE_MS) // typing on: this search is cancelled and a newer one starts
            val found = find(query)
            if (found.note != null) searchedQuery = "" // typing the same again tries again
            _state.value = found
        }
    }

    /** No suggestions: the box was emptied, or a place was picked. */
    fun clear() {
        job?.cancel()
        searchedQuery = ""
        _state.value = PlaceSuggestions()
    }

    /**
     * The top place for [text] (typed and confirmed with 추가 / enter, or said): the first line on
     * screen when it is for the same text, else a fresh search. Null when nothing fits.
     */
    suspend fun best(text: String): Place? {
        val query = placeQuery(text)
        if (query.length < MIN_QUERY) return null
        val shown = _state.value
        val found = if (shown.query == query && !shown.searching) shown else find(query)
        return found.places.firstOrNull()?.place
    }

    /** All of [knownPlaces], with their distance: what board 02 offers before anything is typed. */
    fun known(): List<PlaceSuggestion> = knownPlaces.map { it.asSuggestion(here()) }

    /** Kakao's places for [query]; the known places when there is no Kakao key or the search fails. */
    suspend fun find(query: String): PlaceSuggestions {
        val near = here()
        val matches = { knownPlaceMatches(query, knownPlaces).map { it.asSuggestion(near) } }
        val search = search ?: return PlaceSuggestions(query, matches())
        return try {
            PlaceSuggestions(query, search.search(query, near))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Place search failed for '$query'", e)
            PlaceSuggestions(query, matches(), note = "장소 검색이 안 돼요. 인터넷 연결을 확인해 주세요.")
        }
    }

    private fun Place.asSuggestion(near: GeoPoint?) = PlaceSuggestion(
        place = this,
        address = detail,
        distanceKm = near?.let { haversineKm(it, GeoPoint(lat, lng)) },
    )

    private companion object {
        const val TAG = "CarDropPlaces"

        /** Searches start once this many letters are typed. */
        const val MIN_QUERY = 2

        /** Search after the driver stops typing for this long, not on every key. */
        const val TYPING_PAUSE_MS = 300L
    }
}

/** What the suggestion list shows. */
data class PlaceSuggestions(
    /** What the driver is looking for (the typed text without half-built letters); empty = no suggestions. */
    val query: String = "",
    val places: List<PlaceSuggestion> = emptyList(),
    /** True while a search runs. The last results stay on screen until the new ones arrive. */
    val searching: Boolean = false,
    /** Why the list holds only the places the app knows, e.g. no internet. */
    val note: String? = null,
) {
    /** True when the screen shows the suggestions (instead of its usual content). */
    val shown: Boolean get() = query.isNotEmpty()
}
