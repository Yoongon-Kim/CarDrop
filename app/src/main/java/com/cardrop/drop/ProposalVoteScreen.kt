package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.text.particle
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.crew.CrewViewModel
import kotlinx.coroutines.delay

/**
 * Board 11 for a destination proposal ([VoteRules.isProposal]) · Owner: Member A.
 *
 * Another car asked "속초 해수욕장으로 목적지를 바꿀까요?". This car answers 찬성, 반대, or 다른 목적지 제안하기,
 * which says 반대 here and opens the same search the proposer used ([DestinationProposalPanel]). One 반대 turns the
 * proposal down at once and the destination stays. With no 반대 by the end, the crew's destination changes and board
 * 14 opens on its own (DropRouting), as for any vote. After this car's answer or its new proposal the screen goes back
 * to 홈 right away ([onClose]; the voice says what went out, lead, 2026-11-15).
 * The car that sent it sees the same screen without the buttons (from the inbox): it said 찬성 by proposing.
 */
@Composable
fun ProposalVoteScreen(
    vm: CrewViewModel,
    drop: Drop,
    onOpenResult: (dropId: String) -> Unit,
    onClose: () -> Unit,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val cars = crew?.cars.orEmpty()
    val me = vm.myCarId
    val place = drop.candidates.first().place // a proposal has exactly one place
    // The cars that answer: the sender and the cars it was sent to, in crew order.
    val voters = drop.voterIds(cars.map { it.id }).mapNotNull { id -> cars.firstOrNull { it.id == id } }
    val view = proposalView(drop, me, voters.map { it.id })

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(drop.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secondsLeft = ((drop.deadlineMillis - now) / 1000).coerceAtLeast(0)

    // True after 다른 목적지 제안하기: the search takes this screen's place until the new proposal is sent.
    var countering by remember(drop.id) { mutableStateOf(false) }
    if (countering) {
        val toCarIds = ProposalRules.counterAudience(drop, me)
        DestinationProposalPanel(
            vm = vm,
            title = "어디로 다시 제안할까요?",
            recipients = crew?.recipientLabel(toCarIds, me) ?: "크루 전체",
            onBack = { countering = false },
            onAskAi = null,
            onSend = { other -> vm.proposeDestination(other, toCarIds, against = drop) { onClose() } },
            answering = place, // proposing the same place again would be no other destination
        )
        return
    }

    val mine = drop.fromCarId == me
    val sender = if (mine) "우리 차" else vm.carName(drop.fromCarId)
    ScreenColumn(bottom = {
        when (view) {
            // This car's answer: the two big buttons side by side, the counter-proposal under them.
            ProposalView.ASK -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        "반대",
                        onClick = {
                            vm.vote(drop.id, VoteRules.NO, said = "반대했어요. 목적지는 그대로예요")
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = CdColors.RedLight,
                        contentColor = CdColors.Red,
                        height = 72.dp,
                        radius = 22.dp,
                        fontSize = 20.sp,
                    )
                    PrimaryButton(
                        "찬성",
                        onClick = {
                            vm.vote(drop.id, drop.candidates.first().id, said = "찬성했어요")
                            onClose()
                        },
                        modifier = Modifier.weight(1f),
                        height = 72.dp,
                        radius = 22.dp,
                        fontSize = 20.sp,
                    )
                }
                // White: it sits on the grey page, where the grey button would look like plain text.
                SecondaryButton(
                    "다른 목적지 제안하기",
                    onClick = { countering = true },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = CdColors.White,
                )
            }
            ProposalView.WAITING -> PrimaryButton(
                if (mine) "일행의 답을 기다리는 중" else "응답했어요 · 다른 차를 기다리는 중",
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                height = 72.dp,
                radius = 22.dp,
                fontSize = 20.sp,
            )
            ProposalView.ACCEPTED -> PrimaryButton(
                "결과 보기",
                onClick = { onOpenResult(drop.id) },
                modifier = Modifier.fillMaxWidth(),
                height = 72.dp,
                radius = 22.dp,
                fontSize = 20.sp,
            )
            // Turned down: go back to driving, or try another place.
            ProposalView.REJECTED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("닫기", onClick = onClose, height = 72.dp, radius = 22.dp, containerColor = CdColors.White)
                PrimaryButton(
                    "다른 목적지 제안하기",
                    onClick = { countering = true },
                    modifier = Modifier.weight(1f),
                    height = 72.dp,
                    radius = 22.dp,
                    fontSize = 20.sp,
                )
            }
        }
    }) {
        ScreenHeader(
            eyebrow = null,
            title = if (mine) "목적지 제안을 보냈어요" else "목적지 제안이 왔어요",
            tag = {
                Pill(
                    "${sender}의 제안 · 목적지 변경",
                    background = CdColors.OrangeLight,
                    color = CdColors.OrangeDark,
                    fontSize = 13.sp,
                    padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            titleSize = 32.sp,
            titleLetterSpacing = TightLetterSpacing,
            subtitle = crew?.destination?.let { "지금 목적지는 ${it.name.particle("이에요", "예요")}" },
            subtitleSize = 15.sp,
            gap = 6.dp,
            verticalAlignment = Alignment.Top,
        ) {
            // The time left, as on the vote; "마감" in grey once it is over.
            Text(
                if (drop.isDecided) "마감" else "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                color = if (drop.isDecided) CdColors.Gray450 else CdColors.Alert,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp,
            )
        }
        // The proposed place: "속초 해수욕장으로 바꿀까요?" over its address.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CdColors.White)
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(CdColors.TealLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Flag, contentDescription = null, tint = CdColors.TealDark, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${place.name.withRo()} 바꿀까요?",
                    color = CdColors.Ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (place.detail.isNotBlank()) {
                    Text(place.detail, color = CdColors.Gray600, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Every car that answers, with what it said so far.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            voters.forEach { car -> AnswerTile(car, answerOf(drop, car.id), Modifier.weight(1f)) }
        }
        // What happens next, or how it ended.
        val (text, background, color) = when (view) {
            ProposalView.ACCEPTED -> Triple("모두 찬성했어요 · 목적지를 ${place.name.withRo()} 바꿨어요", CdColors.GreenLight, CdColors.Green)
            ProposalView.REJECTED -> Triple(rejectedText(drop, voters, me), CdColors.RedLight, CdColors.Red)
            else -> Triple("한 대라도 반대하면 목적지는 그대로예요 · 시간 안에 답이 없으면 찬성으로 봐요", CdColors.White, CdColors.Gray600)
        }
        Text(
            text,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (drop.isDecided) FontWeight.ExtraBold else FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** One car's answer: its badge and name over 찬성 (blue), 반대 (red) or 대기 중 (grey). */
@Composable
private fun AnswerTile(car: Car, answer: ProposalAnswer, modifier: Modifier) {
    val (label, background, color) = when (answer) {
        ProposalAnswer.YES -> Triple("찬성", CdColors.BlueLight, CdColors.BlueDark)
        ProposalAnswer.NO -> Triple("반대", CdColors.RedLight, CdColors.Red)
        ProposalAnswer.WAITING -> Triple("대기 중", CdColors.Gray100, CdColors.Gray600)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CdColors.White)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CarBadge(car, size = 30)
        Text(car.name, color = CdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AnswerPill(label, background, color)
    }
}

@Composable
private fun AnswerPill(label: String, background: Color, color: Color) {
    Pill(label, background = background, color = color, fontSize = 13.sp, padding = PaddingValues(horizontal = 12.dp, vertical = 5.dp))
}

/** What a car said to a proposal so far. */
internal enum class ProposalAnswer { YES, NO, WAITING }

/**
 * The line under a turned-down proposal: who said 반대 ("민지 차가 반대해서 목적지는 그대로예요"). This car is
 * "우리 차", as in the tag at the top: its crew name, "아빠 차 (나)", reads badly in a sentence.
 */
internal fun rejectedText(drop: Drop, voters: List<Car>, myCarId: String): String {
    val names = voters.filter { VoteRules.saidNo(drop, it.id) }.map { if (it.id == myCarId) "우리 차" else it.name }
    if (names.isEmpty()) return "반대가 있어 목적지는 그대로예요"
    return "${names.joinToString(" · ").particle("이", "가")} 반대해서 목적지는 그대로예요"
}

/**
 * [carId]'s answer on screen. The proposer counts as 찬성 from the start (a bot proposer never writes a vote).
 * Once the proposal has passed, the cars that said nothing show 찬성 too, as the rule counts them.
 */
internal fun answerOf(drop: Drop, carId: String): ProposalAnswer = when {
    VoteRules.saidNo(drop, carId) -> ProposalAnswer.NO
    carId in drop.votes || carId == drop.fromCarId -> ProposalAnswer.YES
    drop.isDecided && !VoteRules.rejected(drop) -> ProposalAnswer.YES
    else -> ProposalAnswer.WAITING
}

/** Which buttons the proposal screen shows. */
internal enum class ProposalView { ASK, WAITING, ACCEPTED, REJECTED }

/**
 * The state of the proposal for [myCarId]: [ProposalView.ASK] while it can still answer (it is one of [voterIds],
 * didn't send the proposal and hasn't answered), [ProposalView.WAITING] for the other cars' answers, then how it ended.
 */
internal fun proposalView(drop: Drop, myCarId: String, voterIds: List<String>): ProposalView = when {
    VoteRules.rejected(drop) -> ProposalView.REJECTED
    drop.isDecided -> ProposalView.ACCEPTED
    myCarId in voterIds && myCarId != drop.fromCarId && myCarId !in drop.votes -> ProposalView.ASK
    else -> ProposalView.WAITING
}
