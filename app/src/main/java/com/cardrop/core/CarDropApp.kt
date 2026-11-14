package com.cardrop.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardrop.ai.AiRecommendScreen
import com.cardrop.ai.AiViewModel
import com.cardrop.ai.QuickDropPicker
import com.cardrop.ai.game.GamesScreen
import com.cardrop.ai.game.OmokInviteNotice
import com.cardrop.ai.ShoutScreen
import com.cardrop.core.demo.DemoPanel
import com.cardrop.core.demo.DemoRailButton
import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.DropTopic
import com.cardrop.core.model.recipientLabel
import com.cardrop.core.navigation.DropRoutingEffect
import com.cardrop.core.navigation.Screen
import com.cardrop.core.navigation.inboxScreenFor
import com.cardrop.core.ui.CarDropShell
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.crew.CreateCrewScreen
import com.cardrop.crew.CrewViewModel
import com.cardrop.crew.DestinationSearchScreen
import com.cardrop.crew.JoinCrewScreen
import com.cardrop.crew.JoinRules
import com.cardrop.drive.DecidedScreen
import com.cardrop.drive.DriveMainScreen
import com.cardrop.drive.DriveViewModel
import com.cardrop.drive.FuelMeScreen
import com.cardrop.drive.FuelInvite
import com.cardrop.drive.QuickDropEditScreen
import com.cardrop.drive.QuickDrop
import com.cardrop.drive.StopNoticeCard
import com.cardrop.drop.DropSendScreen
import com.cardrop.drop.SongGetDialog
import com.cardrop.drop.SongSendScreen
import com.cardrop.drop.InboxScreen
import com.cardrop.drop.VoteRules
import com.cardrop.drop.VoteScreen
import com.cardrop.navi.InAppNavigatorPanel
import com.cardrop.safety.SafetyScoreScreen
import com.cardrop.safety.SafetyResultScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Owner: lead. Wires every screen to its ViewModel and to the navigator.
 * Members add their screen's callbacks here; keep the edits to your own `is Screen.X ->` branch.
 */
