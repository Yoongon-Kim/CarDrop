package com.cardrop.drive

import android.util.Log
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Crew
import com.cardrop.crew.CrewRepository
import com.cardrop.crew.FirebaseCrewRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owner: lead. With the real crew (`cardrop.useRealCrew=true`): where each car is while driving, next to the
 * crew and its drops (Member A's FirebaseCrewRepository), so every device draws the other cars where they
 * really are. When the trip started is the crew's (`Crew.startedAtMillis`, `crews/<code>/info/startedAt`).
 *
 * Database, apart from `crews/<code>` so that a position every second doesn't wake the crew listener:
 * ```
 * trips/482917/positions/car-uncle    {lat, lng, km (to the destination), min (to the destination)}
 * ```
 * The host clears it when it creates the crew. Logs: `adb logcat -s CarDropTrip`.
 */
class FirebaseTrip(
    private val scope: CoroutineScope,
    private val crew: CrewRepository,
    databaseUrl: String = FirebaseCrewRepository.DATABASE_URL,
) {

    private val db = FirebaseDatabase.getInstance(databaseUrl)

    /** Every car's position as shared, this car's too. */
    private val shared = MutableStateFlow<Map<String, CarPosition>>(emptyMap())

    /** The other cars, where their own devices say they are (only cars whose device is connected). */
    val positions: StateFlow<Map<String, CarPosition>> =
        combine(shared, crew.crew) { all, current -> sharedCarsToShow(all, current, crew.myCarId) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The trip branch we follow, e.g. trips/482917. Null outside a crew. */
    private var tripRef: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    init {
        scope.launch {
            var previousKey: String? = null
            crew.crew.collect { current ->
                val key = current?.let { "${it.code}:${it.hostCarId}" }
                if (key != previousKey) {
                    previousKey = key
                    follow(current)
                }
            }
        }
    }

    /** Shares this car's position from [positions] while in a crew, at most once a second. */
    fun shareMyPosition(positions: StateFlow<Map<String, CarPosition>>) {
        scope.launch {
            positions.mapNotNull { it[crew.myCarId] }.distinctUntilChanged().conflate().collect { p ->
                tripRef?.child("positions")?.child(p.carId)?.setValue(
                    mapOf("lat" to p.lat, "lng" to p.lng, "km" to p.kmToDestination, "min" to p.etaMinutes),
                )
                delay(SHARE_EVERY_MS)
            }
        }
    }

    private fun follow(current: Crew?) {
        listener?.let { l -> tripRef?.removeEventListener(l) }
        listener = null
        tripRef = null
        shared.value = emptyMap()
        if (current == null) return

        val ref = db.getReference("trips").child(current.code)
        // This car just created the crew: a new trip, without an earlier trip's positions.
        if (current.hostCarId == crew.myCarId) {
            ref.removeValue().addOnFailureListener { Log.w(TAG, "Could not clear the old trip: ${it.message}") }
        }
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                shared.value = snapshot.child("positions").children.mapNotNull(::parsePosition).associateBy { it.carId }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase refused to read the trip: ${error.message}")
            }
        }
        ref.addValueEventListener(l)
        tripRef = ref
        listener = l
    }

    private fun parsePosition(s: DataSnapshot): CarPosition? {
        val id = s.key ?: return null
        return CarPosition(
            carId = id,
            lat = (s.child("lat").value as? Number)?.toDouble() ?: return null,
            lng = (s.child("lng").value as? Number)?.toDouble() ?: return null,
            kmToDestination = (s.child("km").value as? Number)?.toFloat() ?: 0f,
            etaMinutes = (s.child("min").value as? Number)?.toInt() ?: 0,
        )
    }

    private companion object {
        const val TAG = "CarDropTrip"
        const val SHARE_EVERY_MS = 1_000L
    }
}

/**
 * The shared positions worth drawing: the other cars of [crew] whose device is connected. A closed app
 * leaves its last position in the database, and its car is marked disconnected.
 */
internal fun sharedCarsToShow(shared: Map<String, CarPosition>, crew: Crew?, myCarId: String): Map<String, CarPosition> {
    val connected = crew?.cars.orEmpty().filter { it.connected && it.id != myCarId }.map { it.id }.toSet()
    return shared.filterKeys { it in connected }
}
