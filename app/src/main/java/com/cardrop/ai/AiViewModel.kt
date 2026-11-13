package com.cardrop.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.core.model.recipientLabel
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RouteSimulator
import com.cardrop.drive.haversineKm
import com.cardrop.drop.voteTag
import com.cardrop.navi.PlaceSearch
import com.cardrop.navi.PlaceSuggester
import com.cardrop.navi.PlaceSuggestion
import com.cardrop.navi.PlaceSuggestions
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
    private val phraseStore: PhraseStore,
    /** Board 10's place search (Kakao), or null without a Kakao key: then only the places the app knows. */
    placeSearch: PlaceSearch? = null,
    /** This car's position (GPS), so nearby places come first. */
    here: () -> GeoPoint? = { null },
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
        /** Red line under the text box (just above the send button), e.g. for a place we can't find or a vote not sent. */
        val inputError: String? = null,
    )

    private val _picker = MutableStateFlow(PickerState(loading = true))
    val picker: StateFlow<PickerState> = _picker

    /** Everything board 09 shows. */
    data class ShoutState(
        val phrases: List<String> = emptyList(),
        /** The chosen phrase's index, or [SPOKEN] for what the driver said. */
        val selected: Int = 0,
        /** What the driver said (04's 말로 제안해요, or board 09's voice row), polished by the AI. */
        val spoken: String? = null,
        /** True while listening, or while the AI polishes what was said. */
        val hearing: Boolean = false,
        /** The open phrase editor, or null when it's closed. */
        val editor: PhraseEditor? = null,
        /** Red line above the send button: why the last message wasn't sent, e.g. before 출발. */
        val sendError: String? = null,
    ) {
        /** What the send button sends, or null when there's nothing to send. */
        val chosenText: String? get() = if (selected == SPOKEN) spoken else phrases.getOrNull(selected)

        companion object {
            const val SPOKEN = -1
        }
    }

    /** Board 09's editor. [editing] is the index being changed, or null for a new phrase. */
    data class PhraseEditor(val editing: Int?, val text: String, val error: String? = null)

    private val _shout = MutableStateFlow(ShoutState(phrases = ShoutPhrases.fromSaved(phraseStore.load())))
    val shout: StateFlow<ShoutState> = _shout

    private val _quiz = MutableStateFlow<Loadable<QuizQuestion>>(Loadable.Loading)
    val quiz: StateFlow<Loadable<QuizQuestion>> = _quiz

    private val _round = MutableStateFlow(QuizRound())
    /** Board 15: the game, question number, score and opponent. Kept here, so a game paused in D resumes in P. */
    val round: StateFlow<QuizRound> = _round

    /** The crew, for board 15's opponent picker. */
    val crewState: StateFlow<Crew?> = crew.crew
    val myCarId: String get() = crew.myCarId

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    val vehicleSnapshot: StateFlow<VehicleSnapshot> = vehicle.snapshot

    /**
     * Board 10's suggestions while the driver types, like Kakao Map's. Without a Kakao key (or offline)
     * only the places the app knows match: they have a location, so they can become a waypoint.
     */
    private val suggester = PlaceSuggester(
        scope = viewModelScope,
        search = placeSearch,
        knownPlaces = DemoData.restStops + DemoData.cafes + DemoData.destinations,
        here = here,
    )
    val suggestions: StateFlow<PlaceSuggestions> = suggester.state

    private var loadJob: Job? = null
    private var listenJob: Job? = null
    private var sending = false

    /**
     * Board 10: ask the AI for candidates. They all start ticked. The AI plans for the cars that
     * take part: this car and [toCarIds] (all cars when it goes to the whole crew).
     */
    fun loadCandidates(topic: DropTopic, toCarIds: List<String> = emptyList()) {
        loadJob?.cancel()
        listenJob?.cancel()
        suggester.clear()
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
                    // Not where the crew is already going: a vote to change it must offer somewhere else.
                    DropTopic.DESTINATION -> DemoData.destinations.filterNot { it.isNear(currentCrew.destination) }
                    DropTopic.CAFE -> DemoData.cafes
                    else -> DemoData.restStops
                }
                val involved = if (toCarIds.isEmpty()) {
                    currentCrew
                } else {
                    currentCrew.copy(cars = currentCrew.cars.filter { it.id == crew.myCarId || it.id in toCarIds })
                }
                val picks = ai.recommend(topic, TripContext(involved, route.positions.value, vehicle.snapshot.value, pool))
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

    /** Board 10: the driver typed [text] in the box. After a short pause the places that fit replace the list. */
    fun onTyped(text: String) {
        suggester.onTyped(text)
        if (_picker.value.inputError != null) _picker.update { it.copy(inputError = null) }
    }

    /** Board 10: the driver tapped a suggestion. It goes into the list, ticked; the screen empties the box. */
    fun pickSuggestion(suggestion: PlaceSuggestion) {
        addPlace(suggestion.place)
    }

    /**
     * Board 10 "추가" (or the keyboard's done key): adds the place the driver typed, the same one
     * as the top suggestion. [onAdded] runs once it is in, so the screen can empty the text box.
     */
    fun addTypedPlace(name: String, onAdded: () -> Unit) {
        val text = name.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val place = resolvePlace(text)
            if (place == null) {
                _picker.update { it.copy(heard = null, status = null, inputError = unknownPlace(text)) }
            } else {
                addPlace(place)
                onAdded()
            }
        }
    }

    private fun addPlace(place: Place) {
        suggester.clear()
        _picker.update { it.copy(pick = it.pick.add(place), heard = null, status = null, inputError = null) }
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
            val place = heard?.takeIf { it.isNotEmpty() }?.let { resolvePlace(it) }
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

    /**
     * The cars a drop to [toCarIds] is for, in crew order: this car and [toCarIds], or every car when it goes to
     * the whole crew. The same cars vote on it (`Drop.voterIds`).
     */
    fun involvedCarIds(toCarIds: List<String>): List<String> =
        crew.crew.value?.cars.orEmpty().map { it.id }.filter { toCarIds.isEmpty() || it == crew.myCarId || it in toCarIds }

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
        val added = chosen.count { pick.isAdded(it.id) }
        _picker.update { it.copy(inputError = null) }
        viewModelScope.launch {
            try {
                val drop = try {
                    crew.sendDrop(
                        DropDraft(
                            kind = DropKind.DECISION,
                            topic = topic,
                            title = topicTitle(topic),
                            message = voteReason(cars = involvedCarIds(toCarIds).size, aiPicks = chosen.size - added, added = added),
                            candidates = chosen,
                            toCarIds = toCarIds,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Not sent, e.g. before 출발: the red line above the send button says why (and the app stays open).
                    _picker.update { it.copy(inputError = notSentText(e)) }
                    return@launch
                }
                voice.speak(voteSentLine(topic))
                onSent(drop)
            } finally {
                sending = false
            }
        }
    }

    /**
     * The place the driver means by [name] (typed or said). Places already in the list first, so
     * a listed name just ticks it; then the top search result.
     */
    private suspend fun resolvePlace(name: String): Place? =
        findKnownPlace(name, _picker.value.pick.candidates.map { it.place }) ?: suggester.best(name)

    private fun unknownPlace(name: String) =
        if (suggester.canSearch) "‘$name’에 맞는 장소를 찾지 못했어요. 다른 이름으로 찾아 보세요."
        else "‘$name’의 위치를 찾지 못했어요. 휴게소나 카페 이름을 넣어 주세요."

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

    /** Board 09 opens. [initialText] is what the driver said on 04, if anything; the AI polishes it. */
    fun openShout(initialText: String) {
        _shout.update {
            it.copy(
                editor = null, spoken = null, sendError = null,
                selected = it.selected.coerceIn(0, maxOf(0, it.phrases.size - 1)),
            )
        }
        if (initialText.isNotBlank()) {
            _shout.update { it.copy(hearing = true) }
            viewModelScope.launch { polishSpoken(initialText) }
        }
    }

    fun selectPhrase(index: Int) {
        _shout.update { it.copy(selected = index, editor = null) }
    }

    fun selectSpoken() {
        _shout.update { if (it.spoken == null) it else it.copy(selected = ShoutState.SPOKEN, editor = null) }
    }

    /** "문구 추가": an empty editor for a new phrase. */
    fun newPhrase() {
        _shout.update { it.copy(editor = PhraseEditor(editing = null, text = "")) }
    }

    /** "선택한 문구 고치기": the editor with the chosen phrase in it. */
    fun editPhrase() {
        _shout.update { state ->
            val text = state.phrases.getOrNull(state.selected) ?: return@update state
            state.copy(editor = PhraseEditor(editing = state.selected, text = text))
        }
    }

    fun changePhrase(text: String) {
        _shout.update { state -> state.editor?.let { state.copy(editor = it.copy(text = text, error = null)) } ?: state }
    }

    fun cancelPhrase() {
        _shout.update { it.copy(editor = null) }
    }

    /** "저장": saves the editor's text, or shows why it can't be saved. */
    fun savePhrase() {
        val state = _shout.value
        val editor = state.editor ?: return
        when (val result = ShoutPhrases.save(state.phrases, editor.editing, editor.text)) {
            is ShoutPhrases.SaveResult.Problem -> _shout.value = state.copy(editor = editor.copy(error = result.message))
            is ShoutPhrases.SaveResult.Saved -> keepPhrases(result)
        }
    }

    /** "삭제": deletes the phrase being edited. */
    fun deletePhrase() {
        val state = _shout.value
        val index = state.editor?.editing ?: return
        keepPhrases(ShoutPhrases.delete(state.phrases, index))
    }

    private fun keepPhrases(saved: ShoutPhrases.SaveResult.Saved) {
        phraseStore.save(saved.phrases)
        _shout.update { it.copy(phrases = saved.phrases, selected = saved.selected, editor = null) }
    }

    /** Board 09 "직접 말해서 보내기": listen, then the AI polishes what was said into the chosen phrase. */
    fun listenForShout() {
        if (_shout.value.hearing) return
        _shout.update { it.copy(hearing = true, editor = null) }
        viewModelScope.launch {
            try {
                val heard = voice.listen()
                if (!heard.isNullOrBlank()) polishSpoken(heard)
            } finally {
                _shout.update { it.copy(hearing = false) }
            }
        }
    }

    private suspend fun polishSpoken(raw: String) {
        val text = try {
            ai.polishMessage(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            raw // send the driver's own words rather than nothing
        }
        _shout.update { it.copy(spoken = text, selected = ShoutState.SPOKEN, hearing = false) }
    }

    /** Board 09 "“…” 보내기": sends the chosen phrase to [toCarIds] as a silent SOCIAL drop. */
    fun sendChosenShout(toCarIds: List<String>, onSent: () -> Unit) {
        val state = _shout.value
        if (state.editor != null || state.hearing) return
        val text = state.chosenText ?: return
        sendShout(text, polish = false, toCarIds = toCarIds, onSent = onSent)
    }

    /** A car to name in board 09's example sentence: the purple car (삼촌 차 in the demo crew). */
    fun exampleCarName(): String {
        val others = crew.crew.value?.cars.orEmpty().filter { it.id != crew.myCarId }
        return (others.firstOrNull { it.color == CarColor.PURPLE } ?: others.firstOrNull())?.name ?: "일행 차"
    }

    /**
     * Sends [raw] (polished by the AI first if [polish]) to [toCarIds] as a silent SOCIAL drop. When it can't be sent
     * (e.g. before 출발), [ShoutState.sendError] says why and [onSent] doesn't run.
     */
    fun sendShout(raw: String, polish: Boolean, toCarIds: List<String>, onSent: () -> Unit) {
        if (sending) return
        sending = true // a second tap must not send the message twice
        _shout.update { it.copy(sendError = null) }
        viewModelScope.launch {
            try {
                try {
                    val text = if (polish) ai.polishMessage(raw) else raw
                    crew.sendDrop(DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = text, toCarIds = toCarIds))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _shout.update { it.copy(sendError = notSentText(e)) }
                    return@launch
                }
                onSent()
            } finally {
                sending = false
            }
        }
    }

    /**
     * Why a drop wasn't sent, for boards 09 and 10: the crew's own reason when it gave one in Korean
     * ([com.cardrop.crew.TripNotStartedException]'s "출발하면 드랍을 보낼 수 있어요", "크루에 먼저 참여해 주세요"), else a
     * plain retry line.
     */
    private fun notSentText(e: Exception): String =
        (e as? IllegalStateException)?.message ?: "보내지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요"

    /**
     * Board 15: the current question of the round. 초성 퀴즈 comes from the built-in list; 지역 퀴즈 asks the AI
     * about the area the crew is driving through.
     */
    fun loadQuiz() {
        val round = _round.value
        if (round.kind == QuizKind.INITIALS) {
            _quiz.value = Loadable.Ready(InitialsQuiz.question(round.number))
            return
        }
        _quiz.value = Loadable.Loading
        viewModelScope.launch {
            _quiz.value = runCatching { ai.localQuiz(currentRegion()) }
                .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "퀴즈를 못 만들었어요") })
        }
    }

    /** Board 15 opened in P: load a question unless one is already showing (a paused game keeps its question). */
    fun ensureQuiz() {
        if (_quiz.value !is Loadable.Ready && !_round.value.finished) loadQuiz()
    }

    fun chooseQuiz(kind: QuizKind) {
        if (kind == _round.value.kind) return
        _round.update { it.restarted(kind) }
        loadQuiz()
    }

    fun chooseOpponent(carId: String?) = _round.update { it.copy(opponentId = carId, challengeSent = false) }

    fun answerQuiz(choice: String) {
        val question = (_quiz.value as? Loadable.Ready)?.value ?: return
        _round.update { it.answered(choice, question) }
    }

    fun nextQuestion() {
        _round.update { it.next() }
        if (!_round.value.finished) loadQuiz()
    }

    fun restartQuiz() {
        _round.update { it.restarted() }
        loadQuiz()
    }

    /** "도전장 드랍": invites the chosen car (or the whole crew) as a silent SOCIAL drop, like the design. */
    fun sendChallenge() {
        val round = _round.value
        if (round.challengeSent) return
        sendShout("${round.kind.title} 한 판 할까요? · 정차 중에 같이 해요", polish = false, toCarIds = listOfNotNull(round.opponentId)) {
            _round.update { it.copy(challengeSent = true) }
        }
    }

    /** The area the crew is driving through: the town of the nearest rest stop (이천 until there's a position). */
    private fun currentRegion(): String {
        val here = route.positions.value[crew.myCarId] ?: return "이천"
        val nearest = DemoData.restStops.minByOrNull {
            val dLat = it.lat - here.lat
            val dLng = it.lng - here.lng
            dLat * dLat + dLng * dLng
        }
        return REST_STOP_TOWNS[nearest?.id] ?: "이천"
    }

    /** The vote's title, the big line on board 11 (target lines 711-713). */
    fun topicTitle(topic: DropTopic): String = when (topic) {
        DropTopic.REST -> "어디서 쉬어갈까요?"
        DropTopic.FOOD -> "뭐 먹고 갈까요?"
        DropTopic.FUEL -> "어디서 주유할까요?"
        DropTopic.DESTINATION -> "목적지를 바꿀까요?"
        DropTopic.MESSAGE -> "한마디"
        DropTopic.CAFE -> "어느 카페에 들를까요?"
        DropTopic.CUSTOM -> "어디에 들를까요?"
    }
}

