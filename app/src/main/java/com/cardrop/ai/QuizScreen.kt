package com.cardrop.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Crew
import com.cardrop.core.model.Drop
import com.cardrop.core.model.Place
import com.cardrop.core.model.QuizQuestion
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drive.StopNews
import com.cardrop.navi.WaypointResult

/**
 * Board 15 게임 (정차 중) · Owner: lead. Laid out like the design: the games on the left with "도전장 드랍", and on the
 * right "나 VS 상대", who to play with, and the quiz with a question counter. Week 1 has the two quizzes (no omok or
 * chess). It opens only in P; a game paused in D carries on at the next stop.
 */
@Composable
fun QuizScreen(
    vm: AiViewModel,
    /** The crew's stop this car is parked at (the navigator's `parkedAt`); null = parked somewhere else. */
    stop: Place? = null,
    /** This car's drops, for who else said "…에 도착했어요" there. */
    drops: List<Drop> = emptyList(),
) {
    val snapshot by vm.vehicleSnapshot.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    val round by vm.round.collectAsStateWithLifecycle()
    val crew by vm.crewState.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot.isParked) { if (snapshot.isParked) vm.ensureQuiz() }

    ScreenColumn {
        ScreenHeader(eyebrow = gameEyebrow(stop, crew, vm.myCarId, drops), title = "일행이랑 한 판 할까요?") {
            Pill("P · 정차 중에만 열려요")
        }
        if (!snapshot.isParked) {
            CdCard(Modifier.fillMaxWidth()) {
                Text("주행 중에는 게임이 멈춰요", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
                Text("다음 정차 때 P로 바꾸면 이어서 해요", fontSize = 15.sp, color = CdColors.Gray600)
            }
            return@ScreenColumn
        }
        val others = crew?.cars.orEmpty().filter { it.id != vm.myCarId }
        val opponent = others.firstOrNull { it.id == round.opponentId }
        val opponentName = opponent?.name ?: CREW_CAR.name
        // The design fills the screen's height (509): the two columns take what is left under the header.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuizKind.entries.forEach { kind -> GameRow(kind, selected = kind == round.kind) { vm.chooseQuiz(kind) } }
                Spacer(Modifier.weight(1f)) // 도전장 드랍 and its hint at the bottom of the column (512-513)
                ChallengeButton(opponentName, sent = round.challengeSent, enabled = crew != null, onClick = vm::sendChallenge)
                Text("D로 바꾸면 게임이 멈추고 다음 정차 때 이어서 해요", fontSize = 13.sp, color = CdColors.Gray600, lineHeight = 20.sp)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(CdColors.White)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VsRow(opponent ?: CREW_CAR, round)
                OpponentPicker(others, round.opponentId, vm::chooseOpponent)
                QuizBody(quiz, round, onAnswer = vm::answerQuiz, onNext = vm::nextQuestion, onRestart = vm::restartQuiz, onRetry = vm::loadQuiz)
            }
        }
    }
}

/**
 * Board 15's eyebrow (target 506): at the crew's stop, its name and how many cars stopped there, e.g.
 * "덕평자연휴게소 · 4대 모두 정차" or "덕평자연휴게소 · 2대 정차". The other cars count once they said
 * "…에 도착했어요" ([StopNews]) since the vote for that stop; this car counts because it is parked there.
 * Anywhere else: "정차 중 · 크루 전체". Internal so GameEyebrowTest can check it.
 */
internal fun gameEyebrow(stop: Place?, crew: Crew?, myCarId: String, drops: List<Drop>): String {
    if (stop == null || crew == null) return "정차 중 · 크루 전체"
    val votedAt = drops.filter { it.decidedCandidate?.place?.id == stop.id }.maxOfOrNull { it.createdAtMillis } ?: 0L
    val stopped = StopNews.results(drops, stop, sinceMillis = votedAt).filterValues { it == WaypointResult.ARRIVED }.keys + myCarId
    val count = crew.cars.count { it.id in stopped }
    return if (count == crew.cars.size) "${stop.name} · ${count}대 모두 정차" else "${stop.name} · ${count}대 정차"
}

/** One game in the left column: its icon tile, name and what it is. */
@Composable
private fun GameRow(kind: QuizKind, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(shape)
            .background(CdColors.White)
            .border(2.dp, if (selected) CdColors.Blue else CdColors.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) CdColors.Blue else CdColors.Gray100),
            contentAlignment = Alignment.Center,
        ) {
            Text(kind.icon, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) CdColors.White else CdColors.Ink2)
        }
        Column {
            Text(kind.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
            Text(kind.meta, fontSize = 12.sp, color = CdColors.Gray600)
        }
    }
}

