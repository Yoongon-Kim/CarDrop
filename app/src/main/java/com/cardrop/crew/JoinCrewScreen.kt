package com.cardrop.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.text.withRo
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.ErrorText
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenColumn
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.SecondaryButton
import com.cardrop.core.ui.TodoNote
import com.cardrop.core.ui.theme.CdColors

/** Board 03 코드로 참여 · Owner: Member A. With the fake, code 482917 always works. */
@Composable
fun JoinCrewScreen(vm: CrewViewModel, onBack: () -> Unit, onJoined: () -> Unit) {
    // rememberSaveable: the digits stay when Android rebuilds the screen (e.g. day / night theme).
    var code by rememberSaveable { mutableStateOf("") }
    val join by vm.join.collectAsStateWithLifecycle()
    // Opened with empty boxes: forget a crew found earlier, so the right half matches the boxes.
    LaunchedEffect(Unit) { if (code.isEmpty()) vm.clearJoin() }
    // Every change of the code goes through here: the crew is looked up at the 6th digit.
    val changeCode: (String) -> Unit = { digits ->
        code = digits
        if (digits.length == JoinRules.CODE_LENGTH) vm.joinCrew(digits) else vm.clearJoin()
    }

    ScreenColumn {
        // Design lines 177-179: the back button, then the title with the grey line under it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            ScreenHeader(
                eyebrow = null,
                title = "코드로 참여",
                subtitle = "주행 중엔 “코드 482917”처럼 말해서 넣어요",
                gap = 0.dp,
                modifier = Modifier.weight(1f),
            )
        }
        // Design lines 181-197: the code on the left half, the crew found with it on the right half,
        // 16 apart. weight(1f) makes the two cards fill the rest of the screen's height (the design's flex: 1).
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CdCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(22.dp),
                gap = 16.dp,
            ) {
                DigitBoxes(code)
                Keypad(
                    onDigit = { if (code.length < JoinRules.CODE_LENGTH) changeCode(code + it) },
                    onErase = { if (code.isNotEmpty()) changeCode(code.dropLast(1)) },
                    listening = false,
                    onListen = null,
                    modifier = Modifier.weight(1f), // the keypad takes the rest of the card
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CrewFound(vm, join, onJoined)
            }
        }
    }
}

/**
 * The six boxes of the code (design line 183): 60 x 76, 8 apart, centred, digits 34/800. A box with a
 * digit is blue on light blue, as drawn; the next one to fill has the blue edge too, the rest a grey one.
 */
@Composable
private fun DigitBoxes(code: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(JoinRules.CODE_LENGTH) { i ->
            val digit = code.getOrNull(i)
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 76.dp)
                    .clip(shape)
                    .background(if (digit != null) CdColors.BlueTint else CdColors.White)
                    .border(2.dp, if (i <= code.length) CdColors.Blue else CdColors.Gray200, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit?.toString().orEmpty(), color = CdColors.Ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** The keys in their rows (design line 772). */
private val keypadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(VOICE_KEY, "0", ERASE_KEY),
)
private const val VOICE_KEY = "말로"
private const val ERASE_KEY = "지우기"

/**
 * The 3 x 4 keypad under the boxes (design line 184): 1-9, 말로, 0, 지우기; light grey keys 8 apart,
 * radius 16, 24/800. It fills the height it gets. 말로 is greyed out while listening, or when there is
 * no voice service ([onListen] = null).
 */
@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    listening: Boolean,
    onListen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keypadRows.forEach { keys ->
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    val keyModifier = Modifier.weight(1f).fillMaxHeight()
                    when (key) {
                        VOICE_KEY -> Key(
                            if (listening) "듣는 중…" else VOICE_KEY,
                            onClick = { onListen?.invoke() },
                            enabled = onListen != null && !listening,
                            modifier = keyModifier,
                        )
                        ERASE_KEY -> Key(ERASE_KEY, onClick = onErase, modifier = keyModifier)
                        else -> Key(key, onClick = { onDigit(key.single()) }, modifier = keyModifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(CdColors.Gray100)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = CdColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** The right half of board 03: the crew found with the code, or what is happening before that. */
@Composable
private fun CrewFound(vm: CrewViewModel, join: CrewViewModel.JoinState, onJoined: () -> Unit) {
    when (join) {
        CrewViewModel.JoinState.Idle -> Unit
        CrewViewModel.JoinState.Searching -> Text("크루를 찾는 중…", style = MaterialTheme.typography.bodyLarge)
        CrewViewModel.JoinState.NotFound -> ErrorText("그 코드의 크루가 없어요")
        is CrewViewModel.JoinState.Found -> CdCard(modifier = Modifier.fillMaxSize()) {
            Text("크루를 찾았어요", style = MaterialTheme.typography.labelMedium)
            Text(join.crew.name, style = MaterialTheme.typography.headlineLarge)
            Text(
                "${vm.carName(join.crew.hostCarId)} 크루 · ${join.crew.cars.size}대 참여 중 · ${join.crew.destination.name}",
                style = MaterialTheme.typography.bodyLarge,
            )
            // withRo() picks 로 or 으로 for the name: "강릉 경포해변으로", "속초 해수욕장으로", "서울로".
            val toDestination = join.crew.destination.name.withRo()
            Text("$toDestination 안내할까요?", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("신촌역 들렀다 가기", onClick = onJoined)
                PrimaryButton("바로 $toDestination", onClick = onJoined)
            }
            TodoNote("신촌역 경유 버튼은 지금 경로를 경유지로 넣어야 해요 (board 03)")
            Text(
                "참여하면 도착할 때까지 일행에게 내 위치가 보여요 · 방장이 수락하면 연결돼요",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
