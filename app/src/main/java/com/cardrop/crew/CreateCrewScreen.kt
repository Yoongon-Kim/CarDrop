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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Crew
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.InfoRow
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.core.ui.theme.TightLetterSpacing
import com.cardrop.navi.DestinationTrip

/** Board 01 크루 만들기 (방장) · Owner: Member A. */
@Composable
fun CreateCrewScreen(
    vm: CrewViewModel,
    onSearchDestination: () -> Unit,
    onJoinWithCode: () -> Unit,
    onDepart: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.ensureCrew() }
    val crew by vm.crew.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
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
            // Left, a little wider (flex 1.05): the destination and the invite code, 14 apart.
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
            }
            // Right (flex 0.95): the crew members, with the 출발 button at the bottom of the same card.
            MembersCard(crew, onDepart, modifier = Modifier.weight(0.95f).fillMaxHeight())
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
 * The crew's name (design line 111, decision D6): "일행 이름  강릉 여행  [바꾸기]". 바꾸기 turns the name into a
 * text box with 저장 and 취소; 저장 (or the keyboard's enter) gives the crew the new name ([onRename]).
 */
@Composable
private fun CrewNameRow(name: String, onRename: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("일행 이름", color = CdColors.Gray600, fontSize = 15.sp)
        if (!editing) {
            Text(name, color = CdColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            // The board's small grey button: 34 high, padding 12, radius 10, 13 bold.
            CdChip(
                "바꾸기",
                onClick = {
                    draft = name
                    editing = true
                },
                height = 34.dp,
                radius = 10.dp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                contentPadding = PaddingValues(horizontal = 12.dp),
            )
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
                placeholder = "일행 이름",
                maxLength = CREW_NAME_MAX_LENGTH,
                modifier = Modifier.weight(1f).focusRequester(focus),
                onDone = save,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Reads the code aloud, digit by digit ("사 팔 이, 구 일 칠").
            if (vm.canSpeak) {
                CdChip("말로 읽어주기", onClick = vm::speakCode, style = ChipStyle.Blue, enabled = crew != null)
            }
            CdChip("링크 문자로 보내기", onClick = onShare, enabled = crew != null)
        }
        shareStatus?.let { StatusText(it) }
    }
}

/** The cars of the crew, then the 출발 button and its hint at the bottom of the card (design line 143). */
@Composable
private fun MembersCard(crew: Crew?, onDepart: () -> Unit, modifier: Modifier = Modifier) {
    CdCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp)) {
        val cars = crew?.cars.orEmpty()
        Text("크루 멤버 · ${cars.count { it.connected }}/${cars.size}대 연결", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cars.forEach { car ->
                InfoRow(
                    title = car.name,
                    // The host (방장) is the car that made the crew, which isn't always this one: a car that
                    // joined with a code sees the host's row marked, not its own.
                    subtitle = if (car.id == crew?.hostCarId) "방장" else if (car.connected) "코드로 참여" else "초대 보냄 · 응답 기다리는 중",
                    leading = { CarBadge(car) },
                    trailing = {
                        if (car.connected) {
                            Pill("연결됨")
                        } else {
                            Pill("기다리는 중", background = CdColors.OrangeLight, color = CdColors.OrangeDark)
                        }
                    },
                )
            }
        }
        // The board pins the button to the bottom of the card (margin-top: auto): the Spacer takes the room left.
        Spacer(Modifier.weight(1f))
        PrimaryButton("다 모이면 출발하기", onClick = onDepart, modifier = Modifier.fillMaxWidth(), height = 66.dp)
        Text(
            "“출발하자”라고 말해도 돼요",
            color = CdColors.Gray600,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
