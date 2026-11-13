package com.cardrop.crew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardrop.core.model.Place
import com.cardrop.core.ui.BackButton
import com.cardrop.core.ui.CdChip
import com.cardrop.core.ui.CdIcons
import com.cardrop.core.ui.CdTextField
import com.cardrop.core.ui.ChipStyle
import com.cardrop.core.ui.StatusText
import com.cardrop.core.ui.theme.CdColors
import com.cardrop.navi.KeyboardRoom
import com.cardrop.navi.PlaceCard
import com.cardrop.navi.PlaceSuggestionList

/**
 * Board 02 목적지 검색 · Owner: Member A (place search: lead).
 *
 * Like Kakao Map's search: typing shows the places that fit (Kakao's place search, nearby first);
 * tapping one, or enter for the top one, makes it the crew's destination. "말로 검색" puts what the
 * driver says into the box and searches it. Before typing, the app's destinations. Without a Kakao
 * key (or offline) only those can be found.
 */
@Composable
fun DestinationSearchScreen(vm: CrewViewModel, onBack: () -> Unit, onPicked: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val suggestions by vm.destinationSuggestions.collectAsStateWithLifecycle()
    val voice by vm.destinationVoice.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        vm.openDestinationSearch()
        if (query.isNotEmpty()) vm.onDestinationTyped(query) // back after a restart: search again
        focus.requestFocus() // this screen is for typing: the box starts focused, the keyboard comes up
    }
    DisposableEffect(Unit) { onDispose { vm.stopListening() } } // leaving the screen stops the mic
    val pick: (Place) -> Unit = {
        vm.pickDestination(it)
        onPicked()
    }

    // The title and box stay put; only the places scroll. Pleos's keyboard covers the lower third.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = onBack)
            Text(
                "목적지를 검색해 주세요",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).em,
                color = CdColors.Ink,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CdTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.onDestinationTyped(it)
                },
                placeholder = "장소 · 주소 검색 (예: 강릉 경포해변)",
                maxLength = 48,
                modifier = Modifier.weight(1f).focusRequester(focus),
                onDone = { vm.pickTypedDestination(query, onPicked) },
                onClear = {
                    query = ""
                    vm.onDestinationTyped("")
                },
            )
            if (vm.canListen) {
                CdChip(
                    if (voice.listening) "듣는 중…" else "말로 검색",
                    onClick = {
                        focusManager.clearFocus() // the keyboard goes away, so the results show
                        vm.listenForDestination { heard -> query = heard }
                    },
                    style = ChipStyle.Blue,
                    icon = CdIcons.Mic,
                    enabled = !voice.listening,
                    modifier = Modifier.height(54.dp),
                )
            }
        }
        voice.status?.let { StatusText(it) }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (suggestions.shown) {
                PlaceSuggestionList(suggestions, onPick = { pick(it.place) })
            } else {
                Text("추천 목적지", color = CdColors.Gray600, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                PlaceCard(vm.knownDestinations(), highlight = "", onPick = { pick(it.place) })
            }
            // Room to scroll the last places up above the keyboard.
            Spacer(Modifier.height(KeyboardRoom))
        }
    }
}
