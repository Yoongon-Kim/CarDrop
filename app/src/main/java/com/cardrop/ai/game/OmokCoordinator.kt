package com.cardrop.ai.game

import android.os.SystemClock
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Gear
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.crew.CrewRepository
import com.cardrop.vehicle.FakeVehicleRepository
import com.cardrop.vehicle.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class OmokSelection(val gameId: String, val revision: Int, val tokens: Map<String, String>, val cell: Int)

internal fun omokGearReady(snapshot: VehicleSnapshot, elapsed: Long, demo: Boolean): Boolean =
    snapshot.gear == Gear.PARK && (demo || snapshot.gearCheckedAtElapsedMillis?.let { elapsed - it in 0..OMOK_PRESENCE_MS } == true)

/**
 * App-scoped: incoming challenges and paused games survive navigation and activity recreation, but not an app
 * restart. Nothing is saved on the device, so a restarted app opens on its usual screen (lead, 2026-11-15).
 */
class OmokCoordinator(
    private val scope: CoroutineScope,
    private val crew: CrewRepository,
    private val vehicle: VehicleRepository,
    val transport: OmokTransport,
    private val real: Boolean,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    val games: StateFlow<Map<String, OmokGame>> = transport.games
    val pending = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val clock = MutableStateFlow(transport.now())
    val localReady = MutableStateFlow(false)
    val openRequests = MutableStateFlow(0)
    private val refresh = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var visible = false
    @Volatile private var gameVisible = false
    private val visibleOwners = mutableSetOf<Any>()
    private val gameOwners = mutableSetOf<Any>()
    private var readyToken = UUID.randomUUID().toString()
    private var lastReady = false
    private var lastGeneration = -1
    private var followedKey: String? = null
    private var botThinkingAt = mutableMapOf<String, Long>()
    val myCarId: String get() = crew.myCarId
    val currentCrew: StateFlow<Crew?> get() = crew.crew

    init {
        scope.launch {
            crew.crew.map { current ->
                current?.takeIf { it.isStarted }?.let { if (real) "${it.code}-${it.startedAtMillis}" else "demo-${it.code}" }
            }.distinctUntilChanged().collect { key ->
                followedKey = key
                readyToken = UUID.randomUUID().toString()
                botThinkingAt.clear()
                transport.follow(key)
            }
        }
        scope.launch {
            combine(vehicle.snapshot, transport.connected, transport.generation) { _, _, _ -> Unit }
                .collect { refreshReadiness(); refresh.trySend(Unit) }
        }
        scope.launch {
            while (true) { tick(); withTimeoutOrNull(1_000) { refresh.receive() } }
        }
    }

    fun openGame() { openRequests.value += 1 }
    fun onVisible(owner: Any, value: Boolean) {
        synchronized(visibleOwners) { if (value) visibleOwners += owner else visibleOwners -= owner; visible = visibleOwners.isNotEmpty() }
        refreshReadiness(); refresh.trySend(Unit)
    }
    fun onGameVisible(owner: Any, value: Boolean) {
        synchronized(gameOwners) { if (value) gameOwners += owner else gameOwners -= owner; gameVisible = gameOwners.isNotEmpty() }
        refreshReadiness(); refresh.trySend(Unit)
    }
    fun clearError() { error.value = null }

    @Synchronized private fun refreshReadiness() {
        val ready = visible && gameVisible && transport.connected.value && crew.crew.value?.isStarted == true &&
            omokGearReady(vehicle.snapshot.value, elapsedRealtime(), vehicle is FakeVehicleRepository)
        if (ready != lastReady || transport.generation.value != lastGeneration) {
            readyToken = UUID.randomUUID().toString()
            lastReady = ready
            lastGeneration = transport.generation.value
        }
        localReady.value = ready
    }

    private fun myPresence(now: Long): OmokPresence = OmokPresence(readyToken, localReady.value, transport.connected.value, now)

    private suspend fun tick() {
        clock.value = transport.now()
        refreshReadiness()
        val current = crew.crew.value ?: return
        val key = followedKey ?: return
        if (!transport.connected.value) return
        for (game in games.value.values.filter { myCarId in it.players && it.busy(clock.value) }) {
            try {
                transport.presence(game.id, myCarId, myPresence(transport.now()))
                if (followedKey != key) return
                if (!real) tickBot(current, games.value[game.id] ?: continue)
            } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
            catch (_: Exception) { /* The lease expires, closing play; no stale P is kept alive. */ }
        }
    }

    private suspend fun tickBot(current: Crew, game: OmokGame) {
        val bot = current.cars.firstOrNull { it.id == game.other(myCarId) && it.isBot && it.connected } ?: return
        transport.presence(game.id, bot.id, OmokPresence("bot-${readyToken}", localReady.value, true, transport.now()))
        val latest = games.value[game.id] ?: return
        val since = botThinkingAt.getOrPut("${latest.id}:${latest.revision}") { transport.now() }
        if (!localReady.value || transport.now() - since < 700) return
        if (latest.phase == OmokPhase.INVITED && latest.whiteId == bot.id) {
            transport.change { rooms, now ->
                val g = rooms[latest.id] ?: throw OmokRejected("대국을 찾을 수 없어요")
                rooms + (g.id to OmokRules.accept(g, bot.id, latest.revision, now))
            }
        } else if (latest.phase == OmokPhase.PLAYING && latest.turnId == bot.id && latest.ready(transport.now())) {
            val cell = OmokBot.choose(latest) ?: return
            transport.change { rooms, now ->
                val g = rooms[latest.id] ?: throw OmokRejected("대국을 찾을 수 없어요")
                refreshReadiness()
                if (!localReady.value) throw OmokRejected("정차 상태를 확인해 주세요")
                rooms + (g.id to OmokRules.move(g, bot.id, cell, latest.revision, latest.tokens(), now))
            }
        }
    }

    fun invite(opponentId: String) = action {
        refreshReadiness()
        if (!localReady.value) throw OmokRejected("P로 정차하고 게임 화면을 열어 주세요")
        val current = crew.crew.value ?: throw OmokRejected("크루에 먼저 참여해 주세요")
        if (!current.isStarted) throw OmokRejected("크루가 출발하면 게임을 초대할 수 있어요")
        if (current.cars.none { it.id == opponentId && it.id != myCarId && it.connected }) throw OmokRejected("연결된 상대 차량을 골라 주세요")
        val id = UUID.randomUUID().toString()
        transport.change { rooms, now ->
            refreshReadiness()
            if (!localReady.value) throw OmokRejected("정차 상태가 바뀌었어요")
            val game = OmokGame(id, myCarId, opponentId, now, presence = mapOf(myCarId to myPresence(now)))
            // Bound the lobby; old terminal/expired invitations need no listener forever.
            val kept = rooms.filterValues { it.busy(now) || now - it.createdAt < 86_400_000 }
            OmokRules.invite(kept, game, now)
        }
    }

    fun accept(game: OmokGame) = action {
        refreshReadiness()
        if (!localReady.value) throw OmokRejected("P로 정차하고 수락해 주세요")
        transport.presence(game.id, myCarId, myPresence(transport.now()))
        transport.change { rooms, now ->
            refreshReadiness()
            if (!localReady.value) throw OmokRejected("정차 상태가 바뀌었어요")
            val latest = rooms[game.id] ?: throw OmokRejected("도전장을 찾을 수 없어요")
            rooms + (game.id to OmokRules.accept(latest, myCarId, game.revision, now))
        }
    }

    fun close(game: OmokGame) = action {
        transport.change { rooms, now ->
            val latest = rooms[game.id] ?: throw OmokRejected("대국을 찾을 수 없어요")
            rooms + (game.id to OmokRules.close(latest, myCarId, game.revision, now))
        }
    }

    fun place(selection: OmokSelection) = action {
        refreshReadiness()
        if (!localReady.value) throw OmokRejected("P로 정차하고 놓을 자리를 다시 골라 주세요")
        if (selection.tokens[myCarId] != readyToken) throw OmokRejected("상태가 바뀌었어요. 놓을 자리를 다시 골라 주세요")
        transport.change { rooms, now ->
            refreshReadiness()
            if (!localReady.value) throw OmokRejected("정차 상태가 바뀌었어요")
            val game = rooms[selection.gameId] ?: throw OmokRejected("대국을 찾을 수 없어요")
            rooms + (game.id to OmokRules.move(game, myCarId, selection.cell, selection.revision, selection.tokens, now))
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (!pending.compareAndSet(false, true)) return
        error.value = null
        scope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) { error.value = "응답을 확인하지 못했어요. 현재 보드를 확인한 뒤 다시 시도해 주세요" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = if (e is OmokRejected) e.message else "대국을 갱신하지 못했어요. 연결을 확인해 주세요" }
            finally { pending.value = false }
        }
    }
}
