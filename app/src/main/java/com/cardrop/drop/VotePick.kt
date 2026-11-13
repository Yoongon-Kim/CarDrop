package com.cardrop.drop

import com.cardrop.core.model.Candidate

/**
 * Owner: Member A. What this car picks on board 11 before it presses 응답하기: one of the places, 아무 데나 좋아,
 * or 난 패스, 먼저 갈게 (the target's `pick`: o1 / o2 / o3 / any / no, line 716). Pure Kotlin, unit tested
 * (VotePickTest).
 */
sealed interface VotePick {
    /** One of the places. */
    data class Place(val candidateId: String) : VotePick

    /** 아무 데나 좋아: "anywhere is fine", a vote for place 1 (the AI's best). */
    data object Anywhere : VotePick

    /** 난 패스, 먼저 갈게: no place; this car keeps its route and only hears the result (D14). */
    data object Pass : VotePick

    /** The vote 응답하기 sends: the place's id, place 1 for 아무 데나, [VoteRules.PASS] for a pass. Null = nothing to send. */
    fun vote(candidates: List<Candidate>): String? = when (this) {
        is Place -> candidateId
        Anywhere -> candidates.firstOrNull()?.id
        Pass -> VoteRules.PASS
    }

    /** The place this car's vote counts for, or null for a pass. */
    fun countedId(candidates: List<Candidate>): String? = vote(candidates)?.takeIf { it != VoteRules.PASS }

    companion object {
        /** What the screen starts with: this car's vote if it sent one, otherwise place 1 (as the target does). */
        fun from(vote: String?, candidates: List<Candidate>): VotePick = when {
            vote == VoteRules.PASS -> Pass
            vote != null -> Place(vote)
            else -> candidates.firstOrNull()?.let { Place(it.id) } ?: Anywhere
        }
    }
}
