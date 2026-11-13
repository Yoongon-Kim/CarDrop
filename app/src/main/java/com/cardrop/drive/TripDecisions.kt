package com.cardrop.drive

import com.cardrop.core.model.Drop

/** Local choices last for the current crew session, including screen/ViewModel recreation. */
class TripDecisions {
    private var crewKey: String? = null
    private val handled = mutableSetOf<String>()
    private val skipped = mutableSetOf<String>()
    private var latestCreatedAt = Long.MIN_VALUE
    private var latestId = ""

    fun forCrew(key: String?) {
        if (crewKey == key) return
        crewKey = key
        handled.clear()
        skipped.clear()
        latestCreatedAt = Long.MIN_VALUE
        latestId = ""
    }
    fun skip(dropId: String) { skipped += dropId }
    fun isSkipped(dropId: String) = dropId in skipped
    fun wasHandled(dropId: String) = dropId in handled
    fun canApply(drop: Drop) = drop.id !in skipped && (drop.createdAtMillis > latestCreatedAt ||
        (drop.createdAtMillis == latestCreatedAt && drop.id >= latestId))
    fun markHandled(drop: Drop, applied: Boolean = false) {
        handled += drop.id
        if (applied && canApply(drop)) {
            latestCreatedAt = drop.createdAtMillis
            latestId = drop.id
        }
    }
}
