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
        /** [routeBefore]: where this car's navigator was heading before it joined (null = no route). */
        data class Found(val crew: Crew, val routeBefore: Place? = null) : JoinState
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

    /** Board 02 "말로 검색" and board 03 "말로": whether the mic is listening, and the grey line about it. */
    data class VoiceInput(val listening: Boolean = false, val status: String? = null)

    private val _destinationVoice = MutableStateFlow(VoiceInput())
    val destinationVoice: StateFlow<VoiceInput> = _destinationVoice
    private val _codeVoice = MutableStateFlow(VoiceInput())
    /** Board 03's 말로 key, listening for the join code. */
    val codeVoice: StateFlow<VoiceInput> = _codeVoice
    private var listenJob: Job? = null

    /** False without a voice service: board 02 then has no "말로 검색" button, board 03's 말로 is greyed out. */
    val canListen: Boolean get() = voice != null

    /** Board 02 opens with no old suggestions, and not listening. */
    fun openDestinationSearch() {
        stopListening()
        destinationSearch.clear()
    }

    /** Board 02 or 03 closes: stop the microphone if it is still listening. */
    fun stopListening() {
        listenJob?.cancel()
        _destinationVoice.value = VoiceInput()
        _codeVoice.value = VoiceInput()
    }

    /**
     * Board 02 "말로 검색": listen for a place name, put what was heard into the box ([onHeard]) and
     * search for it. The driver then taps the right place: a misheard name never becomes the
     * destination on its own.
     */
    fun listenForDestination(onHeard: (String) -> Unit) {
        val voice = voice ?: return
        if (_destinationVoice.value.listening) return
        _destinationVoice.value = VoiceInput(listening = true, status = "듣고 있어요… 가고 싶은 곳을 말해 주세요")
        listenJob = viewModelScope.launch {
            val heard = try {
                voice.listen(placeName = true)?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _destinationVoice.value = VoiceInput(status = "마이크를 열 수 없어요. 키보드로 입력할 수 있어요.")
                return@launch
            }
            if (heard.isNullOrEmpty()) {
                _destinationVoice.value = VoiceInput(status = "음성을 듣지 못했어요. 다시 말하거나 키보드로 입력하세요.")
                return@launch
            }
            _destinationVoice.value = VoiceInput()
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

    /** The code board 03 is looking up now; null = none (the driver changed the code). */
    private var joiningCode: String? = null

    /**
     * Board 03: the driver typed the last digit of [code]. Looks the crew up and joins it. [routeBefore] is
     * where this car's navigator is heading now. Read it before joining: joining puts the crew's destination
     * in its place at once (TripCoordinator), and board 03 offers it as a stop on the way ("…들렀다 가기").
     */
    fun joinCrew(code: String, routeBefore: Place? = null) {
        joiningCode = code
        clearCodeStatus()
        _join.value = JoinState.Searching
        viewModelScope.launch {
            val found = repo.joinCrew(code)
            // The driver erased a digit while we were looking: this answer is for a code no longer on screen.
            if (joiningCode != code) return@launch
            _join.value = found?.let { JoinState.Found(it, routeBefore) } ?: JoinState.NotFound
        }
    }

    /** Board 03: the code changed (a digit erased, or the screen opened again): no crew shown. */
    fun clearJoin() {
        joiningCode = null
        clearCodeStatus()
        _join.value = JoinState.Idle
    }

    /** The driver changed the code by hand: an old line about what the mic heard no longer fits. */
    private fun clearCodeStatus() {
        if (!_codeVoice.value.listening) _codeVoice.value = VoiceInput()
    }

    /**
     * Board 03 "말로": listen for the join code ("코드 482917", "사팔이 구일칠") and hand its 6 digits to
     * [onHeard]. Anything else ("배고파", 5 digits) leaves the boxes as they are, and the grey line says so.
     */
    fun listenForCode(onHeard: (String) -> Unit) {
        val voice = voice ?: return
        if (_codeVoice.value.listening) return
        _codeVoice.value = VoiceInput(listening = true, status = "듣고 있어요… “코드 482917”처럼 말해 주세요")
        listenJob = viewModelScope.launch {
            val heard = try {
                voice.listen()?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _codeVoice.value = VoiceInput(status = "마이크를 열 수 없어요. 숫자를 눌러 넣어 주세요.")
                return@launch
            }
            val code = heard?.let(JoinRules::codeFromSpeech)
            _codeVoice.value = when {
                heard.isNullOrEmpty() -> VoiceInput(status = "음성을 듣지 못했어요. 다시 말하거나 숫자를 눌러 주세요.")
                code == null -> VoiceInput(status = "‘$heard’에서 코드 6자리를 찾지 못했어요")
                else -> VoiceInput()
            }
            if (code != null) onHeard(code)
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
