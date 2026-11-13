package com.cardrop.core.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardrop.core.ui.CdCard
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
 * [onPick]: the bot crew (car null) or the real crew as that car.
 */
@Composable
fun StartModeScreen(lastRealCrew: Boolean, lastCar: String, onPick: (realCrew: Boolean, car: String?) -> Unit) {
    // Sized like the CarDrop area (1067 x 900 design px), so it reads large on the car screen.
    DesignScale(designWidth = 1067f) {
        Box(modifier = Modifier.fillMaxSize().background(CdColors.Gray100), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.width(940.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ScreenHeader(eyebrow = "시연용 · 정식 앱에는 없어요", title = "이 기기는 어떤 크루로 시작할까요?")
                Row(modifier = Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val bots = { onPick(false, null) }
                    CdCard(modifier = Modifier.weight(1f).fillMaxHeight(), onClick = bots, selected = !lastRealCrew) {
                        Text("봇 크루", style = MaterialTheme.typography.headlineLarge)
                        Text("이 기기 혼자 연습해요. 내 차는 아빠 차, 다른 3대는 봇이에요.", style = MaterialTheme.typography.bodyLarge)
                        PrimaryButton("봇 크루로 시작", onClick = bots, modifier = Modifier.fillMaxWidth())
                        if (!lastRealCrew) Pill("지난번")
                    }
                    CdCard(modifier = Modifier.weight(1f).fillMaxHeight(), selected = lastRealCrew) {
                        Text("실제 크루", style = MaterialTheme.typography.headlineLarge)
                        Text("여러 기기가 한 크루로 달려요. 내 차를 누르면 시작해요.", style = MaterialTheme.typography.bodyLarge)
                        DemoCar.KEYS.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { car ->
                                    val name = FirebaseCrewRepository.carFor(car).name
                                    PrimaryButton(
                                        text = if (car == lastCar) "$name ✓" else name,
                                        onClick = { onPick(true, car) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        Text(
                            "아빠 차는 크루를 만들고, 다른 차는 그 코드로 참여해요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CdColors.Gray600,
                        )
                        if (lastRealCrew) Pill("지난번")
                    }
                }
            }
        }
    }
}
