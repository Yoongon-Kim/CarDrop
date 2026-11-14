package com.cardrop.drop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardrop.core.model.Car
import com.cardrop.core.model.Drop
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CarBadge
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.theme.CdColors
import kotlinx.coroutines.delay

/**
 * Owner: Member A (2026-11-15). Another car's 게임 초대 ([GameInviteRules]) as a pop-up in the middle of the screen,
 * over whatever screen is open, when this car is in P: 거절 or 수락. [onAccept] opens the games (an omok 도전장 is then
 * accepted as soon as the game screen is ready); [onDecline] turns an omok 도전장 down. It closes by itself once the
 * minute is over: the sender then reads that nobody answered.
 */
@Composable
fun GameInvitePopup(drop: Drop, sender: Car?, onAccept: () -> Unit, onDecline: () -> Unit, onTimeUp: () -> Unit) {
    var now by remember(drop.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(drop.id) {
        while (GameInviteRules.open(drop, now)) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
        onTimeUp()
    }
    val secondsLeft = ((drop.createdAtMillis + GameInviteRules.ANSWER_MILLIS - now) / 1000).coerceAtLeast(0)
    val senderName = sender?.name ?: "일행"
    val what = if (GameInviteRules.omokGameId(drop) != null) "오목 도전장을 보냈어요" else "게임을 하자고 해요"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x52191F28)) // rgba(25,31,40,0.32), like board 13's pop-up
            // The dim backdrop takes the taps, so the screen behind isn't pressed by accident.
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
                Column(Modifier.weight(1f)) {
                    Text("게임 초대", color = CdColors.TealDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${senderName.particle("이", "가")} $what",
                        color = CdColors.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                    color = CdColors.Alert,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                "“${drop.title}”",
                color = CdColors.Ink2,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CdColors.TealLight)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
            Text("정차 중에만 할 수 있어요 · 출발하면 게임은 멈춰요", color = CdColors.Gray600, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("거절", onClick = onDecline, height = 56.dp, radius = 18.dp, fontSize = 17.sp)
                PrimaryButton(
                    "수락하고 게임으로",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    containerColor = CdColors.Teal,
                    height = 56.dp,
                    radius = 18.dp,
                    fontSize = 17.sp,
                )
            }
        }
    }
}
