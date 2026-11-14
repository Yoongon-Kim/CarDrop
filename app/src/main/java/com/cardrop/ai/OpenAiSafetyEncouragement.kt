package com.cardrop.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8

/** Optional, bounded GPT request dedicated to the record-entry greeting. No shared AiService state. */
class OpenAiSafetyEncouragement(private val apiKey: String) : SafetyEncouragement {
    override suspend fun create(context: EncouragementContext): String {
        if (apiKey.isBlank()) return context.fallback()
        return try {
            val text = withContext(Dispatchers.IO) {
                val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 1000; readTimeout = 1500; doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                }
                try {
                    connection.outputStream.use { it.write(safetyEncouragementRequest(context).toString().toByteArray(UTF_8)) }
                    val status = connection.responseCode
                    if (status !in 200..299) throw IOException("Encouragement request returned HTTP $status")
                    safetyEncouragementText(connection.inputStream.bufferedReader(UTF_8).use { it.readText() })
                } finally { connection.disconnect() }
            }
            validatedEncouragement(text, context)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("CarDropSafetyAi", "Using default encouragement (${e.javaClass.simpleName})")
            context.fallback()
        }
    }
}

internal fun safetyEncouragementRequest(context: EncouragementContext): JSONObject = JSONObject()
    .put("model", "gpt-6-sol")
    .put("reasoning", JSONObject().put("effort", "low"))
    .put("instructions", "Write one warm Korean encouragement for a driver opening their driving record. Maximum 40 characters, one sentence. Do not use digits, rankings, competition, acceleration instructions, promises of safety, or score recovery. Use only supplied facts; if data is insufficient give a general encouragement without asserting driving behavior. Return message only in the required JSON object.")
    .put("input", "Observed minutes=${context.measuredMinutes}; acceleration events=${context.accelerationCount}; braking events=${context.brakingCount}; sufficientData=${context.sufficientData}")
    .put("text", JSONObject().put("format", JSONObject().put("type", "json_schema")
        .put("name", "driving_encouragement").put("strict", true).put("schema", JSONObject()
            .put("type", "object").put("properties", JSONObject().put("message", JSONObject().put("type", "string")))
            .put("required", JSONArray().put("message")).put("additionalProperties", false))))

internal fun safetyEncouragementText(response: String): String {
    val outputs = JSONObject(response).optJSONArray("output") ?: throw IOException("Encouragement returned no output")
    for (i in 0 until outputs.length()) {
        val content = outputs.optJSONObject(i)?.optJSONArray("content") ?: continue
        for (j in 0 until content.length()) {
            val block = content.optJSONObject(j) ?: continue
            if (block.optString("type") == "refusal") throw IOException("Encouragement request refused")
            if (block.optString("type") == "output_text") return JSONObject(block.getString("text")).getString("message")
        }
    }
    throw IOException("Encouragement returned no text")
}
