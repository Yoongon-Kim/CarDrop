package com.cardrop.ai

/** Kept separate from AiService so record greetings never change recommendations, quizzes or messages. */
fun interface SafetyEncouragement {
    suspend fun create(context: EncouragementContext): String
}

data class EncouragementContext(
    val score: Int?, val measuredMinutes: Int, val accelerationCount: Int,
    val brakingCount: Int, val sufficientData: Boolean,
) {
    fun fallback(): String = when {
        !sufficientData -> "차분한 운전을 이어가도록 오늘도 응원할게요!"
        accelerationCount + brakingCount == 0 -> "지금처럼 차분한 운전을 이어가 봐요!"
        else -> "여유를 가지고 편안하게 달려봐요, 응원할게요!"
    }
}

/** AI writes the phrasing, not new scores, rankings or telemetry facts. */
fun validatedEncouragement(candidate: String?, context: EncouragementContext): String {
    val text = candidate?.trim().orEmpty()
    val unsafe = listOf("추월", "따라잡", "분발", "빨리", "밟아", "속도를 높", "가속해", "1위", "회복", "점수를 올", "완벽", "절대 안전")
    if (text.isEmpty() || text.length > 40 || text.any { it.isDigit() } || text.contains('\n') || unsafe.any { it in text }) return context.fallback()
    // An unverified no-event assertion is worse than using the short default encouragement.
    if (("없이" in text || "없었" in text || "없어" in text) &&
        (!context.sufficientData || context.accelerationCount + context.brakingCount > 0)) return context.fallback()
    return text
}
