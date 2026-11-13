package com.cardrop.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.text.particle
import com.cardrop.crew.CrewRepository
import com.cardrop.drive.TripDecisions
import com.cardrop.voice.VoiceService

/**
 * Owner: lead.
 *
 * Opens the right screen when something arrives from another car:
 * - a SOCIAL drop -> a quiet on-screen message, never spoken
 * - a FUEL drop -> board 06 (같이 갈래요?)
 * - any other DECISION drop -> board 11 (vote), announced by voice
 * - a vote that just ended -> board 14 (decision), only if it changed this car's route ([DecidedScreenPicker])
 *
 * [memory] says what was already opened. It comes from AppContainer, so when an activity restart starts
 * this effect again, old drops are not opened a second time or said aloud again.
 */
@Composable
fun DropRoutingEffect(
    crew: CrewRepository,
    voice: VoiceService,
    navigator: AppNavigator,
    memory: DropRoutingMemory,
    onSocialMessage: (String) -> Unit,
) {
    LaunchedEffect(crew, memory) {
        crew.drops.collect { drops ->
            for (drop in memory.newFromOthers(drops)) {
                val sender = crew.crew.value?.cars?.firstOrNull { it.id == drop.fromCarId }?.name ?: "일행"
                when {
                    drop.kind == DropKind.SOCIAL -> onSocialMessage("$sender · ${drop.title}")
                    drop.topic == DropTopic.FUEL -> {
                        voice.speak(drop.title)
                        navigator.go(Screen.FuelThem(drop.id))
                    }
                    else -> {
                        voice.speak("${sender.particle("이", "가")} 투표를 보냈어요. ${drop.title}")
                        navigator.go(Screen.Vote(drop.id))
                    }
                }
            }
            memory.decidedToOpen(crew.crew.value, drops)?.let { navigator.go(Screen.Decided(it.id)) }
        }
    }
}

/**
 * What [DropRoutingEffect] already opened. AppContainer keeps one for the app's whole life, like the crew
 * and trip data. Pure Kotlin, so it is unit tested (DropRoutingMemoryTest).
 * [myCarId] is asked on every call, like [DecidedScreenPicker]: with the real crew the car is picked when
 * joining (board 03), after the app has started.
 * [isSkipped] tells which stops this car turned down (`NavigationHandoff.decisions`).
 */
class DropRoutingMemory(private val myCarId: () -> String, isSkipped: (dropId: String) -> Boolean = { false }) {
    private val seen = mutableSetOf<String>()
    private val decided = DecidedScreenPicker(myCarId, isSkipped)

    /**
     * The drops another car sent to this car that were not opened yet, oldest first. Each drop comes back
     * only once. A drop sent only to other cars (board 08 "받는 차") never shows up on this car.
     */
    fun newFromOthers(drops: List<Drop>): List<Drop> {
        val me = myCarId()
        return drops.filter { it.isFor(me) && seen.add(it.id) && it.fromCarId != me }
    }

    /** The finished vote whose board 14 should open now, or null ([DecidedScreenPicker]). */
    fun decidedToOpen(crew: Crew?, drops: List<Drop>): Drop? = decided.pick(crew, drops)
}

/**
 * Picks the finished vote that opens board 14 (결정).
 *
 * It follows the rule TripCoordinator uses to change the route, and reuses its [TripDecisions] for the
 * order, so board 14 never says "X로 결정되었어요" while the navigator keeps another stop:
 * - only drops this car takes part in; a fuel stop only for the car that sent it or joined it (board 06);
 * - not a stop this car turned down ([isSkipped]);
 * - only the newest decision: a vote that ends after a newer one was decided opens nothing (the route
 *   keeps the newer stop), and when several decisions arrive together, only the newest one opens.
 *
 * It starts over when the crew changes. Pure Kotlin, so it is unit tested (DecidedScreenPickerTest).
 * [myCarId] is asked on every pick: with the real crew the car is picked when joining (board 03),
 * after this starts.
 */
internal class DecidedScreenPicker(
    private val myCarId: () -> String,
    private val isSkipped: (dropId: String) -> Boolean = { false },
) {
    private val order = TripDecisions()

    /** The drop whose board 14 should open now, or null. Each finished vote is looked at once. */
    fun pick(crew: Crew?, drops: List<Drop>): Drop? {
        order.forCrew(crew?.let { "${it.code}:${it.hostCarId}" })
        val me = myCarId()
        val pending = drops.filter {
            it.isFor(me) && it.kind == DropKind.DECISION && it.decidedCandidate != null && !order.wasHandled(it.id)
        }
        val newest = pending.filter { order.canApply(it) && involved(it, me) && !isSkipped(it.id) }
            .maxWithOrNull(compareBy<Drop> { it.createdAtMillis }.thenBy { it.id })
        pending.forEach { order.markHandled(it, applied = it.id == newest?.id) }
        return newest
    }

    private fun involved(drop: Drop, me: String): Boolean =
        drop.topic != DropTopic.FUEL || drop.fromCarId == me || me in drop.votes
}
