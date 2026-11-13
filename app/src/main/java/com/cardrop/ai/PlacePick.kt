package com.cardrop.ai

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Place
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.haversineKm

/**
 * Owner: lead. Board 10's list: the AI's picks (best first), then the places the driver added,
 * and which ones are ticked. Only ticked places go into the vote, in this order, so the AI's best
 * pick stays candidate 1 (the one that counts for cars that don't answer, see drop/VoteRules.kt).
 */
data class PlacePick(
    val candidates: List<Candidate> = emptyList(),
    /** Ids of the places the driver added. The others came from the AI. */
    val addedIds: Set<String> = emptySet(),
    val selectedIds: Set<String> = emptySet(),
) {
    /** The ticked places, in list order. */
    val selected: List<Candidate> get() = candidates.filter { it.id in selectedIds }

    /** How many places the AI picked. */
    val aiCount: Int get() = candidates.count { it.id !in addedIds }

    fun isSelected(id: String): Boolean = id in selectedIds

    fun isAdded(id: String): Boolean = id in addedIds

    fun toggle(id: String): PlacePick =
        copy(selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id)

    /**
     * The row already in the list for [place]: the same place, or the same stop found by the place
     * search under a longer name, e.g. Kakao's "덕평자연휴게소 양방향" for the AI's 덕평자연휴게소.
     */
    fun find(place: Place): Candidate? =
        candidates.firstOrNull { it.place.id == place.id } ?: candidates.firstOrNull { it.place.isSameSpot(place) }

    /** Ticks [place]. If it is not in the list yet, it goes at the end first. */
    fun add(place: Place): PlacePick {
        val existing = find(place)
        if (existing != null) return copy(selectedIds = selectedIds + existing.id)
        val added = Candidate(id = place.id, place = place, reason = ADDED_REASON, detourMinutes = 0)
        return copy(
            candidates = candidates + added,
            addedIds = addedIds + added.id,
            selectedIds = selectedIds + added.id,
        )
    }

    companion object {
        /** The line under a place the driver added (design: "직접 추가한 장소"). */
        const val ADDED_REASON = "직접 추가한 장소"

        /** The AI's picks, all ticked: the design starts with every AI place ticked. */
        fun fromAi(candidates: List<Candidate>): PlacePick =
            PlacePick(candidates = candidates, selectedIds = candidates.map { it.id }.toSet())
    }
}

/**
 * The place the driver means by [name], or null when none of [places] fits. The exact name wins;
 * otherwise a name that contains what was typed or said: "덕평", "덕평 휴게소" and "덕평휴게소." all
 * find 덕평자연휴게소.
 */
fun findKnownPlace(name: String, places: List<Place>): Place? = knownPlaceMatches(name, places).firstOrNull()

/**
 * All of [places] that fit [name], best first: exact names, then names that contain it. Used when
 * there is no place search (no Kakao key, or no internet): a place needs a location before it can
 * become a waypoint, so then drivers can add only places the app already knows.
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

/**
 * True when [other] is this place under another name: within 300 m, and one name contains the
 * other ("덕평자연휴게소" and "덕평자연휴게소 양방향"). A different shop next door has another name,
 * so "스타벅스 덕평휴게소점" stays its own place.
 */
private fun Place.isSameSpot(other: Place): Boolean {
    val a = name.forMatching()
    val b = other.name.forMatching()
    if (a.length < 2 || b.length < 2 || (a !in b && b !in a)) return false
    return haversineKm(GeoPoint(lat, lng), GeoPoint(other.lat, other.lng)) < SAME_SPOT_KM
}

private const val SAME_SPOT_KM = 0.3

/** Letters and digits only, lower case: spaces and punctuation from speech don't matter. */
private fun String.forMatching(): String = filter { it.isLetterOrDigit() }.lowercase()
