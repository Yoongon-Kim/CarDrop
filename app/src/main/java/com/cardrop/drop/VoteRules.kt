package com.cardrop.drop

import com.cardrop.core.model.Drop

/**
 * Owner: Member A.
 *
 * The vote rules from the design (board 07 and 11):
 * - A car that has not answered by the deadline counts as voting for candidate 1.
 * - The candidate with the most votes wins; a tie goes to the higher-ranked candidate.
 * - A car can pass (board 11 "난 패스, 먼저 갈게", D14): it answered, so the vote doesn't wait for it, but its
 *   vote counts for no place. It keeps its own route and only hears the result.
 *
 * Pure functions with no Android code, so they are easy to unit test (see VoteRulesTest).
 */
object VoteRules {

    /**
     * The vote a car sends when it passes. `Drop.votes` maps each car to the candidate it chose, so a pass is
     * written there like any vote, with this value instead of a candidate id.
     */
    const val PASS = "_pass"

    /** True when every car answered (a pass is an answer), or the deadline passed. */
    fun isOver(drop: Drop, carIds: List<String>, nowMillis: Long): Boolean =
        nowMillis >= drop.deadlineMillis || carIds.all { it in drop.votes }

    /**
     * Returns the winning candidate id, or null if the drop has no candidates. Cars that passed are left out.
     * If every car passed, candidate 1 still closes the vote; nobody stops there, as every car turned it down.
     */
    fun winner(drop: Drop, carIds: List<String>): String? {
        val first = drop.candidates.firstOrNull() ?: return null
        val counts = carIds.mapNotNull { ballot(drop, it, first.id) }.groupingBy { it }.eachCount()
        // maxBy keeps the first maximum, and candidates are in rank order, so ties go to rank.
        return drop.candidates.maxBy { counts[it.id] ?: 0 }.id
    }

    /** How many cars agreed with the winner, e.g. "4대 중 3대 동의". A car that passed didn't. */
    fun agreeing(drop: Drop, carIds: List<String>): Int {
        val winner = drop.decidedCandidateId ?: winner(drop, carIds) ?: return 0
        val first = drop.candidates.firstOrNull()?.id ?: return 0
        return carIds.count { ballot(drop, it, first) == winner }
    }

    /** True when [carId] passed on this vote. */
    fun passed(drop: Drop, carId: String): Boolean = drop.votes[carId] == PASS

    /** The candidate [carId]'s vote counts for: its own choice, candidate 1 if it said nothing, none if it passed. */
    private fun ballot(drop: Drop, carId: String, firstId: String): String? = when (val vote = drop.votes[carId]) {
        null -> firstId
        PASS -> null
        else -> vote
    }
}