@Composable
fun CarDropApp(container: AppContainer) {
    // From AppContainer, not `remember`: a new activity (day/night theme, a second MainActivity) keeps the screen.
    val navigator = container.navigator
    // Each explicit record-tab or SCORE quick-slot tap gets a new greeting token.
    LaunchedEffect(navigator.current) {
        val screen = navigator.current
        if (screen is Screen.Safety) container.safety.showRecordEntry(screen.entryId)
        else container.safety.leaveRecordScreen()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, container) {
        container.omok.onVisible(lifecycle, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) container.omok.onVisible(lifecycle, true)
            if (event == Lifecycle.Event.ON_STOP) {
                container.omok.onVisible(lifecycle, false)
                container.safety.leaveRecordScreen()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            container.omok.onVisible(lifecycle, false)
            container.safety.leaveRecordScreen()
        }
    }
    val safetyCompletion by container.safety.completion.collectAsStateWithLifecycle()
    LaunchedEffect(safetyCompletion) {
        safetyCompletion?.let { id ->
            navigator.go(Screen.SafetyResult(id))
            container.safety.consumeCompletion(id)
        }
    }
    val crewVm: CrewViewModel = viewModel { CrewViewModel(
            container.crew, container.placeSearch, container::myPosition, container.voice, container.destinationTrip,
        ) }
    val driveVm: DriveViewModel = viewModel {
        DriveViewModel(
            container.vehicle, container.route, container.navigation, container.crew, container.ai,
            crewFuel = container.crewFuel,
            nearbyStations = container.nearbyStations,
        )
    }
    val aiVm: AiViewModel = viewModel {
        AiViewModel(
            container.ai, container.crew, container.route, container.vehicle, container.voice, container.phrases,
            container.placeSearch, container::myPosition,
        )
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

    // The trip just started (the host's 출발, every car joined, or the minute for joining is over): board 01 gives way to
    // board 04, on the host and on a car that looked at the crew. Only the change counts (drop(1)): a new activity
    // with the trip already started keeps the screen the driver chose.
    LaunchedEffect(container.crew) {
        container.crew.crew.map { it?.isStarted == true }.distinctUntilChanged().drop(1).collect { started ->
            if (started && (navigator.current == Screen.CreateCrew || navigator.current == Screen.DestinationSearch)) {
                navigator.switchTab(Screen.Drive)
            }
        }
    }

    val crewNow by container.crew.crew.collectAsStateWithLifecycle()
    val myCar = crewNow?.cars?.firstOrNull { it.id == container.crew.myCarId }
        ?: Car(container.crew.myCarId, "내 차", CarColor.BLUE, isMe = true)
    // The DEMO panel: its button sits in the rail (so it never covers a screen's bottom buttons), the panel over the screen.
    var demoOpen by remember { mutableStateOf(false) }
    // Board 04's 휴게소 / 식사 / 주유 tiles: board 10 as a pop-up over 04 (null = closed). Keyed on the screen, so any
    // other screen closes it, e.g. another car's vote that opens board 11.
    var quickDropTopic by remember(navigator.current) { mutableStateOf<DropTopic?>(null) }

    CarDropShell(
        current = navigator.current,
        onTab = { screen ->
            navigator.switchTab(if (screen is Screen.Safety) Screen.Safety(container.safety.newEntry()) else screen)
        },
        navPanel = { InAppNavigatorPanel(container.inAppNavigation, driveVm) },
        overlay = {
            Captions(container, socialMessage)
            if (navigator.current != Screen.Games) OmokInviteNotice(container.omok) { navigator.go(Screen.Games) }
            DemoPanel(container, open = demoOpen, onClose = { demoOpen = false })
        },
        myCar = myCar,
        tripStarted = crewNow?.isStarted == true,
        railExtra = { DemoRailButton(onClick = { demoOpen = !demoOpen }) },
    ) {
        // Board 04; with an invite it is board 06 (another car's fuel invitation as a banner on it).
        val driveScreen: @Composable (FuelInvite?) -> Unit = { invite -> Box(modifier = Modifier.fillMaxSize()) {
            val quickDrops by container.quickDrops.chosen.collectAsStateWithLifecycle()
            DriveMainScreen(
                vm = driveVm,
                quickDrops = quickDrops,
                onQuickDrop = { drop ->
                    val topic = drop.popupTopic()
                    if (drop == QuickDrop.SCORE) navigator.go(Screen.Safety(container.safety.newEntry()))
                    else if (topic != null) quickDropTopic = topic else drop.screen()?.let(navigator::go)
                },
                onEditQuickDrops = { navigator.go(Screen.QuickDropEdit) },
                onOpenDropSend = { navigator.go(Screen.DropSend) },
                onOpenInbox = { navigator.go(Screen.Inbox) },
                onFuelWarning = { navigator.go(Screen.FuelMe) },
                onSpeak = {
                    aiVm.suggestByVoice(
                        onTopic = { topic -> navigator.go(Screen.AiRecommend(topic)) },
                        onMessage = { text -> navigator.go(Screen.Shout(text)) },
                    )
                },
                invite = invite,
            )
            // After sending, the pop-up closes and the driver stays on 04; the voice says "휴게소 투표를 보냈어요".
            quickDropTopic?.let { topic ->
                QuickDropPicker(
                    vm = aiVm,
                    topic = topic,
                    placeTimes = container.placeTimes,
                    onClose = { quickDropTopic = null },
                    onSent = { quickDropTopic = null },
                )
            }
        } }
        when (val screen = navigator.current) {
            Screen.CreateCrew -> CreateCrewScreen(
                vm = crewVm,
                vehicle = driveVm.snapshot, // this car's fuel for the host's row
                onSearchDestination = { navigator.go(Screen.DestinationSearch) },
                onJoinWithCode = { navigator.go(Screen.JoinCrew) },
                onDepart = {
                    // The host's 출발: the cars that joined start with this one, the missing ones are left out.
                    // After the start the button only leads back to board 04 (both calls then do nothing).
                    driveVm.startGuidance()
                    crewVm.startTrip()
                    navigator.switchTab(Screen.Drive)
                },
            )
            Screen.DestinationSearch -> DestinationSearchScreen(crewVm, onBack = navigator::back, onPicked = navigator::back)
            Screen.JoinCrew -> JoinCrewScreen(
                vm = crewVm,
                routeDestination = { container.navigation.route.value.destination },
                onBack = navigator::back,
            ) { stop ->
                // Before the host's 출발 this car waits on board 04; TripCoordinator starts it with the host.
                if (container.crew.crew.value?.isStarted == true) driveVm.startGuidance()
                // "…들렀다 가기": where this car was heading becomes the waypoint on the way to the crew's
                // destination. After startGuidance, so the navigator's last message is about the stop.
                val crewDestination = container.crew.crew.value?.destination
                if (stop != null && crewDestination != null) {
                    container.navigation.setRoute(crewDestination, waypoint = stop, reason = JoinRules.stopAddedNote(stop))
                }
                navigator.switchTab(Screen.Drive)
            }
            Screen.Drive -> driveScreen(null)
            is Screen.Safety -> SafetyScoreScreen(container.safety, crewNow) { navigator.go(Screen.SafetyResult(it)) }
            is Screen.SafetyResult -> SafetyResultScreen(container.safety, screen.recordId, crewNow) { navigator.switchTab(Screen.Safety()) }
            // 04's 퀵 드랍 편집: 홈에 저장하기 keeps the new slots on this device, then back to 04.
            Screen.QuickDropEdit -> QuickDropEditScreen(
                saved = container.quickDrops.chosen.value,
                onCancel = navigator::back,
                onSave = { drops ->
                    container.quickDrops.save(drops)
                    navigator.back()
                },
            )
            Screen.FuelMe -> FuelMeScreen(driveVm, onDone = { navigator.switchTab(Screen.Drive) }, voice = container.voice)
            is Screen.FuelThem -> driveScreen(
                FuelInvite(screen.dropId, crewVm::carName, container.voice) { navigator.switchTab(Screen.Drive) },
            )
            // Board 07: a half sheet over board 04, so the drive screen stays behind it. A fuel invite this car
            // turned down (가던 길 갈래요) doesn't wait for an answer there, the same as on 홈's badge.
            Screen.Inbox -> Box(modifier = Modifier.fillMaxSize()) {
                driveScreen(null)
                InboxScreen(crewVm, onClose = navigator::back, isSkipped = driveVm::skipped) { drop ->
                    // A destination proposal that was turned down decided no place, so board 14 has nothing to
                    // show: it opens on board 11, which says who said 반대.
                    navigator.go(if (VoteRules.rejected(drop)) Screen.Vote(drop.id) else inboxScreenFor(drop))
                }
            }
            Screen.DropSend -> DropSendScreen(
                vm = crewVm,
                onPickTopic = { topic, toCarIds -> navigator.go(Screen.AiRecommend(topic, toCarIds)) },
                onShout = { toCarIds -> navigator.go(Screen.Shout(toCarIds = toCarIds)) },
                onGames = { navigator.go(Screen.Games) },
                onClose = { navigator.switchTab(Screen.Drive) },
                // 목적지 변경: once the proposal is out, back to 홈 (the voice says so). Board 14 opens by itself if it
                // passes; the answers are in the inbox (lead, 2026-11-15: it used to wait on board 11).
                onProposalSent = { navigator.switchTab(Screen.Drive) },
                onSong = { toCarIds -> navigator.go(Screen.SongSend(toCarIds)) },
                gamesOpen = driveVm.gamesOpen.collectAsStateWithLifecycle().value,
            )
            // Board 12: after 노래 드랍하기 the driver is back on 04, with a quiet caption.
            is Screen.SongSend -> SongSendScreen(
                vm = crewVm,
                player = container.songs,
                toCarIds = screen.toCarIds,
                onBack = navigator::back,
                onSent = {
                    socialMessage = "${crewNow?.recipientLabel(screen.toCarIds, crewVm.myCarId) ?: "크루 전체"}에 노래를 보냈어요"
                    navigator.switchTab(Screen.Drive)
                },
            )
            // Board 13: another car's song, a pop-up over board 04.
            is Screen.SongGet -> Box(modifier = Modifier.fillMaxSize()) {
                driveScreen(null)
                val drops by crewVm.drops.collectAsStateWithLifecycle()
                val drop = drops.firstOrNull { it.id == screen.dropId }
                if (drop == null) {
                    LaunchedEffect(screen.dropId) { navigator.back() }
                } else {
                    SongGetDialog(drop, crewNow?.cars?.firstOrNull { it.id == drop.fromCarId }, container.songs, onClose = navigator::back)
                }
            }
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
            is Screen.AiRecommend -> AiRecommendScreen(
                aiVm, screen.topic, screen.toCarIds, placeTimes = container.placeTimes, onBack = navigator::back,
            ) { drop ->
                // This car answers its own vote on board 11. One destination is a proposal: sending it said 찬성,
                // so back to 홈 as after 목적지 변경's proposal.
                if (VoteRules.isProposal(drop)) navigator.switchTab(Screen.Drive) else navigator.go(Screen.Vote(drop.id))
            }
            // A pass (난 패스) keeps this car's route: the navigator skips the stop, like 14's "우리 차는 안 들르기".
            is Screen.Vote -> VoteScreen(
                crewVm, screen.dropId, placeTimes = container.placeTimes, onPass = container.navigation.decisions::skip,
                onOpenResult = { navigator.go(Screen.Decided(it)) },
                // After this car's answer (or a counter-proposal, or 닫기 after a 반대): back to 홈 right away. Board 14
                // opens over it once the vote ends (DropRouting; lead, 2026-11-15).
                onClose = { navigator.switchTab(Screen.Drive) },
            )
            is Screen.Decided -> DecidedScreen(driveVm, screen.dropId, onDone = { navigator.switchTab(Screen.Drive) }, voice = container.voice)
            Screen.Games -> {
                // For the eyebrow "덕평자연휴게소 · 4대 모두 정차": the stop this car is parked at, and the crew's messages.
                val stop by container.inAppNavigation.parkedAt.collectAsStateWithLifecycle()
                val drops by driveVm.drops.collectAsStateWithLifecycle()
                // The games open once the car has stopped: P, and the GPS no longer says it moves (gamesOpen).
                val nav by container.inAppNavigation.state.collectAsStateWithLifecycle()
                GamesScreen(aiVm, container.omok, stop = stop, drops = drops, fix = nav.fix)
            }
        }
    }
}

/**
 * Top right, below the screen header: the card for this car getting to the crew's stop (stopped / drove past).
 * Bottom right, a little up from the corner (so they don't cover the screen header): the black captions
 * for the microphone listening, what the voice just said (the fake voice has no sound; fades out after
 * a few seconds), and quiet social messages.
 */
@Composable
private fun Captions(container: AppContainer, socialMessage: String?) {
    val listening by container.voice.listening.collectAsStateWithLifecycle()
    val spoken by container.voice.lastSpoken.collectAsStateWithLifecycle()
    val stopNotice by container.stopNotice.collectAsStateWithLifecycle()
    val navRoute by container.navigation.route.collectAsStateWithLifecycle()

    // The voice keeps its last sentence forever, so the caption hides itself: each new sentence shows
    // for SPOKEN_CAPTION_MILLIS, then fades out. The same words said again are a new line (SpokenLine),
    // so they show again too.
    var spokenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(spoken) {
        spokenVisible = spoken != null
        delay(SPOKEN_CAPTION_MILLIS)
        spokenVisible = false
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Below the screen header (not over board 04's 음성 모드 pill and inbox button), and in line with the
        // screen's right edge (16 + 12 = ScreenColumn's 28).
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = STOP_NOTICE_TOP, end = 12.dp)) {
            StopNoticeCard(stopNotice, navRoute.waypoint)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = CAPTIONS_FROM_BOTTOM),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (listening) Caption("마이크 · 듣고 있어요… 말씀하세요")
            AnimatedVisibility(visible = spokenVisible, enter = fadeIn(), exit = fadeOut()) {
                spoken?.let { Caption("음성 · ${it.text}") }
            }
            socialMessage?.let { Caption("소셜 · $it") }
        }
    }
}

/**
 * How far up from the corner the captions sit: where they were above the DEMO button, which is in the rail now.
 * While they show (a few seconds), they still cover the right end of a button pinned to the bottom.
 */
private val CAPTIONS_FROM_BOTTOM = 50.dp

/**
 * The stop card's distance from the top, inside the 16 dp around the captions: a screen header ends about
 * 88 dp down (24 dp screen padding + board 04's header, about 64 dp), so the card starts about 14 dp below it.
 */
private val STOP_NOTICE_TOP = 86.dp

/** How long the "음성 · …" caption stays before it fades out. */
private const val SPOKEN_CAPTION_MILLIS = 5_000L

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
