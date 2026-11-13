package com.cardrop.core

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardrop.ai.AiRecommendScreen
import com.cardrop.ai.AiViewModel
import com.cardrop.ai.QuizScreen
import com.cardrop.ai.ShoutScreen
import com.cardrop.core.demo.DemoPanel
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.DropKind
import com.cardrop.core.navigation.DropRoutingEffect
import com.cardrop.core.navigation.Screen
import com.cardrop.core.ui.CarDropShell
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CreateCrewScreen
import com.cardrop.crew.CrewViewModel
import com.cardrop.crew.DestinationSearchScreen
import com.cardrop.crew.JoinCrewScreen
import com.cardrop.drive.DecidedScreen
import com.cardrop.drive.DriveMainScreen
import com.cardrop.drive.DriveViewModel
import com.cardrop.drive.FuelMeScreen
import com.cardrop.drive.FuelInvite
import com.cardrop.drive.StopNoticeCard
import com.cardrop.drop.DropSendScreen
import com.cardrop.drop.InboxScreen
import com.cardrop.drop.VoteScreen
import com.cardrop.navi.InAppNavigatorPanel
import kotlinx.coroutines.delay

/**
 * Owner: lead. Wires every screen to its ViewModel and to the navigator.
 * Members add their screen's callbacks here; keep the edits to your own `is Screen.X ->` branch.
 */
@Composable
fun CarDropApp(container: AppContainer) {
    // From AppContainer, not `remember`: a new activity (day/night theme, a second MainActivity) keeps the screen.
    val navigator = container.navigator
    val crewVm: CrewViewModel = viewModel { CrewViewModel(container.crew) }
    val driveVm: DriveViewModel = viewModel {
        DriveViewModel(container.vehicle, container.route, container.navigation, container.crew, container.ai)
    }
    val aiVm: AiViewModel = viewModel {
        AiViewModel(container.ai, container.crew, container.route, container.vehicle, container.voice, container.phrases)
    }

    var socialMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(socialMessage) {
        if (socialMessage != null) {
            delay(4_000)
            socialMessage = null
        }
    }

    DropRoutingEffect(
        crew = container.crew,
        voice = container.voice,
        navigator = navigator,
        memory = container.dropRouting,
    ) { socialMessage = it }
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }

    val crewNow by container.crew.crew.collectAsStateWithLifecycle()
    val myCar = crewNow?.cars?.firstOrNull { it.id == container.crew.myCarId }
        ?: Car(container.crew.myCarId, "내 차", CarColor.BLUE, isMe = true)

    CarDropShell(
        current = navigator.current,
        onTab = navigator::switchTab,
        navPanel = container.inAppNavigation?.let { nav -> { InAppNavigatorPanel(nav, driveVm) } },
        overlay = {
            Captions(container, socialMessage)
            DemoPanel(container)
        },
        myCar = myCar,
    ) {
        // Board 04; with an invite it is board 06 (another car's fuel invitation as a banner on it).
        val driveScreen: @Composable (FuelInvite?) -> Unit = { invite ->
            DriveMainScreen(
                vm = driveVm,
                onQuickDrop = { topic -> navigator.go(Screen.AiRecommend(topic)) },
                onOpenDropSend = { navigator.go(Screen.DropSend) },
                onOpenInbox = { navigator.go(Screen.Inbox) },
                onFuelWarning = { navigator.go(Screen.FuelMe) },
                onSpeak = {
                    aiVm.suggestByVoice(
                        onTopic = { topic -> navigator.go(Screen.AiRecommend(topic)) },
                        onMessage = { text -> navigator.go(Screen.Shout(text)) },
                    )
                },
                onShout = { navigator.go(Screen.Shout()) },
                invite = invite,
            )
        }
        when (val screen = navigator.current) {
            Screen.CreateCrew -> CreateCrewScreen(
                vm = crewVm,
                onSearchDestination = { navigator.go(Screen.DestinationSearch) },
                onJoinWithCode = { navigator.go(Screen.JoinCrew) },
                onDepart = {
                    driveVm.startGuidance()
                    navigator.switchTab(Screen.Drive)
                },
            )
            Screen.DestinationSearch -> DestinationSearchScreen(crewVm, onPicked = navigator::back)
            Screen.JoinCrew -> JoinCrewScreen(crewVm) {
                driveVm.startGuidance()
                navigator.switchTab(Screen.Drive)
            }
            Screen.Drive -> driveScreen(null)
            Screen.FuelMe -> FuelMeScreen(driveVm, onDone = { navigator.switchTab(Screen.Drive) }, voice = container.voice)
            is Screen.FuelThem -> driveScreen(
                FuelInvite(screen.dropId, crewVm::carName, container.voice) { navigator.switchTab(Screen.Drive) },
            )
            Screen.Inbox -> InboxScreen(crewVm) { drop ->
                navigator.go(
                    when {
                        drop.isDecided -> Screen.Decided(drop.id)
                        drop.kind == DropKind.DECISION -> Screen.Vote(drop.id)
                        else -> Screen.Inbox
                    },
                )
            }
            Screen.DropSend -> DropSendScreen(
                vm = crewVm,
                onPickTopic = { topic, toCarIds -> navigator.go(Screen.AiRecommend(topic, toCarIds)) },
                onShout = { toCarIds -> navigator.go(Screen.Shout(toCarIds = toCarIds)) },
            )
            is Screen.Shout -> ShoutScreen(
                vm = aiVm,
                initialText = screen.initialText,
                toCarIds = screen.toCarIds,
                onBack = navigator::back,
                onSent = {
                    // The design's feedback after sending, shown as a quiet caption for a few seconds.
                    socialMessage = "${aiVm.recipientLabel(screen.toCarIds)}에 한마디를 보냈어요"
                    navigator.switchTab(Screen.Drive)
                },
            )
            is Screen.AiRecommend -> AiRecommendScreen(aiVm, screen.topic, screen.toCarIds, onBack = navigator::back) { drop ->
                navigator.go(Screen.Vote(drop.id))
            }
            is Screen.Vote -> VoteScreen(crewVm, screen.dropId)
            is Screen.Decided -> DecidedScreen(driveVm, screen.dropId, onDone = { navigator.switchTab(Screen.Drive) }, voice = container.voice)
            Screen.Games -> QuizScreen(aiVm)
        }
    }
}

/**
 * Shows this car getting to the crew's stop (stopped / drove past), when the microphone is listening,
 * what the voice just said (the fake voice has no sound), and quiet social messages.
 */
@Composable
private fun Captions(container: AppContainer, socialMessage: String?) {
    val listening by container.voice.listening.collectAsStateWithLifecycle()
    val spoken by container.voice.lastSpoken.collectAsStateWithLifecycle()
    val stopNotice by container.stopNotice.collectAsStateWithLifecycle()
    val navRoute by container.navigation.route.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            StopNoticeCard(stopNotice, navRoute.waypoint)
            if (listening) Caption("마이크 · 듣고 있어요… 말씀하세요")
            spoken?.let { Caption("음성 · $it") }
            socialMessage?.let { Caption("소셜 · $it") }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = CdColors.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CdColors.Ink.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
