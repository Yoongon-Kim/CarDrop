package com.cardrop.ai

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.DriverIntent
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.haversineKm
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt

/*
 * Owner: lead. What ClaudeAiService tells Claude about the trip, and how it reads Claude's JSON
 * replies. Every reply is checked before the app uses it; a reply that doesn't fit throws, and
 * FallbackAiService then uses the fake answer. No SDK code here, so ClaudeRepliesTest covers it.
 */

/** The trip as text for [AiService.recommend]: the cars, and the [places] Claude may choose from. */
internal fun describeTrip(topic: DropTopic, context: TripContext, places: List<Place>): String = buildString {
    appendLine("Topic: ${topic.name}")
    appendLine("Crew destination: ${context.crew.destination.name}")
    appendLine("Cars (km to the destination, minutes to arrival):")
    for (car in context.crew.cars) {
        append("- ${car.name}")
        if (car.isMe) append(" (this car)")
        context.positions[car.id]?.let { append(": ${it.kmToDestination.roundToInt()} km, ${it.etaMinutes} min") }
        if (car.isMe) {
            context.myVehicle.fuelPercent?.let { append(", fuel $it%") }
            context.myVehicle.rangeKm?.let { append(", range ${it.roundToInt()} km") }
            context.myVehicle.drivingMinutes?.let { append(", driving for $it min") }
        }
        appendLine()
    }
    appendLine("Places to choose from (in route order, nearest first; straight-line km from each car):")
    for (place in places) {
        append("- id=${place.id} ${place.name}")
        if (place.detail.isNotBlank()) append(" (${place.detail})")
        val distances = context.crew.cars.mapNotNull { car ->
            context.positions[car.id]?.let { car.name to haversineKm(GeoPoint(it.lat, it.lng), GeoPoint(place.lat, place.lng)) }
        }
        if (distances.isNotEmpty()) append(": " + distances.joinToString(", ") { (name, km) -> "$name ${km.roundToInt()} km" })
        appendLine()
    }
}

/** `{"topic", "confidence", "message"}` -> what the driver wants. */
internal fun parseIntent(json: String, utterance: String): DriverIntent {
    val reply = JSONObject(json)
    val topic = DropTopic.valueOf(reply.getString("topic")) // an unknown topic throws
    val confidence = reply.optDouble("confidence", 0.5).toFloat().takeUnless { it.isNaN() }?.coerceIn(0f, 1f) ?: 0.5f
    val message = if (topic == DropTopic.MESSAGE) reply.optString("message").trim().ifEmpty { utterance } else null
    return DriverIntent(topic, confidence, message)
}

/** `{"message"}` -> the short text the other cars see. */
internal fun parsePolished(json: String): String =
    JSONObject(json).getString("message").trim().take(80).ifEmpty { throw IOException("Claude wrote an empty message") }

/**
 * `{"candidates": [{"placeId", "reason", "detourMinutes"}]}` -> up to 3 candidates, best first.
 * Only places from [places] count: Claude must not invent places, because the winner becomes a waypoint.
 */
internal fun parseCandidates(json: String, places: List<Place>): List<Candidate> {
    val byId = places.associateBy { it.id }
    val items = JSONObject(json).getJSONArray("candidates")
    val picked = LinkedHashMap<String, Candidate>()
    for (i in 0 until items.length()) {
        val item = items.getJSONObject(i)
        val place = byId[item.optString("placeId")] ?: continue
        if (place.id in picked) continue
        picked[place.id] = Candidate(
            id = place.id,
            place = place,
            reason = item.optString("reason").trim().take(40).ifEmpty { place.detail },
            detourMinutes = item.optInt("detourMinutes", 5).coerceIn(0, 60),
        )
        if (picked.size == 3) break
    }
    if (picked.isEmpty()) throw IOException("Claude picked no place from the list")
    return picked.values.toList()
}

/** `{"question", "answers", "correctAnswer"}` -> a quiz for board 15. */
internal fun parseQuiz(json: String, regionName: String): QuizQuestion {
    val reply = JSONObject(json)
    val question = reply.getString("question").trim()
    val list = reply.getJSONArray("answers")
    val answers = (0 until list.length()).map { list.getString(it).trim() }.filter { it.isNotEmpty() }.distinct().take(4)
    val correct = reply.getString("correctAnswer").trim()
    if (question.isEmpty() || answers.size < 2 || correct !in answers) throw IOException("Claude's quiz doesn't fit")
    return QuizQuestion(question = question, tiles = listOf(regionName, "?"), answers = answers, correctAnswer = correct)
}
