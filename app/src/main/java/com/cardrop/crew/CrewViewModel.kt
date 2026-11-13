package com.cardrop.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.navi.PlaceSearch
import com.cardrop.navi.PlaceSuggester
import com.cardrop.navi.PlaceSuggestion
import com.cardrop.navi.PlaceSuggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owner: Member A (board 02's place search: lead). Screen state for boards 01, 02, 03, 07, 08 and 11. */
class CrewViewModel(
    private val repo: CrewRepository,
    /** Board 02's place search (Kakao), or null without a Kakao key: then only the app's destinations. */
    placeSearch: PlaceSearch? = null,
    /** This car's position (GPS), so nearby places come first and show their distance. */
    here: () -> GeoPoint? = { null },
) : ViewModel() {

    /**
     * Read on every use, like DriveViewModel's: with the real crew this device picks its car when joining
     * (board 03), after this ViewModel already exists. Screens redraw then because the crew changes.
     */
    val myCarId: String get() = repo.myCarId
    val crew: StateFlow<Crew?> = repo.crew
    val drops: StateFlow<List<Drop>> = repo.drops

    sealed interface JoinState {
        data object Idle : JoinState
        data object Searching : JoinState
        data class Found(val crew: Crew) : JoinState
        data object NotFound : JoinState
    }

    private val _join = MutableStateFlow<JoinState>(JoinState.Idle)
    val join: StateFlow<JoinState> = _join

    /** Board 01 opens with a crew ready to invite into. */
    fun ensureCrew() {
        if (repo.crew.value != null) return
        viewModelScope.launch { repo.createCrew(DemoData.DEMO_CREW_NAME, DemoData.gyeongpo) }
    }

    fun setDestination(place: Place) {
        viewModelScope.launch { repo.setDestination(place) }
    }

    /** Board 02: the places that fit the destination being typed, like Kakao Map's list. */
    private val destinationSearch = PlaceSuggester(viewModelScope, placeSearch, DemoData.destinations, here)
    val destinationSuggestions: StateFlow<PlaceSuggestions> = destinationSearch.state

    /** Board 02 opens with no old suggestions. */
    fun openDestinationSearch() = destinationSearch.clear()

    /** Board 02: the driver typed [text]; after a short pause the places that fit show. */
    fun onDestinationTyped(text: String) = destinationSearch.onTyped(text)

    /** Board 02 before anything is typed: the app's destinations, with their distance. */
    fun knownDestinations(): List<PlaceSuggestion> = destinationSearch.known()

    /** Board 02: the driver tapped a place. It becomes the crew's destination. */
    fun pickDestination(place: Place) {
        destinationSearch.clear()
        setDestination(place)
    }

    /** Board 02's enter key: the top place for [text] becomes the destination, then [onPicked] runs. */
    fun pickTypedDestination(text: String, onPicked: () -> Unit) {
        viewModelScope.launch {
            val place = destinationSearch.best(text) ?: return@launch // the list already says nothing fits
            pickDestination(place)
            onPicked()
        }
    }

    fun joinCrew(code: String) {
        _join.value = JoinState.Searching
        viewModelScope.launch {
            _join.value = repo.joinCrew(code)?.let { JoinState.Found(it) } ?: JoinState.NotFound
        }
    }

    fun sendDrop(draft: DropDraft, onSent: (Drop) -> Unit = {}) {
        viewModelScope.launch { onSent(repo.sendDrop(draft)) }
    }

    fun vote(dropId: String, candidateId: String) {
        viewModelScope.launch { repo.vote(dropId, candidateId) }
    }

    fun carName(carId: String): String =
        crew.value?.cars?.firstOrNull { it.id == carId }?.name ?: "알 수 없는 차"
}
