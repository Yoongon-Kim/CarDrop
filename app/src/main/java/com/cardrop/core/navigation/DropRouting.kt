package com.cardrop.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.crew.CrewRepository
import com.cardrop.voice.VoiceService

/**
 * Owner: lead.
 *
 * Opens the right screen when something arrives from another car:
 * - a SOCIAL drop -> a quiet on-screen message, never spoken
 * - a FUEL drop -> board 06 (같이 갈래요?)
 * - any other DECISION drop -> board 11 (vote), announced by voice
 * - a vote that just ended -> board 14 (decision)
 */
@Composable
fun DropRoutingEffect(
    crew: CrewRepository,
    voice: VoiceService,
    navigator: AppNavigator,
    onSocialMessage: (String) -> Unit,
) {
    LaunchedEffect(crew) {
        val seen = mutableSetOf<String>()
        val seenDecided = mutableSetOf<String>()
        crew.drops.collect { drops ->
            for (drop in drops) {
                val fromOther = drop.fromCarId != crew.myCarId
                val sender = crew.crew.value?.cars?.firstOrNull { it.id == drop.fromCarId }?.name ?: "일행"

                if (seen.add(drop.id) && fromOther) {
                    when {
                        drop.kind == DropKind.SOCIAL -> onSocialMessage("$sender · ${drop.title}")
                        drop.topic == DropTopic.FUEL -> {
                            voice.speak(drop.title)
                            navigator.go(Screen.FuelThem(drop.id))
                        }
                        else -> {
                            voice.speak("${sender}가 투표를 보냈어요. ${drop.title}")
                            navigator.go(Screen.Vote(drop.id))
                        }
                    }
                }

                val involved = !fromOther || crew.myCarId in drop.votes || drop.topic != DropTopic.FUEL
                if (drop.kind == DropKind.DECISION && drop.isDecided && involved && seenDecided.add(drop.id)) {
                    navigator.go(Screen.Decided(drop.id))
                }
            }
        }
    }
}
