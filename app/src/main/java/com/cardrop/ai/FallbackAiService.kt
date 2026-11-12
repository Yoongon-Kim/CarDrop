package com.cardrop.ai

import android.util.Log
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.DriverIntent
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.QuizQuestion
import kotlinx.coroutines.CancellationException

/**
 * Owner: lead. Asks [primary] (Claude) first. When that fails (no network, a timeout, a refusal, a
 * reply that doesn't fit), [backup] (the fake) answers instead, so a demo never gets stuck.
 * Failures are logged under the "CarDropAi" tag: `adb logcat -s CarDropAi`.
 */
class FallbackAiService(private val primary: AiService, private val backup: AiService) : AiService {

    override suspend fun readIntent(utterance: String): DriverIntent =
        attempt("readIntent", { primary.readIntent(utterance) }) { backup.readIntent(utterance) }

    override suspend fun recommend(topic: DropTopic, context: TripContext): List<Candidate> =
        attempt("recommend", { primary.recommend(topic, context) }) { backup.recommend(topic, context) }

    override suspend fun polishMessage(raw: String): String =
        attempt("polishMessage", { primary.polishMessage(raw) }) { backup.polishMessage(raw) }

    override suspend fun localQuiz(regionName: String): QuizQuestion =
        attempt("localQuiz", { primary.localQuiz(regionName) }) { backup.localQuiz(regionName) }

    private suspend fun <T> attempt(name: String, real: suspend () -> T, fake: suspend () -> T): T =
        try {
            real()
        } catch (e: CancellationException) {
            throw e // the screen stopped waiting; don't answer at all
        } catch (e: Exception) {
            Log.w("CarDropAi", "$name: Claude failed, using the fake answer", e)
            fake()
        }
}
