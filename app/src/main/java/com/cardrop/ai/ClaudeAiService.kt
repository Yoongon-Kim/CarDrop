package com.cardrop.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.ThinkingConfigDisabled
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.DriverIntent
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration

/**
 * Owner: lead. The real [AiService]: asks Claude through Anthropic's Java SDK. Every request fixes
 * the shape of the JSON reply (structured outputs), and ClaudeReplies.kt checks it before use.
 *
 * - Claude Haiku 5.5 for [readIntent] and [polishMessage]: short answers with thinking off, so they are fast.
 * - Claude Sonnet 5.5 for [recommend] and [localQuiz], at low effort. If Sonnet declines a request
 *   (a "refusal"), the API answers with another model by itself (server-side fallbacks).
 *
 * Any problem (no network, a refusal, a reply that doesn't fit) throws. [FallbackAiService] then
 * uses the fake answer, so the demo keeps going.
 */
class ClaudeAiService(
    private val apiKey: String,
    /** Same filter as the fake: only stops every car still has ahead (AppContainer.stopsAhead). */
    private val stopsAhead: (List<Place>) -> List<Place> = { it },
) : AiService {

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            // A driver is waiting: one try of at most 10 s (real answers took 2.5-3.6 s), then the
            // fake answers. With retries, a dropped connection made board 10 wait 30 s.
            .timeout(Duration.ofSeconds(10))
            .maxRetries(0)
            .build()
    }

    override suspend fun readIntent(utterance: String): DriverIntent =
        parseIntent(askFast(INTENT_PROMPT, utterance, intentSchema), utterance)

    override suspend fun recommend(topic: DropTopic, context: TripContext): List<Candidate> {
        val places = if (topic == DropTopic.DESTINATION) {
            context.pool
        } else {
            stopsAhead(context.pool).ifEmpty { context.pool.takeLast(3) }
        }
        if (places.isEmpty()) throw IOException("no places to recommend")
        return parseCandidates(askSmart(RECOMMEND_PROMPT, describeTrip(topic, context, places), recommendSchema), places)
    }

    override suspend fun polishMessage(raw: String): String = parsePolished(askFast(POLISH_PROMPT, raw, polishSchema))

    override suspend fun localQuiz(regionName: String): QuizQuestion =
        parseQuiz(askSmart(QUIZ_PROMPT, "지역: $regionName", quizSchema), regionName)

    /** Haiku with thinking off and low effort, for short answers that must come back fast. */
    private suspend fun askFast(system: String, user: String, schema: JsonOutputFormat): String = send(
        MessageCreateParams.builder()
            .model(FAST_MODEL)
            .maxTokens(300L)
            .thinking(ThinkingConfigDisabled.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).format(schema).build())
            .system(system)
            .addUserMessage(user)
            .build(),
    )

    /** Sonnet at low effort, with server-side fallbacks in case it declines. */
    private suspend fun askSmart(system: String, user: String, schema: JsonOutputFormat): String = send(
        MessageCreateParams.builder()
            .model(SMART_MODEL)
            .maxTokens(16_000L)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).format(schema).build())
            .system(system)
            .addUserMessage(user)
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build(),
    )

    /** Sends on a background thread and returns the reply's text (the JSON). */
    private suspend fun send(params: MessageCreateParams): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val message = client.messages().create(params)
        Log.d(TAG, "${message.model()} answered in ${System.currentTimeMillis() - started} ms")
        when (message.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw IOException("Claude declined the request")
            StopReason.MAX_TOKENS -> throw IOException("Claude's reply was cut off")
            else -> Unit
        }
        message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
            .ifBlank { throw IOException("Claude sent no text") }
    }

    private val intentSchema = jsonSchema(
        "topic" to mapOf("type" to "string", "enum" to DropTopic.entries.map { it.name }),
        "confidence" to mapOf("type" to "number"),
        "message" to STRING,
    )
    private val polishSchema = jsonSchema("message" to STRING)
    private val recommendSchema = jsonSchema(
        "candidates" to mapOf(
            "type" to "array",
            "items" to mapOf(
                "type" to "object",
                "properties" to mapOf("placeId" to STRING, "reason" to STRING, "detourMinutes" to mapOf("type" to "integer")),
                "required" to listOf("placeId", "reason", "detourMinutes"),
                "additionalProperties" to false,
            ),
        ),
    )
    private val quizSchema = jsonSchema(
        "question" to STRING,
        "answers" to mapOf("type" to "array", "items" to STRING),
        "correctAnswer" to STRING,
    )

    /** A JSON schema for an object whose [properties] are all required and nothing else is allowed. */
    private fun jsonSchema(vararg properties: Pair<String, Any>): JsonOutputFormat =
        JsonOutputFormat.builder()
            .schema(
                JsonOutputFormat.Schema.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(properties.toMap()))
                    .putAdditionalProperty("required", JsonValue.from(properties.map { it.first }))
                    .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                    .build(),
            )
            .build()

    private companion object {
        const val TAG = "CarDropAi"
        const val FAST_MODEL = "claude-haiku-5-5"
        const val SMART_MODEL = "claude-sonnet-5-5"
        val STRING = mapOf("type" to "string")

        val INTENT_PROMPT = """
            You read one sentence a driver said in the car, in Korean. CarDrop keeps a group of cars
            that drive to the same place in touch. Decide what the driver wants:
            - REST: a break, a toilet, a rest stop, or they feel sleepy or tired.
            - FOOD: they are hungry or want to eat.
            - FUEL: low on fuel or charge, or they want a gas station or charger.
            - DESTINATION: they want the whole group to go somewhere else.
            - MESSAGE: anything else; it is a message for the other cars.
            confidence: from 0 to 1, how sure you are.
            message: for MESSAGE, what the driver wants to tell the other cars, in their words;
            otherwise an empty string.
        """.trimIndent()

        val POLISH_PROMPT = """
            A driver told CarDrop what to tell the other cars in their group. Write the message the
            other cars will see on screen, in Korean: short (at most 30 characters), friendly, polite
            해요체. Keep the meaning and leave out the instruction itself (such as "~에 전해줘" or
            "~라고 말해줘"). Example: "삼촌 차에 곧 도착한다고 전해줘" becomes "곧 도착해요!".
        """.trimIndent()

        val RECOMMEND_PROMPT = """
            CarDrop helps a group of cars that drive to the same place decide where to stop together.
            From the places you are given, choose the 2 or 3 best for the whole group, best first.
            - Use only ids from the list.
            - Every car must get there comfortably: mind the car with the least fuel and the driver
              who has been driving longest.
            - Prefer places that fit the topic: a good rest stop for REST, good food for FOOD, a gas
              station for FUEL.
            For each place give placeId, reason and detourMinutes. reason is Korean, at most 25
            characters, and says why the place suits the group, for example "4대 모두 15분 안 · 주유 가능".
            detourMinutes is how many minutes the stop adds to the trip, not counting the stop
            itself (usually 2 to 10).
        """.trimIndent()

        val QUIZ_PROMPT = """
            Write one multiple-choice quiz question in Korean about the region a family is driving
            through, to play while the car is parked. The question has at most 40 characters. Give 4
            answers of at most 8 characters each; exactly one is correct, and correctAnswer is that
            answer written exactly the same way.
        """.trimIndent()
    }
}
