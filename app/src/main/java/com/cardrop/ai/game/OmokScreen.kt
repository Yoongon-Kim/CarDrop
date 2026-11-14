package com.cardrop.ai.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.ai.AiViewModel
import com.cardrop.ai.QuizKind
import com.cardrop.ai.QuizScreen
import com.cardrop.ai.gameEyebrow
import com.cardrop.core.model.Drop
import com.cardrop.core.model.Place
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.drive.gamesOpen
import androidx.compose.runtime.mutableLongStateOf
import com.cardrop.core.model.Gear
import com.cardrop.navi.GpsFix
import kotlinx.coroutines.delay

/**
 * Board 15. Until the car is parked ([gamesOpen]: P, and the GPS no longer says it moves) nothing of the games shows,
 * only a card that says so: no board, no quiz, no 도전장. [fix] = this car's latest GPS reading. A game in progress is
 * paused meanwhile (the board counts as not visible) and comes back at the next stop.
 */
@Composable
fun GamesScreen(vm: AiViewModel, omok: OmokCoordinator, stop: Place?, drops: List<Drop>, fix: GpsFix?) {
    var tab by rememberSaveable { mutableStateOf("omok") }
    val openRequests by omok.openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openRequests) { if (openRequests > 0) tab = "omok" }
    val snapshot by vm.vehicleSnapshot.collectAsStateWithLifecycle()
    // Ticks every second while this screen is open, so the check runs again and an old GPS reading stops counting.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    // The time is read right here, not kept from the last tick: a GPS reading that came after the tick would look
    // like it is from the future.
    val open = remember(snapshot.gear, fix, tick) { gamesOpen(snapshot.gear, fix, System.currentTimeMillis()) }
    DisposableEffect(omok, tab, open) {
        val owner = Any()
        omok.onGameVisible(owner, open && tab == "omok")
        onDispose { omok.onGameVisible(owner, false) }
    }
    if (!open) {
        GamesClosed(parking = snapshot.gear == Gear.PARK)
        return
    }
    if (tab == "omok") OmokScreen(omok, stop, drops) { kind -> vm.chooseQuiz(kind); tab = "quiz" }
    else QuizScreen(vm, stop, drops, onOmok = { tab = "omok" })
}

/** What board 15 shows until the car is parked: the title, the "P" pill and one card. No game can be seen or started. */
@Composable
private fun GamesClosed(parking: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader("주행 중", "게임은 정차 중에만 열려요") { Pill("P · 정차 중에만 열려요") }
        CdCard(Modifier.fillMaxWidth()) {
            // In P while the car is still rolling: it opens by itself in a moment.
            Text(
                if (parking) "차가 멈추면 열려요" else "주행 중에는 게임을 열 수 없어요",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CdColors.Ink,
            )
            Text("안전한 곳에 세우고 P로 바꾸면 열려요 · 하던 게임은 그대로 이어서 해요", fontSize = 15.sp, color = CdColors.Gray600)
        }
    }
}

