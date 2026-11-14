package com.cardrop.ai.game

import android.content.Context
import com.cardrop.crew.FirebaseCrewRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface OmokTransport {
    val games: StateFlow<Map<String, OmokGame>>
    val connected: StateFlow<Boolean>
    val generation: StateFlow<Int>
    fun now(): Long
    fun follow(key: String?)
    suspend fun change(transform: (Map<String, OmokGame>, Long) -> Map<String, OmokGame>)
    suspend fun presence(gameId: String, carId: String, value: OmokPresence)
}

interface OmokRoomStore { fun read(key: String): String?; fun write(key: String, value: String) }

/** The bot crew's boards, kept only while the app runs: after an app restart the board starts empty. */
class MemoryOmokRoomStore : OmokRoomStore {
    private val boards = mutableMapOf<String, String>()
    override fun read(key: String): String? = boards[key]
    override fun write(key: String, value: String) { boards[key] = value }
}

/** Also used by protocol tests. State changes and persistence are synchronous, never optimistic. */
class LocalOmokTransport(private val store: OmokRoomStore, private val clock: () -> Long = System::currentTimeMillis) : OmokTransport {
    private val state = MutableStateFlow<Map<String, OmokGame>>(emptyMap())
    override val games: StateFlow<Map<String, OmokGame>> = state
    override val connected = MutableStateFlow(true)
    override val generation = MutableStateFlow(0)
    private var key: String? = null
    override fun now(): Long = clock()
    override fun follow(key: String?) {
        if (this.key == key) return
        this.key = key
        generation.update { it + 1 }
        // Every restored lease is closed; a new foreground/P check must reopen it.
        state.value = key?.let(store::read)?.let(OmokCodec::fromJson).orEmpty().mapValues { (_, g) ->
            g.copy(presence = emptyMap())
        }
    }
    override suspend fun change(transform: (Map<String, OmokGame>, Long) -> Map<String, OmokGame>) {
        val currentKey = key ?: throw OmokRejected("크루에 먼저 참여해 주세요")
        val next = transform(state.value, now())
        store.write(currentKey, OmokCodec.toJson(next))
        state.value = next
    }
    override suspend fun presence(gameId: String, carId: String, value: OmokPresence) {
        val game = state.value[gameId] ?: return
        if (carId !in game.players) return
        state.value = state.value + (gameId to game.copy(presence = game.presence + (carId to value)))
    }
}

/**
 * A separate Firebase client isolates cancel/purge from crew votes, location and safety recording.
 * No offline moves: disconnects/timeouts discard this client's outstanding writes. Leases and revisions
 * are rechecked inside every transaction retry; UI sees the board only after the server commits.
 */
class FirebaseOmokTransport(context: Context) : OmokTransport {
    private val firebaseApp = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
        ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, APP_NAME)
    private val db = FirebaseDatabase.getInstance(firebaseApp, FirebaseCrewRepository.DATABASE_URL)
    private val state = MutableStateFlow<Map<String, OmokGame>>(emptyMap())
    override val games: StateFlow<Map<String, OmokGame>> = state
    override val connected = MutableStateFlow(false)
    override val generation = MutableStateFlow(0)
    @Volatile private var offset = 0L
    @Volatile private var key: String? = null
    private var ref: com.google.firebase.database.DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private val disconnectRegistered = mutableSetOf<String>()

    init {
        db.setPersistenceEnabled(false)
        db.getReference(".info/serverTimeOffset").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { offset = (snapshot.value as? Number)?.toLong() ?: 0 }
            override fun onCancelled(error: DatabaseError) = Unit
        })
        db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.value == true
                if (connected.value != online) generation.update { it + 1 }
                connected.value = online
                if (!online) {
                    db.purgeOutstandingWrites()
                    disconnectRegistered.clear()
                }
            }
            override fun onCancelled(error: DatabaseError) { connected.value = false }
        })
    }

    override fun now(): Long = System.currentTimeMillis() + offset
    override fun follow(key: String?) {
        if (this.key == key) return
        listener?.let { ref?.removeEventListener(it) }
        db.purgeOutstandingWrites()
        generation.update { it + 1 }
        this.key = key
        disconnectRegistered.clear()
        state.value = emptyMap()
        ref = key?.let { db.getReference("omokGames").child(it).child("rooms") }
        listener = ref?.let { node -> object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (this@FirebaseOmokTransport.key == key) state.value = OmokCodec.decodeRooms(snapshot.value)
            }
            override fun onCancelled(error: DatabaseError) { connected.value = false }
        }.also(node::addValueEventListener) }
    }

    override suspend fun change(transform: (Map<String, OmokGame>, Long) -> Map<String, OmokGame>) = guarded {
        val node = ref ?: throw OmokRejected("크루에 먼저 참여해 주세요")
        val observedKey = key
        suspendCancellableCoroutine<Unit> { continuation ->
            var rejection: Exception? = null
            node.runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    rejection = null
                    return try {
                        if (key != observedKey || !connected.value) throw OmokRejected("연결이 바뀌었어요. 다시 시도해 주세요")
                        currentData.value = OmokCodec.encodeRooms(transform(OmokCodec.decodeRooms(currentData.value), now()))
                        Transaction.success(currentData)
                    } catch (e: Exception) { rejection = e; Transaction.abort() }
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (!continuation.isActive) return
                    when {
                        error != null -> continuation.resumeWithException(error.toException())
                        !committed -> continuation.resumeWithException(rejection ?: OmokRejected("대국이 갱신됐어요. 다시 시도해 주세요"))
                        else -> {
                            if (key == observedKey) state.value = OmokCodec.decodeRooms(currentData?.value)
                            continuation.resume(Unit)
                        }
                    }
                }
            }, false)
        }
    }

    override suspend fun presence(gameId: String, carId: String, value: OmokPresence) = guarded {
        val node = ref?.child(gameId)?.child("presence")?.child(carId) ?: return@guarded
        val registration = "$gameId/$carId/${generation.value}"
        if (registration !in disconnectRegistered) {
            node.onDisconnect().updateChildren(mapOf("connected" to false, "ready" to false)).omokAwait()
            disconnectRegistered += registration
        }
        // setValue at a descendant can cancel a pending ancestor transaction in this SDK. Keep
        // heartbeats in the same transaction stream as moves instead of overwriting their subtree.
        change { rooms, _ ->
            val game = rooms[gameId]
            if (game == null || carId !in game.players) rooms
            else rooms + (gameId to game.copy(presence = game.presence + (carId to value)))
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        if (!connected.value) throw OmokRejected("연결을 확인하고 다시 시도해 주세요")
        try { withTimeout(5_000) { block() } }
        catch (e: CancellationException) {
            db.purgeOutstandingWrites()
            generation.update { it + 1 }
            throw e
        }
    }

    private companion object { const val APP_NAME = "cardrop-omok" }
}

private suspend fun <T> Task<T>.omokAwait(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: OmokRejected("연결을 확인해 주세요"))
        }
    }
}
