package com.cardrop.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.navi.DestinationTrip
import com.cardrop.navi.PlaceSearch
import com.cardrop.navi.PlaceSuggester
import com.cardrop.navi.PlaceSuggestion
import com.cardrop.navi.PlaceSuggestions
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    /** Board 02 "말로 검색"; null hides the button. */
    private val voice: VoiceService? = null,
    /** Board 01: road distance and drive time to the destination (from the navigator), or null. */
    destinationTrip: StateFlow<DestinationTrip?> = MutableStateFlow(null),
) : ViewModel() {

    /** Board 01's "약 62km · 1시간 5분" for the crew destination; null until the navigator has a route. */
    val trip: StateFlow<DestinationTrip?> = destinationTrip


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

    /** Board 02 "말로 검색": the grey line under the box, and whether the mic is listening. */
    data class DestinationVoice(val listening: Boolean = false, val status: String? = null)

    private val _destinationVoice = MutableStateFlow(DestinationVoice())
    val destinationVoice: StateFlow<DestinationVoice> = _destinationVoice
    private var listenJob: Job? = null

    /** False without a voice service: board 02 then has no "말로 검색" button. */
    val canListen: Boolean get() = voice != null

    /** Board 02 opens with no old suggestions, and not listening. */
    fun openDestinationSearch() {
        stopListening()
        destinationSearch.clear()
    }

    /** Board 02 closes: stop the microphone if it is still listening. */
    fun stopListening() {
        listenJob?.cancel()
        _destinationVoice.value = DestinationVoice()
    }

    /**
     * Board 02 "말로 검색": listen for a place name, put what was heard into the box ([onHeard]) and
     * search for it. The driver then taps the right place: a misheard name never becomes the
     * destination on its own.
     */
    fun listenForDestination(onHeard: (String) -> Unit) {
        val voice = voice ?: return
        if (_destinationVoice.value.listening) return
        _destinationVoice.value = DestinationVoice(listening = true, status = "듣고 있어요… 가고 싶은 곳을 말해 주세요")
        listenJob = viewModelScope.launch {
            val heard = try {
                voice.listen(placeName = true)?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _destinationVoice.value = DestinationVoice(status = "마이크를 열 수 없어요. 키보드로 입력할 수 있어요.")
                return@launch
            }
            if (heard.isNullOrEmpty()) {
                _destinationVoice.value = DestinationVoice(status = "음성을 듣지 못했어요. 다시 말하거나 키보드로 입력하세요.")
                return@launch
            }
            _destinationVoice.value = DestinationVoice()
            onHeard(heard)
            destinationSearch.onTyped(heard)
        }
    }

    /** Board 02: the driver typed [text]; after a short pause the places that fit show. */
    fun onDestinationTyped(text: String) = destinationSearch.onTyped(text)

    /** Board 02 before anything is typed: the app's destinations, with their distance. */
    fun knownDestinations(): List<PlaceSuggestion> = destinationSearch.known()

    /** Board 02: the driver tapped a place. It becomes the crew's destination. */
    fun pickDestination(place: Place) {
        stopListening()
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

    /** Board 01 "말로 읽어주기": says the join code aloud, digit by digit. Nothing without a voice service. */
    fun speakCode() {
        val code = crew.value?.code ?: return
        voice?.speak(CrewVoice.codeSentence(code))
    }

    /** Board 01 "말로 읽어주기" is shown only with a voice service. */
    val canSpeak: Boolean get() = voice != null

    fun carName(carId: String): String =
        crew.value?.cars?.firstOrNull { it.id == carId }?.name ?: "알 수 없는 차"
}