@Composable
private fun OmokScreen(omok: OmokCoordinator, stop: Place?, drops: List<Drop>, onQuiz: (QuizKind) -> Unit) {
    val games by omok.games.collectAsStateWithLifecycle()
    val now by omok.clock.collectAsStateWithLifecycle()
    val crew by omok.currentCrew.collectAsStateWithLifecycle()
    val pending by omok.pending.collectAsStateWithLifecycle()
    val error by omok.error.collectAsStateWithLifecycle()
    val localReady by omok.localReady.collectAsStateWithLifecycle()
    val connected by omok.transport.connected.collectAsStateWithLifecycle()
    val mine = games.values.filter { omok.myCarId in it.players }
    val game = mine.filter { it.busy(now) }.maxByOrNull { it.createdAt } ?: mine.maxByOrNull { it.createdAt }
    val busy = game?.busy(now) == true
    val others = crew?.cars.orEmpty().filter { it.id != omok.myCarId }
    var selectedOpponent by rememberSaveable(crew?.code) { mutableStateOf<String?>(null) }
    val opponentId = if (busy) game?.other(omok.myCarId) else selectedOpponent?.takeIf { id -> others.any { it.id == id && it.connected } }
        ?: others.firstOrNull { it.connected }?.id
    val opponent = others.firstOrNull { it.id == opponentId }
    val boardOpponent = others.firstOrNull { it.id == game?.other(omok.myCarId) } ?: opponent
    val canPlay = localReady && connected && !pending && game?.phase == OmokPhase.PLAYING &&
        game.turnId == omok.myCarId && game.ready(now)
    val tokens = game?.tokens().orEmpty()
    var selection by remember(game?.id, game?.revision, tokens, canPlay) { mutableStateOf<OmokSelection?>(null) }
    var confirmingResign by remember(game?.id, game?.revision) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(gameEyebrow(stop, crew, omok.myCarId, drops), "일행이랑 한 판 할까요?") { Pill("P · 정차 중에만 열려요") }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameTile("●", "오목", "5개 먼저 잇기 · 1:1", true) {}
                QuizKind.entries.forEach { kind -> GameTile(kind.icon, kind.title, kind.meta, false) { onQuiz(kind) } }
                Text("게임 상대", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CdColors.Gray600, modifier = Modifier.padding(top = 10.dp))
                others.forEach { car ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (car.id == opponentId) CdColors.BlueLight else CdColors.White)
                            .clickable(enabled = !busy && car.connected && !pending) { selectedOpponent = car.id }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CarBadge(car, size = 32, bob = false)
                        Column {
                            Text(car.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CdColors.Ink)
                            Text(if (car.connected) if (car.isBot) "연습 봇" else "연결됨" else "연결 대기", fontSize = 12.sp, color = CdColors.Gray600)
                        }
                    }
                }
                if (!busy) PrimaryButton(
                    if (game?.terminal == true) "새 도전장 보내기" else "도전장 보내기",
                    { opponentId?.let(omok::invite) }, Modifier.fillMaxWidth(),
                    enabled = localReady && !pending && opponentId != null,
                    fontSize = 17.sp,
                )
                Text("13×13 · 금수 없음\n5개 이상 연결하면 승리해요", fontSize = 13.sp, lineHeight = 20.sp, color = CdColors.Gray600)
                Text("D로 바꾸거나 게임 화면을 벗어나면 대국이 멈춰요. 두 차량이 돌아오면 이어서 둬요.", fontSize = 13.sp, lineHeight = 20.sp, color = CdColors.Gray600)
            }
            CdCard(Modifier.weight(1f).fillMaxHeight(), gap = 8.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("나", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
                    Text(if (game == null || game.blackId == omok.myCarId) "● 흑" else "○ 백", color = CdColors.Gray600, fontSize = 15.sp)
                    Text("VS", color = CdColors.Gray400, fontSize = 16.sp)
                    boardOpponent?.let { CarBadge(it, size = 36, bob = false) }
                    Text(boardOpponent?.name ?: "상대를 골라 주세요", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CdColors.Ink)
                    Spacer(Modifier.weight(1f))
                    if (game != null) Pill("${game.moves.size}수")
                }
                val status = when {
                    crew == null -> "크루에 먼저 참여해 주세요"
                    !crew!!.isStarted -> "크루가 출발하면 게임을 초대할 수 있어요"
                    !connected -> "연결이 끊겼어요 · 연결되면 현재 보드를 확인해요"
                    !localReady -> "P로 정차하고 게임 화면을 열어 주세요"
                    game == null -> "상대 차량에 도전장을 보내 주세요"
                    // No answer within the minute (Member A, 2026-11-15): the sender reads that nobody answered.
                    game.phase == OmokPhase.INVITED && !game.invited(now) ->
                        if (game.blackId == omok.myCarId) "1분 동안 응답이 없었어요 · 새 도전장을 보내 주세요" else "도전장이 만료됐어요"
                    game.phase == OmokPhase.INVITED -> if (game.whiteId == omok.myCarId) "오목 도전장이 왔어요" else "상대의 수락을 기다리고 있어요"
                    game.phase == OmokPhase.FINISHED -> if (game.winnerId == null) "무승부예요" else if (game.winnerId == omok.myCarId) "이겼어요! · ${game.reason}" else "상대가 이겼어요 · ${game.reason}"
                    game.phase == OmokPhase.DECLINED -> "상대가 도전장을 거절했어요"
                    game.phase == OmokPhase.CANCELLED -> "도전장을 취소했어요"
                    game.phase == OmokPhase.EXPIRED ->
                        if (game.blackId == omok.myCarId) "1분 동안 응답이 없었어요" else "도전장이 만료됐어요"
                    !game.ready(now) -> "상대의 정차·연결·게임 화면을 기다려요"
                    game.turnId == omok.myCarId -> "내 차례 · 놓을 자리를 고르고 착수를 눌러 주세요"
                    else -> "상대 차례예요"
                }
                Text(status, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CdColors.BlueDark)
                if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(error!!, Modifier.weight(1f), fontSize = 14.sp, color = CdColors.Red)
                    CdChip("확인", omok::clearError, height = 34.dp)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight, 470.dp)
                    OmokBoard(game, selection?.cell, canPlay, Modifier.size(side)) { cell ->
                        if (game != null) selection = OmokSelection(game.id, game.revision, game.tokens(), cell)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        game?.phase == OmokPhase.INVITED && game.invited(now) -> {
                            if (game.whiteId == omok.myCarId) PrimaryButton("수락하고 시작", { omok.accept(game) }, Modifier.weight(1f), enabled = localReady && !pending, height = 52.dp, fontSize = 17.sp)
                            CdChip(if (game.blackId == omok.myCarId) "도전장 취소" else "거절", { omok.close(game) }, enabled = !pending)
                        }
                        game?.phase == OmokPhase.PLAYING -> {
                            val target = selection?.cell?.let { "${('A'.code + it % OMOK_SIZE).toChar()}${it / OMOK_SIZE + 1}에 착수" } ?: "자리를 골라 주세요"
                            PrimaryButton(if (pending) "확인 중…" else target, { selection?.let(omok::place) }, Modifier.weight(1f), enabled = canPlay && selection != null, height = 52.dp, fontSize = 17.sp)
                            CdChip(if (confirmingResign) "기권 확정" else "기권", {
                                if (confirmingResign) omok.close(game) else confirmingResign = true
                            }, style = if (confirmingResign) ChipStyle.Danger else ChipStyle.Gray, enabled = !pending)
                            if (confirmingResign) CdChip("계속 두기", { confirmingResign = false })
                        }
                        else -> Text("흑이 먼저 시작해요 · 새 판은 상대의 수락 후 시작해요", color = CdColors.Gray600, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(icon: String, title: String, meta: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(22.dp)).background(CdColors.White)
            .border(2.dp, if (selected) CdColors.Blue else CdColors.White, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (selected) CdColors.Blue else CdColors.Gray100), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp, color = if (selected) CdColors.White else CdColors.Ink)
        }
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.Ink)
            Text(meta, fontSize = 12.sp, color = CdColors.Gray600)
        }
    }
}