/**
 * Board 11's grey line under the title: why these places are in the vote, e.g. "AI가 4대 모두 가기 좋은 곳으로
 * 골랐어요" (target line 712). It travels in the vote drop's `message`. [cars] = how many cars take part in the
 * vote; [aiPicks] and [added] = how many of its places the AI picked and how many the driver added on board 10.
 * Internal (not private) only so VoteReasonTest can check it.
 */
internal fun voteReason(cars: Int, aiPicks: Int, added: Int): String = when {
    added == 0 && cars >= 2 -> "AI가 ${cars}대 모두 가기 좋은 곳으로 골랐어요"
    added == 0 -> "AI가 가기 좋은 곳으로 골랐어요"
    aiPicks == 0 -> "직접 고른 ${added}곳 중에서 골라요"
    else -> "AI가 고른 ${aiPicks}곳에 직접 고른 ${added}곳을 더했어요"
}

/**
 * What the voice says once board 10 (page or quick-drop pop-up) has sent its vote: "휴게소 투표를 보냈어요", with the
 * topic named as on board 11's tag and on 04's tiles (휴게소 / 식사 / 주유 / 목적지 변경 / 카페 / 들를 곳).
 * Internal (not private) only so VoteReasonTest can check it.
 */
internal fun voteSentLine(topic: DropTopic): String = "${topic.voteTag()} 투표를 보냈어요"

/**
 * True when [other] is the same spot: within 1 km. The crew destination can be Kakao's place for the same beach
 * under another id and name ("경포해변" for the app's "강릉 경포해변"), so the id alone can't tell.
 */
private fun Place.isNear(other: Place): Boolean =
    id == other.id || haversineKm(GeoPoint(lat, lng), GeoPoint(other.lat, other.lng)) < 1.0

/** The town each rest stop on the demo trip is in (board 15's 지역 퀴즈). 덕평 is in 이천, 문막 in 원주. */
private val REST_STOP_TOWNS = mapOf(
    "deokpyeong" to "이천",
    "yeoju" to "여주",
    "munmak" to "원주",
    "hoengseong" to "횡성",
    "pyeongchang" to "평창",
    "gangneung-rest" to "강릉",
)
