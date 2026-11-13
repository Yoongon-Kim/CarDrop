package com.cardrop.navi

import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint

/**
 * Owner: lead. Finds places by name, for the suggestions while a driver types a place: board 02's
 * destination and board 10's "다른 곳을 직접 입력" (like Kakao Map's list). Real: [KakaoPlaceSearch].
 * Without a Kakao key the app has no search and offers only the places it already knows
 * ([PlaceSuggester]).
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

/**
 * All of [places] that fit [name], best first: exact names, then names that contain it. Used when
 * there is no place search (no Kakao key, or no internet): a place needs a location before it can
 * become a waypoint or destination, so then drivers can pick only places the app already knows.
 */
fun knownPlaceMatches(name: String, places: List<Place>): List<Place> {
    val query = name.forMatching()
    if (query.isEmpty()) return emptyList()
    val exact = places.filter { it.name.forMatching() == query }
    val core = query.removeSuffix("휴게소")
    if (core.length < 2) return exact
    return (exact + places.filter { core in it.name.forMatching() }).distinctBy { it.id }
}

/**
 * What to search for while the driver is typing [typed]. A Korean keyboard sends half-built
 * letters ("덕ㅍ" on the way to "덕평"), which find nothing, so a lone consonant or vowel at the end
 * is dropped: "덕평ㅈ" searches "덕평" again instead of emptying the list.
 */
fun placeQuery(typed: String): String =
    typed.trim().trimEnd { it in '\u3131'..'\u318E' || it in '\u1100'..'\u11FF' }.trim()

/** Letters and digits only, lower case: spaces and punctuation from speech don't matter. */
internal fun String.forMatching(): String = filter { it.isLetterOrDigit() }.lowercase()
