package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint

/**
 * Owner: lead. Finds places by name, for the suggestions under board 10's "다른 곳을 직접 입력" box
 * (like Kakao Map's list while you type). Real: [KakaoPlaceSearch]. Without a Kakao key the app
 * has no search and offers only the places it already knows (see `AiViewModel`).
 */
interface PlaceSearch {
    /**
     * Places that fit [query], best first. [near] is this car's position, so places nearby rank
     * higher (null before the first GPS fix). Throws when the search fails, e.g. with no internet.
     */
    suspend fun search(query: String, near: GeoPoint?): List<PlaceSuggestion>
}

/** One line of the suggestion list. The place has a location, so it can become a waypoint. */
data class PlaceSuggestion(
    val place: Place,
    /** e.g. "고속도로휴게소"; empty when unknown. */
    val category: String = "",
    /** The road address, or the old lot address when there is none. */
    val address: String = "",
    /** Straight-line distance from the car, or null when we don't know where the car is. */
    val distanceKm: Double? = null,
)
