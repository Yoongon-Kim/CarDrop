package com.cardrop.crew

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.Crew
import com.cardrop.core.model.VehicleSnapshot
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.navi.DestinationTrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Board 01 크루 만들기 (방장) · Owner: Member A. */
@Composable
fun CreateCrewScreen(
    vm: CrewViewModel,
    /** This car's fuel, for the host's row ("방장 · 연료 64%"). */
    vehicle: StateFlow<VehicleSnapshot>,
    onSearchDestination: () -> Unit,
    onJoinWithCode: () -> Unit,
    onDepart: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.ensureCrew() }
    val crew by vm.crew.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val car by vehicle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shareStatus by remember { mutableStateOf<String?>(null) }

    ScreenColumn {
        // Design lines 95-97: eyebrow 15px, title 32/800 with letter-spacing -0.02em.
        ScreenHeader(
            eyebrow = "출발 전 · 정차 중",
            title = "함께 갈 차를 모아요",
            eyebrowSize = 15.sp,
            titleSize = 32.sp,
            titleLetterSpacing = TightLetterSpacing,
        ) {
            // White on the grey page (design line 99); the grey SecondaryButton didn't show on it.
            PrimaryButton(
                "받은 코드로 참여",
                onClick = onJoinWithCode,
                containerColor = CdColors.White,
                contentColor = CdColors.Ink2,
                height = 50.dp,
                radius = 14.dp,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 18.dp,
            )
        }
        // Design lines 101-146: two columns 16 apart, as tall as the rest of the screen (weight(1f) = flex: 1).
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Left, a little wider (flex 1.05): the destination, the invite code and the saved groups, 14 apart.
            Column(modifier = Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DestinationCard(crew, trip, onSearchDestination, onRename = vm::renameCrew)
                InviteCodeCard(vm, crew, onShare = {
                    val c = crew ?: return@InviteCodeCard
                    // Opens the messaging app with the invite text filled in.
                    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                        .putExtra("sms_body", CrewVoice.inviteMessage(c.name, c.code))
                    shareStatus = try {
                        context.startActivity(sms)
                        null
                    } catch (e: ActivityNotFoundException) {
                        "이 기기에는 문자 앱이 없어요 · 코드를 말로 알려 주세요"
                    }
                }, shareStatus = shareStatus)
                SavedGroupsCard()
            }
            // Right (flex 0.95): the crew members, with the 출발 button at the bottom of the same card.
            MembersCard(
                crew,
                myCarId = vm.myCarId,
                myFuelPercent = car.fuelPercent,
                onDepart = onDepart,
                modifier = Modifier.weight(0.95f).fillMaxHeight(),
            )
        }
    }
}

