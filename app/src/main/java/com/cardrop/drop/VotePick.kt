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

    /**
     * What the AI card says about this pick: a bold part and a grey part (target lines 466 and 724), e.g.
     * "덕평자연휴게소에 1표" and "“1번”, “첫 번째”도 같은 표예요".
     */
    fun explain(candidates: List<Candidate>): Pair<String, String> = when (this) {
        is Place -> {
            val index = candidates.indexOfFirst { it.id == candidateId }
            val name = candidates.getOrNull(index)?.place?.name ?: "이곳"
            "${name}에 1표" to "“${index + 1}번”, “${ordinal(index + 1)}”도 같은 표예요"
        }
        Anywhere -> "상관없음 → 1순위 ${candidates.firstOrNull()?.place?.name ?: "1번"}에 1표" to
            "“아무 데나”, “다 좋아”, “알아서 해” 모두 같은 뜻이에요"
        Pass -> "이번엔 안 들러요" to "우리 차는 경로를 유지하고 결과만 알려드려요"
    }

    /**
     * What the voice says once 응답하기 sent this pick: board 11 then goes back to 홈 right away, so this tells the
     * driver which answer went out, e.g. "강문 덴켄에 투표했어요".
     */
    fun answeredLine(candidates: List<Candidate>): String = when (this) {
        is Place -> "${candidates.firstOrNull { it.id == candidateId }?.place?.name ?: "고른 곳"}에 투표했어요"
        Anywhere -> "아무 데나 좋다고 답했어요"
        Pass -> "이번엔 패스했어요. 우리 차는 가던 길로 가요"
    }

    companion object {
        /** What the screen starts with: this car's vote if it sent one, otherwise place 1 (as the target does). */
        fun from(vote: String?, candidates: List<Candidate>): VotePick = when {
            vote == VoteRules.PASS -> Pass
            vote != null -> Place(vote)
            else -> candidates.firstOrNull()?.let { Place(it.id) } ?: Anywhere
        }
    }
}

/** "첫 번째", "두 번째", …: how a driver says a place's number in words. */
private fun ordinal(number: Int): String = when (number) {
    1 -> "첫 번째"
    2 -> "두 번째"
    3 -> "세 번째"
    4 -> "네 번째"
    5 -> "다섯 번째"
    else -> "${number}번째"
}
