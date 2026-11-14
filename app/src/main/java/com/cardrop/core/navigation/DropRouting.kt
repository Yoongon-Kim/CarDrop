package com.cardrop.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.text.particle
import com.cardrop.core.text.withRo
import com.cardrop.crew.CrewRepository
import com.cardrop.drive.TripDecisions
import com.cardrop.drop.VoteRules
import com.cardrop.drop.voteTag
import com.cardrop.voice.VoiceService
import kotlinx.coroutines.delay

/**
 * Owner: lead.
 *
 * Opens the right screen when something arrives from another car:
 * - a song drop (SOCIAL) -> board 13 over board 04, never spoken (Member A)
 * - any other SOCIAL drop -> a quiet on-screen message, never spoken
 * - a FUEL drop -> board 06 (같이 갈래요?)
 * - any other DECISION drop -> board 11 (vote), announced by voice
 * - a vote that just ended -> board 14 (decision), only if it changed this car's route ([DecidedScreenPicker])
 * - a destination proposal that was just turned down -> said aloud ("…로 바꾸자는 제안은 반대가 있어서…")
 * - a vote this car hasn't answered, 30 s before it ends -> said aloud ([reminderLine]): no answer counts as place 1,
 *   and its board 14 would otherwise come as a surprise (lead, 2026-11-15)
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
                    drop.kind == DropKind.SOCIAL && drop.topic == DropTopic.SONG -> navigator.go(Screen.SongGet(drop.id))
                    drop.kind == DropKind.SOCIAL -> onSocialMessage("$sender · ${drop.title}")
                    drop.topic == DropTopic.FUEL -> {
                        voice.speak(drop.title)
                        navigator.go(Screen.FuelThem(drop.id))
                    }
                    else -> {
                        // A destination proposal asks 찬성 / 반대 on one place; any other vote picks between places.
                        val what = if (VoteRules.isProposal(drop)) "목적지 변경을 제안했어요" else "투표를 보냈어요"
                        voice.speak("${sender.particle("이", "가")} $what. ${drop.title}")
                        navigator.go(Screen.Vote(drop.id))
                    }
                }
            }
            memory.decidedToOpen(crew.crew.value, drops)?.let { navigator.go(Screen.Decided(it.id)) }
            // A proposal that was turned down opens no board 14 and changes no route, so it is said aloud instead.
            for (drop in memory.newlyRejected(drops)) {
                val place = drop.candidates.firstOrNull()?.place ?: continue
                voice.speak("${place.name.withRo()} 바꾸자는 제안은 반대가 있어서 목적지는 그대로예요")
            }
        }
    }
    // The reminders: the time left counts down without any new drop, so this looks every second.
    LaunchedEffect(crew, memory) {
        while (true) {
            val now = System.currentTimeMillis()
            for (drop in memory.dueReminders(crew.drops.value, now)) voice.speak(reminderLine(drop, now))
            delay(1_000)
        }
    }
}

/**
 * What the voice says when a vote this car hasn't answered is about to end: "카페 투표가 30초 남았어요. 답이 없으면 1번
 * 강문 스타벅스에 투표한 걸로 봐요"; for a destination proposal "속초 해수욕장으로 바꾸자는 제안이 30초 남았어요. 답이 없으면
 * 찬성으로 봐요". Pure Kotlin, unit tested (DropRoutingMemoryTest).
 */
internal fun reminderLine(drop: Drop, nowMillis: Long): String {
    val seconds = ((drop.deadlineMillis - nowMillis + 999) / 1000).coerceAtLeast(1)
    val first = drop.candidates.firstOrNull()?.place?.name.orEmpty()
    return if (VoteRules.isProposal(drop)) {
        "${first.withRo()} 바꾸자는 제안이 ${seconds}초 남았어요. 답이 없으면 찬성으로 봐요"
    } else {
        "${drop.topic.voteTag()} 투표가 ${seconds}초 남았어요. 답이 없으면 1번 ${first}에 투표한 걸로 봐요"
    }
}

/** How long before a vote ends the voice reminds a car that hasn't answered it ([DropRoutingMemory.dueReminders]). */
internal const val REMIND_BEFORE_MILLIS = 30_000L

/**
 * The screen a drop opens from the inbox (board 07): the same screens as when it arrives.
 * - a finished vote -> board 14 (결과 보기)
 * - a fuel invite -> board 06, whose 같이 갈래요 puts the station into the navigator right away
 *   (board 11 would only record a vote, and the station would wait for the vote to end)
 * - any other decision drop -> board 11 (vote)
 * Social drops have no button in the inbox, so they stay on it. Pure Kotlin, unit tested (InboxScreenForTest).
 */
fun inboxScreenFor(drop: Drop): Screen = when {
    drop.isDecided -> Screen.Decided(drop.id)
    drop.kind == DropKind.DECISION && drop.topic == DropTopic.FUEL -> Screen.FuelThem(drop.id)
    drop.kind == DropKind.DECISION -> Screen.Vote(drop.id)
    else -> Screen.Inbox
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

    /** Destination proposals for this car that were still open when last seen. */
    private val openProposals = mutableSetOf<String>()

    /**
     * The destination proposals for this car that were turned down since the last call, each once. Only proposals
     * this car saw while they were open, so an old one (a reconnect delivers the whole history) isn't said again.
     * Not for a car that said 반대 itself: it knows already.
     */
    fun newlyRejected(drops: List<Drop>): List<Drop> {
        val me = myCarId()
        val proposals = drops.filter { it.isFor(me) && VoteRules.isProposal(it) }
        val rejected = proposals.filter { VoteRules.rejected(it) && openProposals.remove(it.id) && !VoteRules.saidNo(it, me) }
        proposals.filter { !it.isDecided }.forEach { openProposals += it.id }
        return rejected
    }

    /** Votes this car was already reminded of. */
    private val reminded = mutableSetOf<String>()

    /**
     * The votes this car still has to answer that end within [REMIND_BEFORE_MILLIS], each once (the voice reminds the
     * driver, [reminderLine]). Not a fuel invite (no answer there means not going along, and the car that sent it never
     * votes) nor this car's own proposal (sending it said 찬성).
     */
    fun dueReminders(drops: List<Drop>, nowMillis: Long): List<Drop> {
        val me = myCarId()
        return drops.filter {
            it.isFor(me) && it.kind == DropKind.DECISION && it.topic != DropTopic.FUEL && !it.isDecided &&
                it.candidates.isNotEmpty() && me !in it.votes && !(VoteRules.isProposal(it) && it.fromCarId == me) &&
                it.deadlineMillis - nowMillis in 1..REMIND_BEFORE_MILLIS && reminded.add(it.id)
        }
    }
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
