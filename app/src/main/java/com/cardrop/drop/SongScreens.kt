package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.text.particle
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CrewViewModel

/**
 * Board 12 노래 드랍 보내기 · Owner: Member A (target lines 470-486).
 * What this car "plays" now, the four songs (the first one picked), and 노래 드랍하기, which sends the picked song to
 * [toCarIds] (empty = the whole crew) as a SOCIAL drop. No sound: nothing really plays ([SongPlayer]).
 * The target's top card shows the picked song under "내 차에서 지금 재생 중"; here it shows what plays now, which the
 * list also marks ("· 지금 재생 중").
 */
@Composable
fun SongSendScreen(
    vm: CrewViewModel,
    player: SongPlayer,
    toCarIds: List<String>,
    onBack: () -> Unit,
    onSent: () -> Unit,
) {
    val crew by vm.crew.collectAsStateWithLifecycle()
    val playing by player.nowPlaying.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf(SongRules.SONGS.first()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val receivers = crew?.cars.orEmpty().count { !it.isMe && (toCarIds.isEmpty() || it.id in toCarIds) }
    val label = crew?.recipientLabel(toCarIds, vm.myCarId) ?: "크루 전체"

    ScreenColumn(
        bottom = {
            error?.let { ErrorText(it) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "받는 차 · $label (${receivers}대) · 각자 받을지 정해요",
                    color = CdColors.Gray600,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "노래 드랍하기",
                    enabled = !sending,
                    onClick = {
                        sending = true
                        error = null
                        vm.sendSong(picked, toCarIds) { failed ->
                            sending = false
                            if (failed == null) onSent() else error = failed
                        }
                    },
                    containerColor = CdColors.Pink,
                    height = 70.dp,
                    fontSize = 18.sp,
                    horizontalPadding = 34.dp,
                )
            }
        },
    ) {
        // Header: 뒤로, the title and its grey line, 12 apart.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            Column {
                Text("노래 드랍", color = CdColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("친구가 받으면 친구의 Apple Music 재생목록에서 이 곡으로 바뀌어요", color = CdColors.Gray600, fontSize = 14.sp)
            }
        }
        // What plays now: a 76 px pink tile with the note, then the label and the song (22 / 800).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CdColors.White)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MusicTile(size = 76.dp, radius = 18.dp, icon = 34.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("내 차에서 지금 재생 중 · Apple Music", color = CdColors.Pink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("${playing.name} · ${playing.artist}", color = CdColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        // The four songs in a white card, a grey line under each.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(CdColors.White)
                .padding(horizontal = 22.dp, vertical = 8.dp),
        ) {
            SongRules.SONGS.forEachIndexed { i, song ->
                SongRow(
                    number = i + 1,
                    song = song,
                    playing = song.id == playing.id,
                    picked = song.id == picked.id,
                    onPick = { picked = song },
                )
            }
        }
    }
}

/** One song: a 48 px number tile (pink when picked), the name and artist, and "보낼 곡" when picked. */
@Composable
private fun SongRow(number: Int, song: Song, playing: Boolean, picked: Boolean, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = picked, role = Role.RadioButton, onClick = onPick)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(CdColors.Gray100, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (picked) CdColors.Pink else CdColors.PinkLight),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = if (picked) CdColors.White else CdColors.Pink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(song.name, color = CdColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(song.artist + if (playing) " · 지금 재생 중" else "", color = CdColors.Gray600, fontSize = 13.sp)
        }
        if (picked) SmallTag("보낼 곡", CdColors.White, CdColors.Pink, fontSize = 13.sp, radius = 999.dp, horizontal = 10.dp, vertical = 5.dp)
    }
}

/**
 * Board 13 노래 받기 · Owner: Member A (target lines 299-325): another car's song drop, a pop-up over board 04.
 * 괜찮아요 closes it; 받아서 바로 듣기 makes the song the one playing ([SongPlayer], no sound) and shows the playlist:
 * 지금 재생 the song, 다음 what this car was playing. 확인 closes it. A song this build doesn't know closes at once.
 */
@Composable
fun SongGetDialog(drop: Drop, sender: Car?, player: SongPlayer, onClose: () -> Unit) {
    val song = SongRules.songOf(drop)
    if (song == null) {
        LaunchedEffect(drop.id) { onClose() }
        return
    }
    val senderName = sender?.name ?: "일행"
    // Set by 받아서 바로 듣기: what plays after the song.
    var next by remember(drop.id) { mutableStateOf<Song?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x52191F28)) // rgba(25,31,40,0.32)
            // The dim backdrop takes the taps, so board 04 behind it isn't pressed by accident.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(CdColors.White)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sender != null) CarBadge(sender, size = 48, bob = false)
                Column {
                    Text("소셜 드랍 · 노래", color = CdColors.Pink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${senderName.particle("이", "가")} 노래를 보냈어요",
                        color = CdColors.Ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CdColors.PinkLight)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MusicTile(size = 64.dp, radius = 16.dp, icon = 28.dp)
                Column {
                    Text(song.name, color = CdColors.Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${song.artist} · ${song.length}", color = CdColors.Gray600, fontSize = 14.sp)
                }
            }
            val after = next
            if (after == null) {
                Text("받으면 지금 듣는 Apple Music 재생목록에서 이 곡으로 바뀌어요", color = CdColors.Gray600, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("괜찮아요", onClick = onClose, modifier = Modifier.weight(1f), height = 60.dp, radius = 18.dp, fontSize = 16.sp)
                    PrimaryButton(
                        text = "받아서 바로 듣기",
                        onClick = { next = player.playNow(song) },
                        modifier = Modifier.weight(2f),
                        containerColor = CdColors.Pink,
                        height = 60.dp,
                        radius = 18.dp,
                        fontSize = 17.sp,
                    )
                }
            } else {
                Playlist(song, senderName, after)
                PrimaryButton("확인", onClick = onClose, modifier = Modifier.fillMaxWidth(), height = 56.dp, radius = 18.dp, fontSize = 16.sp)
            }
        }
    }
}

/** After 받아서 바로 듣기: the playlist with the song playing now and what comes next. */
@Composable
private fun Playlist(song: Song, senderName: String, next: Song) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CdColors.Gray100, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Apple Music · ${SongRules.PLAYLIST} 재생목록", color = CdColors.Gray600, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CdColors.PinkLight)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallTag("지금 재생", CdColors.White, CdColors.Pink)
            Text(
                buildAnnotatedString {
                    append(song.name)
                    withStyle(SpanStyle(fontSize = 12.sp, color = CdColors.Gray600, fontWeight = FontWeight.Medium)) {
                        append(" · ${senderName.particle("이", "가")} 보냄")
                    }
                },
                color = CdColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SmallTag("다음", CdColors.Gray600, CdColors.Gray100)
            Text("${next.name} · 원래 듣던 곡 이어서", color = CdColors.Gray600, fontSize = 15.sp)
        }
    }
}

/** The pink square with the note (boards 12 and 13). */
@Composable
private fun MusicTile(size: Dp, radius: Dp, icon: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(radius)).background(CdColors.Pink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CdIcons.Music, contentDescription = null, tint = CdColors.White, modifier = Modifier.size(icon))
    }
}

/** A small label: "보낼 곡", "지금 재생", "다음". */
@Composable
private fun SmallTag(
    text: String,
    color: Color,
    background: Color,
    fontSize: TextUnit = 12.sp,
    radius: Dp = 6.dp,
    horizontal: Dp = 6.dp,
    vertical: Dp = 2.dp,
) {
    Text(
        text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(radius))
            .background(background)
            .padding(horizontal = horizontal, vertical = vertical),
    )
}
