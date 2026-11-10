package com.cardrop.drop

import com.cardrop.core.model.Drop

/**
 * Owner: Member A.
 *
 * The vote rules from the design (board 07 and 11):
 * - A car that has not answered by the deadline counts as voting for candidate 1.
 * - The candidate with the most votes wins; a tie goes to the higher-ranked candidate.
 *
 * Pure functions with no Android code, so they are easy to unit test (see VoteRulesTest).
 */
object VoteRules {

    /** True when every car voted, or the deadline passed. */
    fun isOver(drop: Drop, carIds: List<String>, nowMillis: Long): Boolean =
        nowMillis >= drop.deadlineMillis || carIds.all { it in drop.votes }

    /** Returns the winning candidate id, or null if the drop has no candidates. */
    fun winner(drop: Drop, carIds: List<String>): String? {
        val first = drop.candidates.firstOrNull() ?: return null
        val ballots = carIds.map { carId -> drop.votes[carId] ?: first.id }
        val counts = ballots.groupingBy { it }.eachCount()
        // maxBy keeps the first maximum, and candidates are in rank order, so ties go to rank.
        return drop.candidates.maxBy { counts[it.id] ?: 0 }.id
    }

    /** How many cars agreed with the winner, e.g. "4대 중 3대 동의". */
    fun agreeing(drop: Drop, carIds: List<String>): Int {
        val winner = drop.decidedCandidateId ?: winner(drop, carIds) ?: return 0
        val first = drop.candidates.firstOrNull()?.id
        return carIds.count { carId -> (drop.votes[carId] ?: first) == winner }
    }
}
