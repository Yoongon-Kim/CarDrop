package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Owner: lead.
 *
 * Kakao Local keyword search: any place in Korea by name (rest stops, cafés, restaurants, addresses
 * of shops...), with its location. Same key as the routes (`cardrop.kakaoRestKey`, the Kakao app's
 * REST API key; the app's 카카오맵 service must be on). Given the car's position, Kakao puts nearby
 * places first: "스타벅스" near 이천 lists the 이천 branches.
 * Docs: https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword
 */
class KakaoPlaceSearch(private val restApiKey: String) : PlaceSearch {

    override suspend fun search(query: String, near: GeoPoint?): List<PlaceSuggestion> {
        if (restApiKey.isBlank()) throw IOException("no Kakao REST API key (cardrop.kakaoRestKey)")
        val url = buildString {
            append("https://dapi.kakao.com/v2/local/search/keyword.json?size=").append(RESULTS)
            // URLEncoder writes a space as "+"; %20 is the safe form in a query string.
            append("&query=").append(URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
            near?.let { append(String.format(Locale.US, "&x=%.6f&y=%.6f", it.lng, it.lat)) }
        }
        return parseKakaoPlaces(httpGet(url, mapOf("Authorization" to "KakaoAK $restApiKey")))
    }

    /**
     * Places named [query] within [radiusMetres] of [near] (Kakao allows up to 20 km), nearest first: board 05's
     * "주유소" around the car when no rest stop on the route will do.
     */
    suspend fun nearby(query: String, near: GeoPoint, radiusMetres: Int = 20_000): List<PlaceSuggestion> {
        if (restApiKey.isBlank()) throw IOException("no Kakao REST API key (cardrop.kakaoRestKey)")
        val url = buildString {
            append("https://dapi.kakao.com/v2/local/search/keyword.json?size=").append(RESULTS)
            append("&query=").append(URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
            append(String.format(Locale.US, "&x=%.6f&y=%.6f", near.lng, near.lat))
            append("&radius=").append(radiusMetres.coerceIn(0, 20_000)).append("&sort=distance")
        }
        return parseKakaoPlaces(httpGet(url, mapOf("Authorization" to "KakaoAK $restApiKey")))
    }

    private companion object {
        /** Kakao allows 1-15. Ten fill the list without scrolling far. */
        const val RESULTS = 10
    }
}

/** Turns Kakao's keyword-search JSON into suggestions. Places without a name or location are left out. Public for unit tests. */
fun parseKakaoPlaces(json: String): List<PlaceSuggestion> {
    val documents = JSONObject(json).getJSONArray("documents")
    return (0 until documents.length()).mapNotNull { i ->
        val d = documents.getJSONObject(i)
        val name = d.optString("place_name").trim()
        // Kakao sends the coordinates as text: x = longitude, y = latitude.
        val lat = d.optString("y").toDoubleOrNull()
        val lng = d.optString("x").toDoubleOrNull()
        if (name.isEmpty() || lat == null || lng == null) return@mapNotNull null
        val address = d.optString("road_address_name").trim().ifEmpty { d.optString("address_name").trim() }
        PlaceSuggestion(
            // "kakao-" keeps Kakao's ids apart from the app's own place ids ("deokpyeong", ...).
            place = Place(id = "kakao-${d.optString("id")}", name = name, lat = lat, lng = lng, detail = address),
            // "교통,수송 > 휴게소 > 고속도로휴게소" -> "고속도로휴게소"
            category = d.optString("category_name").substringAfterLast('>').trim(),
            address = address,
            // Only there when we sent the car's position; in metres, as text.
            distanceKm = d.optString("distance").toDoubleOrNull()?.div(1000.0),
        )
    }
}
