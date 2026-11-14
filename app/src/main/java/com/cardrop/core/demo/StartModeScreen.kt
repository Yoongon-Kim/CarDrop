package com.cardrop.core.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cardrop.core.text.particle
import com.cardrop.core.ui.CdCard
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.DesignScale
import com.cardrop.core.ui.Pill
import com.cardrop.core.ui.PrimaryButton
import com.cardrop.core.ui.ScreenHeader
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.FirebaseCrewRepository

/**
 * Owner: lead. For the demo version only, not the official app: when CarDrop starts, it first asks how
 * this device runs. 봇 크루 = this device alone with three bot cars; 실제 크루 = several devices in one
 * crew through Firebase, as the car picked here. The answer is saved like the DEMO panel's (DemoCrew,
 * DemoCar), and only then does MainActivity make the app's parts (AppContainer).
 */
object StartMode {
    /**
     * In the DEMO panel's restart (restartApp): the crew and car were just picked, so don't ask again.
     * Also for adb: `am start ... --ez cardrop.skipStartQuestion true` starts with the last pick.
     */
    const val EXTRA_SKIP_QUESTION = "cardrop.skipStartQuestion"

    /** Ask at start? Not when told to skip it, and not in a build without Firebase settings (bots only). */
    fun shouldAsk(hasFirebase: Boolean, skip: Boolean): Boolean = hasFirebase && !skip
}

/**
 * The start question. [lastRealCrew] / [lastCar]: what this device used last time (marked).
 * [onPick]: the bot crew (car null) or the real crew as that car. The pencil next to each car renames it on this
 * device ([CarNames]; user request, 2026-11-15), for the bot crew too.
 */
@Composable
fun StartModeScreen(lastRealCrew: Boolean, lastCar: String, onPick: (realCrew: Boolean, car: String?) -> Unit) {
    // The car whose name is being typed (a car id), and the names shown, read again after each rename.
    var editing by remember { mutableStateOf<String?>(null) }
    var names by remember { mutableStateOf(CarNames.DEFAULTS.keys.associateWith(CarNames::of)) }
    val hostName = names.getValue(FirebaseCrewRepository.carFor(DemoCar.HOST).id)
    // Sized like the CarDrop area (1067 x 900 design px), so it reads large on the car screen.
    DesignScale(designWidth = 1067f) {
        Box(modifier = Modifier.fillMaxSize().background(CdColors.Gray100), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.width(940.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ScreenHeader(eyebrow = "시연용 · 정식 앱에는 없어요", title = "이 기기는 어떤 크루로 시작할까요?")
                Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val bots = { onPick(false, null) }
                    CdCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = bots, selected = !lastRealCrew) {
                        Text("봇 크루", style = MaterialTheme.typography.headlineLarge)
                        Text("이 기기 혼자 연습해요. 내 차는 $hostName, 다른 3대는 봇이에요.", style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton("봇 크루로 시작", onClick = bots, modifier = Modifier.fillMaxWidth())
                        if (!lastRealCrew) Pill("지난번")
                    }
                    CdCard(modifier = Modifier.weight(1f).fillMaxHeight(), selected = lastRealCrew) {
                        Text("실제 크루", style = MaterialTheme.typography.headlineLarge)
                        Text("여러 기기가 한 크루로 달려요. 내 차를 누르면 시작해요.", style = MaterialTheme.typography.bodyLarge)
                        // One car a row, so a longer name fits next to its pencil.
                        DemoCar.KEYS.forEach { car ->
                            val id = FirebaseCrewRepository.carFor(car).id
                            val name = names.getValue(id)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(
                                    text = if (car == lastCar) "$name ✓" else name,
                                    onClick = { onPick(true, car) },
                                    modifier = Modifier.weight(1f),
                                )
                                PencilButton(label = "$name 이름 바꾸기", onClick = { editing = id })
                            }
                        }
                        Text(
                            "${hostName.particle("은", "는")} 크루를 만들고, 다른 차는 그 코드로 참여해요. 연필로 차 이름을 바꿔요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CdColors.Gray600,
                        )
                        if (lastRealCrew) Pill("지난번")
                    }
                }
            }
            editing?.let { id ->
                CarNameEditor(
                    carId = id,
                    onClose = {
                        names = CarNames.DEFAULTS.keys.associateWith(CarNames::of)
                        editing = null
                    },
                )
            }
        }
    }
}

/** The small round pencil next to a car's button: opens [CarNameEditor] for that car. */
@Composable
private fun PencilButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CdColors.BlueLight)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CdIcons.Pencil, contentDescription = label, tint = CdColors.Blue, modifier = Modifier.size(24.dp))
    }
}

/**
 * Renames [carId] ([CarNames.rename]), like board 01's crew name: an empty box with the current name in grey, 저장
 * (or the keyboard's enter) and 취소, plus 원래 이름으로 for a renamed car. A card at the top of a grey veil, so Pleos's
 * keyboard (the lower third of the screen) never covers the box; a tap on the veil is 취소.
 */
@Composable
private fun CarNameEditor(carId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val current = CarNames.of(carId)
    val original = CarNames.DEFAULTS.getValue(carId)
    var draft by remember { mutableStateOf("") }
    val save = {
        if (CarNames.clean(draft) != null) {
            CarNames.rename(context, carId, draft)
            onClose()
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // the keyboard comes up for the new name
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        CdCard(
            modifier = Modifier
                .padding(top = 56.dp)
                .width(720.dp)
                // Taps on the card itself don't reach the veil (which would close it).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            gap = 14.dp,
        ) {
            Text("$current 이름 바꾸기", style = MaterialTheme.typography.titleLarge)
            Text(
                "이 기기에만 저장되고, 앱을 다시 켜도 그대로예요. 최대 ${CarNames.MAX_LENGTH}자.",
                style = MaterialTheme.typography.bodyMedium,
                color = CdColors.Gray600,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CdTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = current,
                    maxLength = CarNames.MAX_LENGTH,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    onDone = save,
                    onClear = { draft = "" },
                )
                CdChip("저장", onClick = save, style = ChipStyle.Primary, enabled = CarNames.clean(draft) != null, height = 54.dp)
                CdChip("취소", onClick = onClose, height = 54.dp)
            }
            if (CarNames.isRenamed(carId)) {
                CdChip(
                    "원래 이름으로 · $original",
                    onClick = {
                        CarNames.rename(context, carId, "")
                        onClose()
                    },
                    height = 46.dp,
                )
            }
        }
    }
}
