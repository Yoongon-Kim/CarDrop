package com.cardrop.crew

import android.util.Log
import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Car
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.drop.VoteRules
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owner: Member A.
 *
 * The real crew: every car reads and writes the same Firebase Realtime Database, so a drop sent on
 * one car shows up on the others within a second. Same behaviour as [FakeCrewRepository], minus the bots.
 *
 * Which car this device is comes from local.properties `cardrop.myCar` (dad / uncle / minji / aunt),
 * read once when the app starts, because other parts of the app keep `myCarId` from the start.
 *
 * Data in the database (one crew = one branch under its 6-digit code):
 * ```
 * crews/482917/info    name, hostCarId, destination {id, name, lat, lng, detail}
 * crews/482917/cars    car-dad: {connected: true}, car-uncle: {...}, ...
 * crews/482917/drops   <dropId>: {fromCarId, kind, topic, title, ..., votes: {carId: candidateId}}
 * ```
 * Logs: `adb logcat -s CarDropCrew`.
 */
class FirebaseCrewRepository(
    private val scope: CoroutineScope,
    myCar: Car,
    databaseUrl: String = DATABASE_URL,
) : CrewRepository {

    override val myCarId: String = myCar.id

    private val _crew = MutableStateFlow<Crew?>(null)
    override val crew: StateFlow<Crew?> = _crew

    private val _drops = MutableStateFlow<List<Drop>>(emptyList())
    override val drops: StateFlow<List<Drop>> = _drops

    private val db = FirebaseDatabase.getInstance(databaseUrl)

    /** The crew branch we listen to, e.g. crews/482917. Null until we create or join. */
    private var crewRef: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var ticker: Job? = null

    /** Server time minus this phone's time, so every car agrees when a vote ends. */
    @Volatile private var serverOffsetMillis = 0L

    init {
        db.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverOffsetMillis = (snapshot.value as? Number)?.toLong() ?: 0L
            }
            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    private fun now(): Long = System.currentTimeMillis() + serverOffsetMillis

    // ---- CrewRepository ----

    override suspend fun createCrew(name: String, destination: Place): Crew {
        val code = DemoData.DEMO_CREW_CODE
        val ref = db.getReference("crews").child(code)
        // A new crew replaces the old one with the same code (old drops are cleared).
        ref.setValue(
            mapOf(
                "info" to mapOf("name" to name, "hostCarId" to myCarId, "destination" to placeToMap(destination)),
                "cars" to LINEUP.associate { car -> car.id to mapOf("connected" to (car.id == myCarId)) },
            ),
        ).await()
        startListening(ref)
        return _crew.value ?: buildCrew(code, name, myCarId, destination, setOf(myCarId))
    }

    override suspend fun joinCrew(code: String): Crew? {
        val digits = code.filter { it.isDigit() }
        val ref = db.getReference("crews").child(digits)
        val info = ref.child("info").get().await()
        if (!info.exists()) return null
        ref.child("cars").child(myCarId).child("connected").setValue(true).await()
        startListening(ref)
        return _crew.value ?: parseCrew(digits, ref.get().await())
    }

    override suspend fun setDestination(destination: Place) {
        crewRef?.child("info")?.child("destination")?.setValue(placeToMap(destination))?.await()
    }

    /** Every device reads the name from crews/<code>/info/name, so they all see the new one. */
    override suspend fun renameCrew(name: String) {
        crewRef?.child("info")?.child("name")?.setValue(name)?.await()
    }

    override suspend fun sendDrop(draft: DropDraft): Drop {
        val ref = crewRef ?: error("크루에 먼저 참여해 주세요")
        val created = now()
        val drop = Drop(
            id = UUID.randomUUID().toString(),
            fromCarId = myCarId,
            kind = draft.kind,
            topic = draft.topic,
            title = draft.title,
            message = draft.message,
            candidates = draft.candidates,
            createdAtMillis = created,
            deadlineMillis = created + draft.voteSeconds * 1_000L,
            toCarIds = draft.toCarIds,
        )
        ref.child("drops").child(drop.id).setValue(dropToMap(drop)).await()
        return drop
    }

    override suspend fun vote(dropId: String, candidateId: String) {
        val ref = crewRef ?: return
        ref.child("drops").child(dropId).child("votes").child(myCarId).setValue(candidateId).await()
    }

    override fun leaveCrew() {
        crewRef?.child("cars")?.child(myCarId)?.child("connected")?.setValue(false)
        stopListening()
        _crew.value = null
        _drops.value = emptyList()
    }

    // ---- listening ----

    private fun startListening(ref: DatabaseReference) {
        stopListening()
        crewRef = ref
        // If this car loses its connection (app closed, no network), Firebase marks it offline for us.
        ref.child("cars").child(myCarId).child("connected").onDisconnect().setValue(false)

        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _crew.value = parseCrew(ref.key ?: "", snapshot)
                _drops.value = snapshot.child("drops").children
                    .mapNotNull(::parseDrop)
                    .sortedBy { it.createdAtMillis }
                closeFinishedVotes()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Firebase refused to read the crew: ${error.message}")
            }
        }
        ref.addValueEventListener(l)
        listener = l

        // Votes also end when the timer runs out, with nobody touching the data, so check every second.
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                closeFinishedVotes()
            }
        }
    }

    private fun stopListening() {
        listener?.let { l -> crewRef?.removeEventListener(l) }
        listener = null
        ticker?.cancel()
        ticker = null
        crewRef = null
    }

    /**
     * Writes the winner of every vote that is over. Every car runs this, so the write is a
     * transaction: only the first car writes, the others see a winner already there and stop.
     */
    private fun closeFinishedVotes() {
        val ref = crewRef ?: return
        val carIds = _crew.value?.cars?.map { it.id } ?: return
        val time = now()
        for (drop in _drops.value) {
            if (drop.kind != DropKind.DECISION || drop.isDecided || drop.candidates.isEmpty()) continue
            val voters = drop.voterIds(carIds)
            if (!VoteRules.isOver(drop, voters, time)) continue
            val winner = VoteRules.winner(drop, voters) ?: continue
            ref.child("drops").child(drop.id).child("decidedCandidateId")
                .runTransaction(object : Transaction.Handler {
                    override fun doTransaction(current: MutableData): Transaction.Result {
                        if (current.value == null) current.value = winner
                        return Transaction.success(current)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, data: DataSnapshot?) {
                        if (error != null) Log.w(TAG, "Could not close vote ${drop.id}: ${error.message}")
                    }
                })
        }
    }

    // ---- reading the database ----

    private fun parseCrew(code: String, snapshot: DataSnapshot): Crew? {
        val info = snapshot.child("info")
        if (!info.exists()) return null
        val destination = mapToPlace(info.child("destination")) ?: DemoData.gyeongpo
        val connected = LINEUP.map { it.id }
            .filter { id -> snapshot.child("cars").child(id).child("connected").value == true }
            .toSet()
        return buildCrew(
            code = code,
            name = info.child("name").getValue(String::class.java) ?: DemoData.DEMO_CREW_NAME,
            hostCarId = info.child("hostCarId").getValue(String::class.java) ?: myCarId,
            destination = destination,
            connected = connected,
        )
    }

    /** The crew always lists the same 4 cars in the same order; this car gets "(나)". */
    private fun buildCrew(code: String, name: String, hostCarId: String, destination: Place, connected: Set<String>) =
        Crew(
            code = code,
            name = name,
            destination = destination,
            hostCarId = hostCarId,
            cars = LINEUP.map { car ->
                val mine = car.id == myCarId
                car.copy(
                    name = if (mine) "${car.name} (나)" else car.name,
                    isMe = mine,
                    isBot = false,
                    connected = mine || car.id in connected,
                )
            },
        )

    private fun parseDrop(s: DataSnapshot): Drop? = runCatching {
        Drop(
            id = s.key!!,
            fromCarId = s.child("fromCarId").getValue(String::class.java)!!,
            kind = DropKind.valueOf(s.child("kind").getValue(String::class.java)!!),
            topic = DropTopic.valueOf(s.child("topic").getValue(String::class.java)!!),
            title = s.child("title").getValue(String::class.java).orEmpty(),
            message = s.child("message").getValue(String::class.java).orEmpty(),
            candidates = s.child("candidates").children.mapNotNull { c ->
                val place = mapToPlace(c.child("place")) ?: return@mapNotNull null
                Candidate(
                    id = c.child("id").getValue(String::class.java) ?: return@mapNotNull null,
                    place = place,
                    reason = c.child("reason").getValue(String::class.java).orEmpty(),
                    detourMinutes = c.child("detourMinutes").number()?.toInt() ?: 0,
                )
            },
            votes = s.child("votes").children.associate { v -> v.key!! to v.getValue(String::class.java).orEmpty() },
            createdAtMillis = s.child("createdAtMillis").number()?.toLong() ?: 0L,
            deadlineMillis = s.child("deadlineMillis").number()?.toLong() ?: 0L,
            decidedCandidateId = s.child("decidedCandidateId").getValue(String::class.java),
            toCarIds = s.child("toCarIds").children.mapNotNull { it.getValue(String::class.java) },
        )
    }.onFailure { Log.w(TAG, "Skipped a drop it could not read (${s.key}): $it") }.getOrNull()

    private fun mapToPlace(s: DataSnapshot): Place? {
        if (!s.exists()) return null
        return Place(
            id = s.child("id").getValue(String::class.java) ?: return null,
            name = s.child("name").getValue(String::class.java).orEmpty(),
            lat = s.child("lat").number()?.toDouble() ?: return null,
            lng = s.child("lng").number()?.toDouble() ?: return null,
            detail = s.child("detail").getValue(String::class.java).orEmpty(),
        )
    }

    // ---- writing the database ----

    private fun placeToMap(p: Place) =
        mapOf("id" to p.id, "name" to p.name, "lat" to p.lat, "lng" to p.lng, "detail" to p.detail)

    private fun dropToMap(d: Drop) = mapOf(
        "fromCarId" to d.fromCarId,
        "kind" to d.kind.name,
        "topic" to d.topic.name,
        "title" to d.title,
        "message" to d.message,
        "candidates" to d.candidates.map { c ->
            mapOf("id" to c.id, "reason" to c.reason, "detourMinutes" to c.detourMinutes, "place" to placeToMap(c.place))
        },
        "createdAtMillis" to d.createdAtMillis,
        "deadlineMillis" to d.deadlineMillis,
        "toCarIds" to d.toCarIds,
    )

    companion object {
        private const val TAG = "CarDropCrew"

        const val DATABASE_URL = "https://cardrop-d47ca-default-rtdb.asia-southeast1.firebasedatabase.app"

        /** The 4 cars of the demo crew, in crew order (names without "(나)"). */
        val LINEUP: List<Car> = listOf(
            DemoData.me.copy(name = "아빠 차"),
            DemoData.uncle,
            DemoData.minji,
            DemoData.aunt,
        )

        /** local.properties `cardrop.myCar` -> this device's car. Unknown or empty = 아빠 차. */
        fun carFor(key: String): Car = when (key.trim().lowercase()) {
            "uncle" -> DemoData.uncle
            "minji" -> DemoData.minji
            "aunt" -> DemoData.aunt
            else -> LINEUP.first()
        }
    }
}

/** Firebase stores 37.0 as 37, so read every number as a Number first. */
private fun DataSnapshot.number(): Number? = value as? Number

/** Waits for a Firebase write or read to finish, without blocking the screen. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) cont.resumeWithException(error) else cont.resume(task.result)
    }
}
