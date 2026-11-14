package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Candidate
import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.Place
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel
import com.cardrop.crew.DropDraft
import com.cardrop.crew.SearchBox
import com.cardrop.navi.KeyboardRoom
import com.cardrop.navi.PlaceCard
import com.cardrop.navi.PlaceSuggestionList

/**
 * Owner: Member A. The plain rules of a destination proposal (unit tested, ProposalRulesTest).
 *
 * A proposal is a 목적지 변경 drop with one place: a driver who wants another destination already knows where,
 * so the car searches for that place and asks the others 찬성 / 반대 ([VoteRules.isProposal]). The AI's three
 * picks stay as an extra ("AI 추천 받기", board 10).
 */
object ProposalRules {

    /** The drop to send for [place]: "속초 해수욕장으로 목적지를 바꿀까요?", with the place's address under it. */
    fun draft(place: Place, toCarIds: List<String>): DropDraft = DropDraft(
        kind = DropKind.DECISION,
        topic = DropTopic.DESTINATION,
        title = "${place.name.withRo()} 목적지를 바꿀까요?",
        message = place.detail,
        candidates = listOf(Candidate(id = place.id, place = place, reason = place.detail, detourMinutes = 0)),
        toCarIds = toCarIds,
    )

    /**
     * Who a counter-proposal ("다른 목적지 제안하기") goes to: the same cars that took part in [original], seen
     * from [myCarId]. Empty = the whole crew, as in `Drop.toCarIds`.
     */
    fun counterAudience(original: Drop, myCarId: String): List<String> =
        if (original.toCarIds.isEmpty()) emptyList()
        else (original.toCarIds + original.fromCarId).distinct().filter { it != myCarId }

    /** True when [place] is where the crew is heading already: proposing it would change nothing. */
    fun isCurrent(place: Place, destination: Place?): Boolean = isSame(place, destination)

    /**
     * Why [place] can't be proposed, or null when it can: the crew is heading there already, or it is the place of
     * [answering], the proposal this one answers ("다른 목적지 제안하기"), which this car just said 반대 to.
     */
    fun whyNot(place: Place, destination: Place?, answering: Place?): String? = when {
        isCurrent(place, destination) -> "지금 가고 있는 목적지예요. 다른 곳을 골라 주세요."
        isSame(place, answering) -> "방금 제안된 곳이에요. 다른 곳을 골라 주세요."
        else -> null
    }

    /** The same place: the same id, or the same name (a place found again by another search gets another id). */
    private fun isSame(place: Place, other: Place?): Boolean =
        other != null && (place.id == other.id || place.name == other.name)
}

/**
 * "어디로 제안할까요?": search for the new destination, then confirm ("여기로 제안을 보낼까요?"). Used by board 08's
 * 목적지 변경 tile and by board 11's "다른 목적지 제안하기". It fills the screen in place of its caller.
 *
 * The search is board 02's (Kakao's place search, 말로 검색, the app's destinations before typing), so a proposed
 * place always has a location the navigator can drive to. [recipients] = who gets it ("크루 전체"). [onAskAi] opens
 * board 10 with the AI's three picks; null hides the button. [onSend] sends the proposal for the picked place.
 * [answering] = the place of the proposal a "다른 목적지 제안하기" answers: it is left out like the current destination.
 */
