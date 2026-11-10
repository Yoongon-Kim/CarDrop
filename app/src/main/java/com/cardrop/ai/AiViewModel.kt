package com.cardrop.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.QuizQuestion
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.DropDraft
import com.cardrop.drive.RouteSimulator
import com.cardrop.vehicle.VehicleRepository
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _candidates = MutableStateFlow<Loadable<List<Candidate>>>(Loadable.Loading)
    val candidates: StateFlow<Loadable<List<Candidate>>> = _candidates

    private val _quiz = MutableStateFlow<Loadable<QuizQuestion>>(Loadable.Loading)
    val quiz: StateFlow<Loadable<QuizQuestion>> = _quiz

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    val vehicleSnapshot: StateFlow<VehicleSnapshot> = vehicle.snapshot

    /** Board 10: ask the AI for candidates. */
    fun loadCandidates(topic: DropTopic) {
        val currentCrew = crew.crew.value ?: return
        _candidates.value = Loadable.Loading
        viewModelScope.launch {
            _candidates.value = runCatching {
                ai.recommend(topic, TripContext(currentCrew, route.positions.value, vehicle.snapshot.value))
            }.fold({ Loadable.Ready(it) }, { Loadable.Failed(it.message ?: "AI 추천에 실패했어요") })
        }
    }

    /** Board 10: "이 3곳으로 투표 보내기". */
    fun sendVote(topic: DropTopic, candidates: List<Candidate>, onSent: (Drop) -> Unit) {
        viewModelScope.launch {
            val drop = crew.sendDrop(
                DropDraft(
                    kind = DropKind.DECISION,
                    topic = topic,
                    title = topicTitle(topic),
                    message = "AI가 고른 ${candidates.size}곳",
                    candidates = candidates,
                ),
            )
            voice.speak("${topicTitle(topic)} 투표를 보냈어요")
            onSent(drop)
        }
    }

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

    /** Board 09: polish a message with the AI, then send it as a silent SOCIAL drop. */
    fun sendShout(raw: String, polish: Boolean, onSent: () -> Unit) {
        viewModelScope.launch {
            val text = if (polish) ai.polishMessage(raw) else raw
            crew.sendDrop(DropDraft(kind = DropKind.SOCIAL, topic = DropTopic.MESSAGE, title = text))
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
    }
}
