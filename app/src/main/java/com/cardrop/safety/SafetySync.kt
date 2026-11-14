package com.cardrop.safety

import android.util.Log
import com.cardrop.core.model.Car
import com.cardrop.crew.FirebaseCrewRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow

/** Scores never live under the position/fuel branch, which the existing trip implementation clears. */
class SafetySync {
    val peers = MutableStateFlow<Map<String, SafetyPeer>>(emptyMap())
    val closed = MutableStateFlow(false)
    private var ref: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    fun follow(tripId: String, cars: List<Car>, host: Boolean) {
        listener?.let { ref?.removeEventListener(it) }
        peers.value = emptyMap(); closed.value = false
        val node = FirebaseDatabase.getInstance(FirebaseCrewRepository.DATABASE_URL).getReference("safetyTrips").child(tripId)
        ref = node
        if (host) node.child("meta").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (data.value == null) data.value = mapOf("rulesVersion" to SafetyRules.VERSION, "participantCarIds" to cars.map { it.id })
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) = Unit
        })
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val finalized = snapshot.child("meta/closedAt").exists()
                closed.value = finalized
                val branch = snapshot.child(if (finalized) "finalCars" else "cars")
                peers.value = branch.children.mapNotNull { s -> s.key?.let { id -> safetyPeerFromFirebase(id, s.value)?.let { id to it } } }.toMap()
            }
            override fun onCancelled(error: DatabaseError) { Log.w("CarDropSafety", "Cannot read crew scores: ${error.message}") }
        }.also { node.addValueEventListener(it) }
    }

    fun share(peer: SafetyPeer) {
        if (closed.value) return
        val payload = peer.toFirebase().toMutableMap().apply { put("updatedAt", ServerValue.TIMESTAMP) }
        ref?.child("cars")?.child(peer.carId)?.setValue(payload)
            ?.addOnFailureListener { Log.w("CarDropSafety", "Score remains saved locally; sync will retry", it) }
    }

    fun closeGroup(mine: SafetyPeer) {
        if (closed.value) return
        val frozen = (peers.value + (mine.carId to mine)).mapValues { (_, p) ->
            (if (p.status == SafetyTripStatus.RECORDING) p.copy(status = SafetyTripStatus.STOPPED) else p).toFirebase()
        }
        ref?.updateChildren(mapOf("meta/closedAt" to ServerValue.TIMESTAMP, "finalCars" to frozen))
    }

    fun stop() { listener?.let { ref?.removeEventListener(it) }; listener = null; ref = null; peers.value = emptyMap(); closed.value = false }
}

/** Other demo cars replay measured speed traces through SafetyDetector rather than hard-coded scores. */
class DemoSafetyFleet(cars: List<Car>, tripId: String, startedAt: Long, destination: String) {
    private val engines = cars.mapIndexed { i, car ->
        car.id to (i to SafetyDetector(SafetyRecord(tripId, car.id, car.name, destination, startedAt, startedAt, true), 0))
    }.toMap()
    private val progressed = mutableMapOf<String, Long>()
    private val start = startedAt

    fun finish(wallMillis: Long, completed: Boolean) { advance(wallMillis); engines.values.forEach { it.second.finish(wallMillis, completed) } }

    fun advance(wallMillis: Long): Map<String, SafetyPeer> = engines.mapValues { (id, indexed) ->
        val (index, detector) = indexed
        var at = progressed[id] ?: 0L
        val target = (wallMillis - start).coerceIn(0, 3_600_000)
        while (at + 100 <= target) {
            at += 100
            val t = (at / 1000.0) % 120
            val v = when (index % 3) {
                1 -> when { t in 12.0..14.0 -> 20 + (t - 12) * 3.4; t > 14 && t < 20.8 -> 26.8 - (t - 14); else -> 20.0 }
                2 -> when { t in 20.0..22.0 -> 20 - (t - 20) * 4; t > 22 && t < 28.6667 -> 12 + (t - 22) * 1.2; else -> 20.0 }
                else -> 20.0
            }
            val nanos = at * 1_000_000
            detector.ingest(SpeedReading(v, nanos, nanos), start + at)
        }
        progressed[id] = at
        detector.record.peer().copy(updatedAtMillis = wallMillis)
    }
}