/** "{상대}에 도전장 드랍", then "… 보냈어요 ✓" once sent (a silent social drop to that car or the crew). */
@Composable
private fun ChallengeButton(opponentName: String, sent: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (sent) CdColors.BlueLight else if (enabled) CdColors.Blue else CdColors.Gray300)
            .clickable(enabled = enabled && !sent, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (sent) "${opponentName}에 도전장 보냈어요 ✓" else "${opponentName}에 도전장 드랍",
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (sent) CdColors.BlueDark else CdColors.White, maxLines = 1,
        )
    }
}

/** "나 VS 상대" and the question counter. */
@Composable
private fun VsRow(opponent: Car, round: QuizRound) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // "나" as the design draws it (516): a 44 px teal circle with "A" in dark teal, 17/800.
        Box(Modifier.size(44.dp).clip(CircleShape).background(CdColors.Teal), contentAlignment = Alignment.Center) {
            Text("A", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TEAL_INK)
        }
        Text("나", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
        Text("VS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Gray400)
        CarBadge(opponent, size = 44) // 73 x 48, as in the design (516, 788)
        Text(opponent.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(
            if (round.finished) "끝" else "${round.number} / $QUIZ_ROUND 문제",
            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.BlueDark,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(CdColors.BlueLight).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** "누구랑 할까요": 크루 전체 or one car. */
@Composable
private fun OpponentPicker(others: List<Car>, selectedId: String?, onChoose: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("누구랑 할까요", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Gray600, modifier = Modifier.padding(end = 4.dp))
        OpponentChip(CREW_CAR, selected = selectedId == null) { onChoose(null) }
        others.forEach { car -> OpponentChip(car, selected = selectedId == car.id) { onChoose(car.id) } }
    }
}

@Composable
private fun OpponentChip(car: Car, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) CdColors.BlueLight else CdColors.Gray100)
            .border(2.dp, if (selected) CdColors.Blue else CdColors.Gray100, shape)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CarBadge(car, size = 24)
        Text(car.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) CdColors.BlueDark else CdColors.Ink2, maxLines = 1)
    }
}

/**
 * The question (tiles and four answers), what the pick was worth, "다음 문제", and the score at the end.
 * The answers sit at the bottom of the white card, as in the design (`margin-top: auto`, target 531).
 */
@Composable
private fun ColumnScope.QuizBody(
    quiz: AiViewModel.Loadable<QuizQuestion>,
    round: QuizRound,
    onAnswer: (String) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
) {
    if (round.finished) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("끝! $QUIZ_ROUND 문제 중 ${round.correct}개 맞혔어요", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
            Text("먼저 맞힌 차가 점수를 받아요", fontSize = 15.sp, color = CdColors.Gray600)
            PrimaryButton("다시 하기", onRestart)
        }
        return
    }
    when (quiz) {
        AiViewModel.Loadable.Loading -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            if (round.kind == QuizKind.LOCAL) Text("AI가 지금 위치로 문제를 만들고 있어요", fontSize = 14.sp, color = CdColors.Gray600)
        }
        is AiViewModel.Loadable.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(quiz.message, color = CdColors.OrangeDark)
            CdChip("다시 시도", onRetry)
        }
        is AiViewModel.Loadable.Ready -> {
            val question = quiz.value
            Text(question.question, fontSize = 15.sp, color = CdColors.Gray600, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                question.tiles.forEach { tile ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .height(104.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(CdColors.Gray100)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tile, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            question.answers.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { answer ->
                        AnswerButton(answer, round.picked, question.correctAnswer, Modifier.weight(1f)) { onAnswer(answer) }
                    }
                }
            }
            round.picked?.let { picked ->
                val right = picked == question.correctAnswer
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (right) "정답! 먼저 맞힌 차가 점수를 받아요" else "아쉬워요 · 정답은 ${question.correctAnswer}",
                        fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (right) CdColors.BlueDark else CdColors.Red,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(if (round.number == QUIZ_ROUND) "결과 보기" else "다음 문제", onNext)
                }
            }
        }
    }
}

/** An answer; once one is picked, the right answer turns blue and a wrong pick red, as in the design. */
@Composable
private fun AnswerButton(text: String, picked: String?, correct: String, modifier: Modifier, onClick: () -> Unit) {
    val (background, content, line) = when {
        picked == null -> Triple(CdColors.Gray100, CdColors.Ink, CdColors.Gray100)
        text == correct -> Triple(CdColors.BlueLight, CdColors.BlueDark, CdColors.Blue)
        text == picked -> Triple(CdColors.RedLight, CdColors.Red, CdColors.Alert)
        else -> Triple(CdColors.Gray50, CdColors.Gray450, CdColors.Gray50)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(70.dp)
            .clip(shape)
            .background(background)
            .border(2.dp, line, shape)
            .clickable(enabled = picked == null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = content)
    }
}

/** "크루 전체" as an opponent, drawn as a blue car like the design. */
private val CREW_CAR = Car(id = "crew", name = "크루 전체", color = CarColor.BLUE)

/** The dark teal text on the design's teal (#06302C), e.g. the "A" of "나". */
private val TEAL_INK = Color(0xFF06302C)
