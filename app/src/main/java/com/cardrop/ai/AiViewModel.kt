package com.cardrop.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.core.model.recipientLabel
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.drive.RouteSimulator
import com.cardrop.vehicle.VehicleRepository
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owner: lead. Screen state for boards 09, 10 and 15, plus the voice suggestion button. */
class AiViewModel(
    private val ai: AiService,
    private val crew: CrewRepository,
    private val route: RouteSimulator,
    private val vehicle: VehicleRepository,
    private val voice: VoiceService,
) : ViewModel() {

    sealed interface Loadable<out T> {
        data object Loading : Loadable<Nothing>
        data class Ready<T>(val value: T) : Loadable<T>
        data class Failed(val message: String) : Loadable<Nothing>
    }

    /** Everything board 10 shows. */
    data class PickerState(
        /** True while the AI is choosing. */
        val loading: Boolean = false,
        /** Why the AI could not answer. The driver can retry, or add places by hand. */
        val aiError: String? = null,
        val pick: PlacePick = PlacePick(),
        /** True while the mic listens for a place name. */
        val listening: Boolean = false,
        /** The place the mic heard, waiting for "후보에 넣기". */
        val heard: Place? = null,
        /** Grey line under the text box, e.g. "듣고 있어요…". */
        val status: String? = null,
        /** Red line under the text box, e.g. for a place we can't find. */
        val inputError: String? = null,
    )

    private val _picker = MutableStateFlow(PickerState(loading = true))
    val picker: StateFlow<PickerState> = _picker

    private val _quiz = MutableStateFlow<Loadable<QuizQuestion>>(Loadable.Loading)
    val quiz: StateFlow<Loadable<QuizQuestion>> = _quiz

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    val vehicleSnapshot: StateFlow<VehicleSnapshot> = vehicle.snapshot

    /** Places a driver may add by name on board 10. They have a location, so they can become a waypoint. */
    private val knownPlaces: List<Place> = DemoData.restStops + DemoData.cafes + DemoData.destinations

    private var loadJob: Job? = null
    private var listenJob: Job? = null
    private var sending = false

    /** Board 10: ask the AI for candidates. They all start ticked. */
    fun loadCandidates(topic: DropTopic) {
        loadJob?.cancel()
        listenJob?.cancel()
        val currentCrew = crew.crew.value
        if (currentCrew == null) {
            _picker.value = PickerState(aiError = "먼저 크루를 만들거나 참여해 주세요")
            return
        }
        if (topic == DropTopic.CUSTOM) {
            _picker.value = PickerState() // 직접 추가: no AI picks, the driver adds the places
            return
        }
        _picker.value = PickerState(loading = true)
        loadJob = viewModelScope.launch {
            _picker.value = try {
                val pool = when (topic) {
                    DropTopic.DESTINATION -> DemoData.destinations
                    DropTopic.CAFE -> DemoData.cafes
                    else -> DemoData.restStops
                }
                val picks = ai.recommend(topic, TripContext(currentCrew, route.positions.value, vehicle.snapshot.value, pool))
                PickerState(pick = PlacePick.fromAi(picks))
            } catch (e: CancellationException) {
                throw e // the screen asked again; that newer request wins
            } catch (e: Exception) {
                PickerState(aiError = e.message ?: "AI 추천에 실패했어요")
            }
        }
    }

    /** Board 10: tick or untick a place. */
    fun togglePlace(id: String) {
        _picker.update { it.copy(pick = it.pick.toggle(id)) }
    }

    /**
     * Board 10 "추가": the driver typed a place name. Returns true when the place was added (the
     * screen then empties the text box), false when we don't know where that place is.
     */
    fun addTypedPlace(name: String): Boolean {
        val text = name.trim()
        if (text.isEmpty()) return false
        val place = findPlace(text)
        if (place == null) {
            _picker.update { it.copy(heard = null, status = null, inputError = unknownPlace(text)) }
            return false
        }
        _picker.update { it.copy(pick = it.pick.add(place), heard = null, status = null, inputError = null) }
        return true
    }

    /** Board 10 "말해서 추가": listen for a place name, then show "'…' 후보에 넣기". */
    fun listenForPlace() {
        if (_picker.value.listening) return
        _picker.update { it.copy(listening = true, heard = null, status = "듣고 있어요…", inputError = null) }
        listenJob = viewModelScope.launch {
            val heard = try {
                voice.listen(placeName = true)?.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _picker.update { it.copy(listening = false, status = "마이크를 열 수 없어요. 키보드로 입력할 수 있어요.") }
                return@launch
            }
            val place = heard?.takeIf { it.isNotEmpty() }?.let(::findPlace)
            _picker.update {
                when {
                    heard.isNullOrEmpty() ->
                        it.copy(listening = false, status = "음성을 듣지 못했어요. 다시 시도하거나 키보드를 이용하세요.")
                    place == null -> it.copy(listening = false, status = null, inputError = unknownPlace(heard))
                    else -> it.copy(listening = false, heard = place, status = "장소 이름을 확인하고 추가하세요.")
                }
            }
        }
    }

    /** Board 10 "'평창휴게소' 후보에 넣기": add the place the mic heard. */
    fun confirmHeardPlace() {
        _picker.update { state ->
            val place = state.heard ?: return@update state
            state.copy(pick = state.pick.add(place), heard = null, status = null, inputError = null)
        }
    }

    /** "크루 전체", or the names of [toCarIds], for the "받는 차" lines on boards 09 and 10. */
    fun recipientLabel(toCarIds: List<String>): String =
        crew.crew.value?.recipientLabel(toCarIds, crew.myCarId) ?: "크루 전체"

    /**
     * Board 10 "이 N곳으로 투표 보내기": only the ticked places go into the vote, sent to [toCarIds]
     * (empty = the whole crew).
     */
    fun sendVote(topic: DropTopic, toCarIds: List<String>, onSent: (Drop) -> Unit) {
        val pick = _picker.value.pick
        val chosen = pick.selected
        if (chosen.isEmpty() || sending) return
        sending = true // a second tap must not send a second vote
        viewModelScope.launch {
            try {
                val drop = crew.sendDrop(
                    DropDraft(
                        kind = DropKind.DECISION,
                        topic = topic,
                        title = topicTitle(topic),
                        message = if (chosen.any { pick.isAdded(it.id) }) "후보 ${chosen.size}곳" else "AI가 고른 ${chosen.size}곳",
                        candidates = chosen,
                        toCarIds = toCarIds,
                    ),
                )
                voice.speak("${topicTitle(topic)} 투표를 보냈어요")
                onSent(drop)
            } finally {
                sending = false
            }
        }
    }

    /** Places already in the list first, so typing a listed name just ticks it. */
    private fun findPlace(name: String): Place? =
        findKnownPlace(name, _picker.value.pick.candidates.map { it.place } + knownPlaces)

    private fun unknownPlace(name: String) = "‘$name’의 위치를 찾지 못했어요. 휴게소나 카페 이름을 넣어 주세요."

    /** Board 04 "말로 제안해요": listen, read the intent, then open the right screen. */
    fun suggestByVoice(onTopic: (DropTopic) -> Unit, onMessage: (String) -> Unit) {
        if (_listening.value) return
        _listening.value = true
        viewModelScope.launch {
            try {
                val heard = voice.listen() ?: return@launch
                val intent = ai.readIntent(heard)
                if (intent.topic == DropTopic.MESSAGE) onMessage(intent.message ?: heard) else onTopic(intent.topic)
            } finally {
                _listening.value = false
            }
        }
    }

    /** Board 09: polish a message with the AI, then send it to [toCarIds] as a silent SOCIAL drop. */
    fun sendShout(raw: String, polish: Boolean, toCarIds: List<String>, onSent: () -> Unit) {
        viewModelScope.launch {
            val text = if (polish) ai.polishMessage(raw) else raw
            crew.sendDrop(DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = text, toCarIds = toCarIds))
            onSent()
        }
    }

    /** Board 15: a quiz about where the crew is right now. */
    fun loadQuiz() {
        _quiz.value = Loadable.Loading
        viewModelScope.launch {
            _quiz.value = runCatching { ai.localQuiz("이천") }
                .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "퀴즈를 못 만들었어요") })
        }
    }

    fun topicTitle(topic: DropTopic): String = when (topic) {
        DropTopic.REST -> "어느 휴게소에서 쉴까요?"
        DropTopic.FOOD -> "어디서 밥 먹을까요?"
        DropTopic.FUEL -> "어디서 주유할까요?"
        DropTopic.DESTINATION -> "목적지를 바꿀까요?"
        DropTopic.MESSAGE -> "한마디"
        DropTopic.CAFE -> "어느 카페에 들를까요?"
        DropTopic.CUSTOM -> "어디에 들를까요?"
    }
}