@Composable
private fun OmokBoard(game: OmokGame?, selected: Int?, enabled: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    val board = remember(game?.moves) { game?.board() ?: IntArray(OMOK_CELLS) }
    val winning = remember(game?.moves) { game?.let(OmokRules::winningLine).orEmpty() }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5D9A6))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.width / OMOK_SIZE
            val first = step / 2
            val last = size.width - first
            for (i in 0 until OMOK_SIZE) {
                val pos = first + i * step
                drawLine(Color(0xFF977845), Offset(first, pos), Offset(last, pos), 1.2f)
                drawLine(Color(0xFF977845), Offset(pos, first), Offset(pos, last), 1.2f)
            }
            for (r in listOf(3, 6, 9)) for (c in listOf(3, 6, 9)) if ((r == 6) == (c == 6)) {
                drawCircle(Color(0xFF977845), step * .06f, Offset(first + c * step, first + r * step))
            }
            board.forEachIndexed { cell, stone ->
                val point = Offset(first + cell % OMOK_SIZE * step, first + cell / OMOK_SIZE * step)
                if (stone != 0) {
                    drawCircle(Brush.radialGradient(if (stone == 1) listOf(Color(0xFF666666), Color(0xFF101010)) else listOf(Color.White, Color(0xFFD5D5D5)), point - Offset(step * .12f, step * .12f), step * .6f), step * .42f, point)
                    if (cell in winning) drawCircle(CdColors.Blue, step * .44f, point, style = Stroke(step * .08f))
                    else if (cell == game?.moves?.lastOrNull()) drawCircle(if (stone == 1) Color.White else CdColors.Blue, step * .09f, point)
                }
                if (cell == selected) {
                    drawCircle(CdColors.Blue.copy(alpha = .3f), step * .4f, point)
                    drawCircle(CdColors.Blue, step * .4f, point, style = Stroke(step * .07f))
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            repeat(OMOK_SIZE) { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(OMOK_SIZE) { col ->
                        val cell = row * OMOK_SIZE + col
                        val label = "오목 ${('A'.code + col).toChar()}${row + 1} " + when (board[cell]) { 1 -> "흑돌"; 2 -> "백돌"; else -> "빈자리" }
                        Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label }
                            .clickable(enabled = enabled && board[cell] == 0, role = Role.Button) { onSelect(cell) })
                    }
                }
            }
        }
    }
}