@Composable
fun DestinationProposalPanel(
    vm: CrewViewModel,
    title: String,
    recipients: String,
    onBack: () -> Unit,
    onAskAi: (() -> Unit)?,
    onSend: (Place) -> Unit,
    answering: Place? = null,
) {
    var query by remember { mutableStateOf("") }
    // The place the driver tapped; null while searching.
    var picked by remember { mutableStateOf<Place?>(null) }
    val crew by vm.crew.collectAsStateWithLifecycle()
    val suggestions by vm.destinationSuggestions.collectAsStateWithLifecycle()
    val voice by vm.destinationVoice.collectAsStateWithLifecycle()
    val sending by vm.proposal.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        vm.openDestinationSearch()
        vm.clearProposalError()
        focus.requestFocus() // this step is for typing: the keyboard comes up
    }
    DisposableEffect(Unit) { onDispose { vm.stopListening() } } // leaving stops the mic
    val pick: (Place) -> Unit = {
        focusManager.clearFocus() // the keyboard goes away, so the question and its buttons show
        vm.clearProposalError()
        picked = it
    }
    val current = crew?.destination

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Back from the question returns to the search; back from the search leaves.
            BackButton(onClick = { if (picked != null) picked = null else onBack() })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (picked == null) title else "여기로 제안을 보낼까요?",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CdColors.Ink,
                )
                Text(
                    listOfNotNull("받는 차 · $recipients", current?.let { "지금 목적지 · ${it.name}" }).joinToString("   "),
                    color = CdColors.Gray600,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (picked == null && onAskAi != null) {
                CdChip("AI 추천 받기", onClick = onAskAi, style = ChipStyle.Blue, height = 48.dp, fontSize = 15.sp)
            }
        }
        val place = picked
        if (place != null) {
            ConfirmCard(
                place = place,
                whyNot = ProposalRules.whyNot(place, current, answering),
                sending = sending.sending,
                error = sending.error,
                onAgain = { picked = null },
                onSend = { onSend(place) },
            )
        } else {
            SearchBox(
                query = query,
                onQueryChange = {
                    query = it
                    vm.onDestinationTyped(it)
                },
                focus = focus,
                // Enter picks the top place, like board 02; the driver still confirms it on the next step.
                onDone = { vm.bestDestination(query, pick) },
                onClear = {
                    query = ""
                    vm.onDestinationTyped("")
                },
                listening = voice.listening,
                onListen = if (vm.canListen) {
                    {
                        focusManager.clearFocus()
                        vm.listenForDestination { heard -> query = heard }
                    }
                } else {
                    null
                },
            )
            voice.status?.let { StatusText(it) }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (suggestions.shown) {
                    PlaceSuggestionList(suggestions, onPick = { pick(it.place) })
                } else {
                    // Before typing: the app's destinations, without the one the crew is heading to (or the one
                    // this car is answering).
                    val known = vm.knownDestinations().filter { ProposalRules.whyNot(it.place, current, answering) == null }
                    if (known.isNotEmpty()) {
                        Text("추천 목적지", color = CdColors.Gray600, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        PlaceCard(known, highlight = "", onPick = { pick(it.place) })
                    }
                }
                // Room to scroll the last places up above the keyboard.
                Spacer(Modifier.height(KeyboardRoom))
            }
        }
    }
}

/**
 * The second step: the picked place in a white card, what happens next in grey, and 다시 고르기 / 제안 보내기.
 * It sits at the top of the screen, above where Pleos's keyboard would be. [whyNot] = why this place can't be
 * proposed ([ProposalRules.whyNot]); then it is said in red and 제안 보내기 is greyed out.
 */
@Composable
private fun ConfirmCard(
    place: Place,
    whyNot: String?,
    sending: Boolean,
    error: String?,
    onAgain: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(CdColors.White)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(CdColors.TealLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Flag, contentDescription = null, tint = CdColors.TealDark, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    place.name,
                    color = CdColors.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place.detail.isNotBlank()) {
                    Text(place.detail, color = CdColors.Gray600, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (whyNot != null) {
            ErrorText(whyNot)
        } else {
            Text(
                "받는 차가 찬성 · 반대로 답해요. 한 대라도 반대하면 목적지는 그대로예요 · 1분 안에 답이 없으면 찬성으로 봐요",
                color = CdColors.Gray600,
                fontSize = 14.sp,
            )
        }
        error?.let { ErrorText(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("다시 고르기", onClick = onAgain, enabled = !sending)
            PrimaryButton(
                text = if (sending) "보내는 중…" else "제안 보내기",
                onClick = onSend,
                enabled = !sending && whyNot == null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
