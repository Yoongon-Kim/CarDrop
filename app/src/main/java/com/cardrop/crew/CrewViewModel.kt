package com.cardrop.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owner: Member A. Screen state for boards 01, 02, 03, 07, 08 and 11. */
class CrewViewModel(private val repo: CrewRepository) : ViewModel() {

    val myCarId: String = repo.myCarId
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
