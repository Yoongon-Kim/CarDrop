package com.cardrop.ai

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Crew
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** How ClaudeAiService reads Claude's JSON, and what it tells Claude about the trip. No network. */
class ClaudeRepliesTest {

    private val places = DemoData.restStops.take(3) // deokpyeong, yeoju, munmak

    @Test
    fun readsATopic() {
        val intent = parseIntent("""{"topic":"FOOD","confidence":0.9,"message":""}""", "배고파")
        assertEquals(DropTopic.FOOD, intent.topic)
        assertEquals(0.9f, intent.confidence, 0.001f)
        assertNull(intent.message)
    }

    @Test
    fun aMessageKeepsTheDriversWords() {
        val intent = parseIntent("""{"topic":"MESSAGE","confidence":0.7,"message":"곧 도착해요"}""", "곧 도착한다고 전해줘")
        assertEquals("곧 도착해요", intent.message)
        val empty = parseIntent("""{"topic":"MESSAGE","confidence":0.7,"message":" "}""", "곧 도착한다고 전해줘")
        assertEquals("곧 도착한다고 전해줘", empty.message)
    }

    @Test
    fun anUnknownTopicThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            parseIntent("""{"topic":"PARTY","confidence":1,"message":""}""", "x")
        }
    }

    @Test
    fun keepsOnlyListedPlacesInClaudesOrder() {
        val json = """{"candidates":[
            {"placeId":"munmak","reason":"가장 한산해요","detourMinutes":4},
            {"placeId":"starbucks","reason":"invented","detourMinutes":1},
            {"placeId":"munmak","reason":"again","detourMinutes":4},
            {"placeId":"deokpyeong","reason":" ","detourMinutes":500}
        ]}"""
        val picks = parseCandidates(json, places)
        assertEquals(listOf("munmak", "deokpyeong"), picks.map { it.id })
        assertEquals("가장 한산해요", picks[0].reason)
        assertEquals(places[0].detail, picks[1].reason) // an empty reason falls back to the place's detail
        assertEquals(60, picks[1].detourMinutes) // capped
    }

    @Test
    fun atMostThreePlaces() {
        val all = DemoData.restStops
        val json = all.joinToString(",", """{"candidates":[""", "]}") { """{"placeId":"${it.id}","reason":"r","detourMinutes":3}""" }
        assertEquals(3, parseCandidates(json, all).size)
    }

    @Test
    fun noUsablePlaceThrows() {
        assertThrows(Exception::class.java) {
            parseCandidates("""{"candidates":[{"placeId":"nowhere","reason":"r","detourMinutes":1}]}""", places)
        }
    }

    @Test
    fun readsAPolishedMessage() {
        assertEquals("곧 도착해요!", parsePolished("""{"message":"  곧 도착해요!  "}"""))
        assertThrows(Exception::class.java) { parsePolished("""{"message":""}""") }
    }

    @Test
    fun aQuizNeedsItsAnswerAmongTheChoices() {
        val quiz = parseQuiz("""{"question":"이천은 무엇으로 유명할까요?","answers":["쌀","귤","인삼","굴비"],"correctAnswer":"쌀"}""", "이천")
        assertEquals(listOf("이천", "?"), quiz.tiles)
        assertEquals("쌀", quiz.correctAnswer)
        assertThrows(Exception::class.java) {
            parseQuiz("""{"question":"q","answers":["a","b"],"correctAnswer":"c"}""", "이천")
        }
    }

    @Test
    fun theTripTextNamesEveryCarAndPlace() {
        val crew = Crew("482917", "강릉 여행", DemoData.gyeongpo, DemoData.me.id, listOf(DemoData.me, DemoData.uncle))
        val positions = mapOf(DemoData.me.id to CarPosition(DemoData.me.id, 37.30, 127.20, 180f, 120))
        val text = describeTrip(
            DropTopic.REST,
            TripContext(crew, positions, VehicleSnapshot(fuelPercent = 12, rangeKm = 85f, drivingMinutes = 130)),
            places,
        )
        assertTrue(text.contains("Topic: REST"))
        assertTrue(text.contains("아빠 차 (나) (this car): 180 km, 120 min, fuel 12%, range 85 km, driving for 130 min"))
        assertTrue(text.contains("- 삼촌 차\n")) // no position yet: the name only
        places.forEach { assertTrue(text.contains("id=${it.id} ${it.name}")) }
        assertTrue(text.contains("아빠 차 (나) ")) // straight-line km from this car to each place
    }
}
