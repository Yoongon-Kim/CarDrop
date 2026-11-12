package com.cardrop.ai

import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Place

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

    /** Ticks [place]. If it is not in the list yet, it goes at the end first. */
    fun add(place: Place): PlacePick {
        val existing = candidates.firstOrNull { it.place.id == place.id }
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
 * The place the driver means by [name], or null when none of [places] fits.
 *
 * A place needs a location before it can become a waypoint, and there is no place search yet,
 * so drivers can only add places the app already knows. The exact name wins; otherwise a name
 * that contains what was typed or said: "덕평", "덕평 휴게소" and "덕평휴게소." all find 덕평자연휴게소.
 */
fun findKnownPlace(name: String, places: List<Place>): Place? {
    val query = name.forMatching()
    if (query.isEmpty()) return null
    places.firstOrNull { it.name.forMatching() == query }?.let { return it }
    val core = query.removeSuffix("휴게소")
    if (core.length < 2) return null
    return places.firstOrNull { core in it.name.forMatching() }
}

/** Letters and digits only, lower case: spaces and punctuation from speech don't matter. */
private fun String.forMatching(): String = filter { it.isLetterOrDigit() }.lowercase()
