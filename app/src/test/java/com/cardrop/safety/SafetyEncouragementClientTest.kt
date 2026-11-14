package com.cardrop.safety

import com.cardrop.ai.EncouragementContext
import com.cardrop.ai.OpenAiSafetyEncouragement
import com.cardrop.ai.safetyEncouragementRequest
import com.cardrop.ai.safetyEncouragementText
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SafetyEncouragementClientTest {
    private val context = EncouragementContext(95, 3, 1, 1, true)

    @Test fun noOpenAiKeyUsesLocalGreetingWithoutAnyNetworkRequest() = runBlocking {
        assertEquals(context.fallback(), OpenAiSafetyEncouragement("").create(context))
    }

    @Test fun requestContainsOnlyTheRecordGreetingAndMotionFacts() {
        val request = safetyEncouragementRequest(context)
        val input = request.getString("input")
        assertTrue(input.contains("acceleration events=1"))
        assertTrue(input.contains("braking events=1"))
        assertFalse(input.contains("speeding"))
        assertFalse(request.toString().contains("apiKey"))
        assertEquals("driving_encouragement", request.getJSONObject("text").getJSONObject("format").getString("name"))
        assertEquals(1, request.getJSONObject("text").getJSONObject("format").getJSONObject("schema").getJSONArray("required").length())
    }

    @Test fun responseSkipsReasoningAndReadsTheStructuredMessage() {
        val message = "서두르지 않는 운전을 응원해요!"
        val response = JSONObject().put("output", JSONArray()
            .put(JSONObject().put("type", "reasoning"))
            .put(JSONObject().put("content", JSONArray().put(JSONObject()
                .put("type", "output_text").put("text", JSONObject().put("message", message).toString())))))
        assertEquals(message, safetyEncouragementText(response.toString()))
    }

    @Test fun missingOutputAndRefusalAreRejected() {
        assertThrows(IOException::class.java) { safetyEncouragementText("{}") }
        assertThrows(IOException::class.java) { safetyEncouragementText("""{"output":[{"content":[{"type":"refusal"}]}]}""") }
    }
}