/** The crew's destination (design lines 103-109): a label, then the place as a tile that opens board 02. */
@Composable
private fun DestinationCard(
    crew: Crew?,
    trip: DestinationTrip?,
    onSearchDestination: () -> Unit,
    onRename: (String) -> Unit,
) {
    CdCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 12.dp) {
        Text("목적지", color = CdColors.Gray600, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        // The tile: a blue edge on light blue, the blue pin square, the place, and "검색" at the right.
        val shape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(shape)
                .background(CdColors.BlueTint)
                .border(2.dp, CdColors.Blue, shape)
                .clickable(role = Role.Button, onClick = onSearchDestination)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(CdColors.Blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CdIcons.Pin, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    crew?.destination?.name ?: "목적지를 골라 주세요",
                    color = CdColors.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // "강원 강릉시 · 해수욕장 · 약 62km · 1시간 5분": the road distance and time come from the navigator.
                val detail = listOfNotNull(crew?.destination?.detail?.takeIf { it.isNotBlank() }, trip?.text)
                if (detail.isNotEmpty()) {
                    Text(detail.joinToString(" · "), color = CdColors.Gray600, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("검색", color = CdColors.BlueDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        // "왼쪽 내비": the navigator is the left third of the screen.
        Text(
            "입력하면 왼쪽 내비 목적지에 바로 반영돼요 · 일행 모두 같은 목적지로",
            color = CdColors.BlueDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        if (crew != null) CrewNameRow(crew.name, onRename)
    }
}

/**
 * The crew's name (design line 111, decision D6): "일행 이름  강릉 여행  [바꾸기]". 바꾸기 turns the name into an
 * empty text box (the old name in grey) with 저장 and 취소; 저장 (or the keyboard's enter) gives the crew the
 * typed name ([onRename]).
 */
@Composable
private fun CrewNameRow(name: String, onRename: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("일행 이름", color = CdColors.Gray600, fontSize = 15.sp)
        if (!editing) {
            Text(name, color = CdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            SmallGreyButton("바꾸기", onClick = {
                // An empty box with the old name in grey: the text box's cursor would start in front of the
                // old name, so typing straight away gives the new one instead.
                draft = ""
                editing = true
            })
        } else {
            val save = {
                onRename(draft)
                editing = false
            }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() } // the keyboard comes up for the new name
            CdTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = name,
                maxLength = CREW_NAME_MAX_LENGTH,
                modifier = Modifier.weight(1f).focusRequester(focus),
                onDone = save,
                onClear = { draft = "" },
            )
            CdChip("저장", onClick = save, style = ChipStyle.Primary, enabled = draft.isNotBlank(), height = 54.dp)
            CdChip("취소", onClick = { editing = false }, height = 54.dp)
        }
    }
}

/** A crew name longer than this is cut off. */
private const val CREW_NAME_MAX_LENGTH = 20

/** The join code, and two ways to tell it to the others: read aloud, or a text message ([onShare]). */
@Composable
private fun InviteCodeCard(vm: CrewViewModel, crew: Crew?, onShare: () -> Unit, shareStatus: String?) {
    // Design lines 113-114: radius 24, padding 22 / 24, 14 apart; the code big and centred, with no label.
    CdCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp), gap = 14.dp) {
        Text(
            crew?.code?.chunked(3)?.joinToString(" ") ?: "------",
            color = CdColors.Ink,
            fontSize = 60.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.08.em,
            lineHeight = 60.sp, // the board's line-height: 1
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // Design lines 115-118: centred, 8 apart, 46 high, 15; light blue 800 and grey 700.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            // Reads the code aloud, digit by digit ("사 팔 이, 구 일 칠").
            if (vm.canSpeak) {
                CdChip("말로 읽어주기", onClick = vm::speakCode, style = ChipStyle.Blue, enabled = crew != null, height = 46.dp, fontSize = 15.sp)
            }
            CdChip(
                "링크 문자로 보내기",
                onClick = onShare,
                enabled = crew != null,
                height = 46.dp,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        shareStatus?.let { StatusText(it, Modifier.align(Alignment.CenterHorizontally)) }
    }
}

/**
 * Tier C, fixed demo data: the app keeps no saved groups yet, so these are the board's two (line 123-124).
 * Picking one only marks it on screen; no invites are sent.
 */
private val savedGroups = listOf("가족 · 4대", "주말 드라이브 동호회 · 3대")

/** What the buttons that aren't built yet say (tier C: + 그룹 등록, 초대). */
private const val NOT_READY = "아직 준비 중이에요"

/**
 * 저장된 그룹 · 한 번에 초대 (design lines 120-126): the header with "+ 그룹 등록", then the groups as chips; the
 * picked one has a blue edge on light blue.
 */
@Composable
private fun SavedGroupsCard() {
    var picked by rememberSaveable { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    CdCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp), gap = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("저장된 그룹 · 한 번에 초대", color = CdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            SmallGreyButton("+ 그룹 등록", onClick = { status = NOT_READY })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            savedGroups.forEachIndexed { i, group ->
                GroupChip(group, picked = i == picked, onClick = { picked = i })
            }
        }
        status?.let { StatusText(it) }
    }
}

/** A saved group (design lines 123-124): 46 high, radius 14, 14px; picked = 2 px blue edge on light blue. */
@Composable
private fun GroupChip(text: String, picked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(shape)
            .background(if (picked) CdColors.BlueTint else CdColors.Gray100)
            .border(2.dp, if (picked) CdColors.Blue else CdColors.Gray100, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (picked) CdColors.BlueDark else CdColors.Ink2,
            fontSize = 14.sp,
            fontWeight = if (picked) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The board's small grey button (34 high, padding 12, radius 10, 13 bold): 바꾸기, + 그룹 등록. */
@Composable
private fun SmallGreyButton(text: String, onClick: () -> Unit) {
    CdChip(
        text,
        onClick = onClick,
        height = 34.dp,
        radius = 10.dp,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        contentPadding = PaddingValues(horizontal = 12.dp),
    )
}

/** The cars of the crew, then the 출발 button and its hint at the bottom of the card (design line 143). */
@Composable
private fun MembersCard(
    crew: Crew?,
    myCarId: String,
    myFuelPercent: Int?,
    onDepart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // When this screen saw each car connect, for "코드로 참여 · 방금 전". It starts over when the screen opens:
    // for the cars already in, it doesn't know when they came, so their rows show no time.
    var joinTimes by remember { mutableStateOf(JoinTimes()) }
    LaunchedEffect(crew) { crew?.let { joinTimes = joinTimes.next(it, System.currentTimeMillis()) } }
    // The clock for "방금 전" -> "1분 전": the rows are drawn again every 15 s.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    // Design line 128: radius 26, padding 20 / 22, only 2 between the card's parts (the rows have their own room).
    CdCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp), gap = 2.dp) {
        val cars = crew?.cars.orEmpty()
        // "크루 멤버" at the left, "3 / 4대 연결" in blue at the right, on the same baseline, 4 above the rows.
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("크루 멤버", color = CdColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.alignByBaseline())
            Text(
                "${cars.count { it.connected }} / ${cars.size}대 연결",
                color = CdColors.BlueDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alignByBaseline(),
            )
        }
        cars.forEach { car ->
            // The host (방장) is the car that made the crew, which isn't always this one: a car that joined with
            // a code sees the host's row marked, not its own.
            val meta = MemberRules.meta(
                car,
                hostCarId = crew?.hostCarId.orEmpty(),
                myCarId = myCarId,
                myFuelPercent = myFuelPercent,
                joinedAgo = joinTimes.agoText(car.id, now),
            )
            MemberRow(car, meta)
        }
        PastCars()
        // The board pins the button to the bottom of the card (margin-top: auto): the Spacer takes the room left.
        Spacer(Modifier.weight(1f))
        // Only the host (방장) starts the trip; the other cars go with it. Once it started, the button leads to board 04.
        val isHost = crew != null && crew.hostCarId == myCarId
        val started = crew?.isStarted == true
        val deadline = crew?.joinDeadlineMillis
        if (crew != null && !started && deadline != null) {
            AutoStartCountdown(deadline, missing = JoinRules.missingCars(crew).size)
        }
        val hostName = crew?.cars?.firstOrNull { it.id == crew.hostCarId }?.name ?: "방장 차"
        val hostCanStart = !started && (crew == null || isHost)
        PrimaryButton(
            when {
                started -> "주행 화면으로"
                hostCanStart -> "다 모이면 출발하기"
                else -> "${hostName.particle("이", "가")} 출발하면 같이 출발해요"
            },
            onClick = onDepart,
            enabled = crew != null && (started || isHost),
            modifier = Modifier.fillMaxWidth(),
            height = 66.dp,
        )
        if (hostCanStart) {
            Text(
                "“출발하자”라고 말해도 돼요",
                color = CdColors.Gray600,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), // the board's margin-top: 6px
            )
        }
    }
}

