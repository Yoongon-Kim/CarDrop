package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropTopic

/**
 * Owner: Member A.
 *
 * The vote rules from the design (board 07 and 11):
 * - A car that has not answered by the deadline counts as voting for candidate 1.
 * - The candidate with the most votes wins; a tie goes to the higher-ranked candidate.
 * - A car can pass (board 11 "난 패스, 먼저 갈게", D14): it answered, so the vote doesn't wait for it, but its
 *   vote counts for no place. It keeps its own route and only hears the result.
 * - A destination proposal ([isProposal]) is one place to say 찬성 or 반대 to, not a choice between places: one 반대
 *   turns it down at once ([REJECTED]); a car that says nothing counts as 찬성, like candidate 1 above.
 * - A quick drop's sender (board 04's pop-up) doesn't vote (lead, 2026-11-15): the callers pass `Drop.voterIds`, which
 *   leaves it out, so the vote ends once the other cars have answered and only their answers count.
 *
 * Pure functions with no Android code, so they are easy to unit test (see VoteRulesTest).
 */
object VoteRules {

    /**
     * The vote a car sends when it passes. `Drop.votes` maps each car to the candidate it chose, so a pass is
     * written there like any vote, with this value instead of a candidate id.
     */
    const val PASS = "_pass"

    /**
     * The vote a car sends when it says 반대 to a destination proposal. Written into `Drop.votes` like [PASS].
     * "다른 목적지 제안하기" sends it too, before the new proposal goes out.
     */
    const val NO = "_no"

    /**
     * What `Drop.decidedCandidateId` holds when a destination proposal was turned down. It is no candidate's id, so
     * `Drop.decidedCandidate` is null: the navigator and board 14 leave such a drop alone, and the destination stays.
     */
    const val REJECTED = "_rejected"

    /**
     * True for a destination proposal: a 목적지 변경 drop with exactly one place ("속초 해수욕장으로 바꿀까요?").
     * The cars answer 찬성 (that place's id) or 반대 ([NO]). With two or more places it is an ordinary vote.
     */
    fun isProposal(drop: Drop): Boolean = drop.topic == DropTopic.DESTINATION && drop.candidates.size == 1

    /** True when [carId] said 반대 to this proposal. */
    /**
     * A quick drop with one place (04's pop-up; Member A, 2026-11-15): the other cars answer only 예 (a vote for it) or
     * 아니요 ([PASS]: that car doesn't stop there). It always ends on its place, so the cars that said 예, the silent
     * ones and the sender stop there, and the cars that said 아니요 keep their route.
     */
    fun isYesNo(drop: Drop): Boolean = !drop.senderVotes && drop.candidates.size == 1 && !isProposal(drop)

    fun saidNo(drop: Drop, carId: String): Boolean = drop.votes[carId] == NO

    /** True when this proposal is over and was turned down. */
    fun rejected(drop: Drop): Boolean = drop.decidedCandidateId == REJECTED

    /**
     * True when every car answered (a pass is an answer), or the deadline passed. A proposal is also over as
     * soon as one car says 반대: nothing can save it after that.
     */
    fun isOver(drop: Drop, carIds: List<String>, nowMillis: Long): Boolean =
        nowMillis >= drop.deadlineMillis || carIds.all { answered(drop, it) } ||
            (isProposal(drop) && carIds.any { saidNo(drop, it) })

    /**
     * True when [carId] answered. The car that sent a proposal answered by sending it (찬성), even without a vote
     * in `Drop.votes`: a bot's proposal and a board 10 vote with one place have none, and board 11 gives that car
     * no buttons. Without this, such a proposal would wait for the timer even when everyone said 찬성.
     */
    private fun answered(drop: Drop, carId: String): Boolean =
        carId in drop.votes || (isProposal(drop) && carId == drop.fromCarId)

    /**
     * Returns the winning candidate id, or null if the drop has no candidates. Cars that passed are left out.
     * If every car passed, candidate 1 still closes the vote; nobody stops there, as every car turned it down.
     * A proposal: its place when no car said 반대, else [REJECTED].
     */
    fun winner(drop: Drop, carIds: List<String>): String? {
        val first = drop.candidates.firstOrNull() ?: return null
        if (isProposal(drop)) return if (carIds.any { saidNo(drop, it) }) REJECTED else first.id
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