/**
 * Over the 출발 button until the trip starts (an app extra, the board has no timer): "2:41 뒤 자동 출발", with
 * "· 안 온 차는 빼고 가요" while [missing] cars haven't joined. At [deadlineMillis] the trip starts by itself.
 * Plain text, not StatusText: TalkBack would read it out every second.
 */
@Composable
private fun AutoStartCountdown(deadlineMillis: Long, missing: Int) {
    val now by produceState(System.currentTimeMillis(), deadlineMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(
        JoinRules.autoStartText(deadlineMillis - now, missing),
        color = if (missing > 0) CdColors.OrangeDark else CdColors.BlueDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

/** A car this one drove with before (tier C, fixed demo data: the app keeps no trip history yet). */
private data class PastCar(val initial: String, val name: String, val trip: String)

private val pastCars = listOf(PastCar("민", "민수 차", "9월 · 양양 여행"))

/**
 * 예전에 함께 달린 차 (design lines 137-142): the label, then each car with a grey initial circle, its name and
 * when it came along, and 초대. 초대 isn't built yet: it says "아직 준비 중이에요".
 */
@Composable
private fun PastCars() {
    var status by remember { mutableStateOf<String?>(null) }
    // margin-top: 12px on the board, on top of the card's 2.
    Text(
        "예전에 함께 달린 차",
        color = CdColors.Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 12.dp),
    )
    pastCars.forEach { car ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(CdColors.Gray100),
                contentAlignment = Alignment.Center,
            ) {
                Text(car.initial, color = CdColors.Gray600, fontWeight = FontWeight.ExtraBold)
            }
            // "민수 차" 15 bold, then " 9월 · 양양 여행" in small grey letters, on one line.
            Text(
                buildAnnotatedString {
                    append(car.name)
                    withStyle(SpanStyle(color = CdColors.Gray600, fontSize = 12.sp, fontWeight = FontWeight.Normal)) {
                        append(" ${car.trip}")
                    }
                },
                color = CdColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CdChip(
                "초대",
                onClick = { status = NOT_READY },
                style = ChipStyle.Blue,
                height = 42.dp,
                radius = 12.dp,
                contentPadding = PaddingValues(horizontal = 18.dp),
            )
        }
    }
    status?.let { StatusText(it) }
}

/**
 * One car of the crew (design lines 131-134): the badge, the name (16/800) over the grey line (13), and
 * its state at the right; 8 above and below, 12 apart, a thin grey line under it.
 */
@Composable
private fun MemberRow(car: Car, meta: String) {
    // One item with its line, so the card's 2 dp gap comes between the rows, not between a row and its line.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CarBadge(car)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(car.name, color = CdColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(meta, color = CdColors.Gray600, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // The board's status pill: 13 extra bold, padding 6 / 10 (line 639).
            val pillPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            if (car.connected) {
                Pill("연결됨", fontSize = 13.sp, padding = pillPadding)
            } else {
                Pill("기다리는 중", background = CdColors.OrangeLight, color = CdColors.OrangeDark, fontSize = 13.sp, padding = pillPadding)
            }
        }
        // The board's border-bottom: 1px solid #F2F4F6.
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(CdColors.Gray100))
    }
}
